package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.CreateTransactionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.TransactionMapper;
import com.mssus.app.repository.*;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.EmailService;
import org.springframework.security.core.Authentication;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.service.TransactionService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionMapper transactionMapper;
    private final SharedRideRepository sharedRideRepository;
    private final SharedRideRequestRepository sharedRideRequestRepository;
    private final BalanceCalculationService balanceCalculationService;  // ✅ SSOT: Calculate balance from ledger

    public UUID generateGroupId() {
        return UUID.randomUUID();
    }

    @Override
    @Transactional
    public List<Transaction> initTopup(Integer userId, BigDecimal amount, String pspRef, String description) {
        validateTopupInput(userId, amount, pspRef);

        // Check idempotency
        String idempotencyKey = "TOPUP_" + pspRef + "_" + amount;
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate topup request with idempotency_key: {}", idempotencyKey);
            // Find system transaction too
            List<Transaction> existingTransactions = transactionRepository.findByGroupId(existing.get().getGroupId());
            return existingTransactions;
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for userId: " + userId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found for userId: " + userId));

        UUID groupId = generateGroupId();

        // ✅ SSOT: Create system transaction (no wallet, system wallet only)
        // ✅ FIX P0-DOUBLE_ENTRY: System.MASTER OUT (Debit) để balance với User IN (Credit)
        // Logic: External source (PSP) → System.MASTER OUT (system gives to user) → User IN
        // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint violation)
        // pspRef chỉ cần ở User transaction (vì đó là reference từ PSP cho user payment)
        Transaction systemOutflow = Transaction.builder()
                .type(TransactionType.TOPUP)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)  // ✅ FIX: OUT thay vì IN để balance
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(null)  // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint)
                .note("PSP Inflow - System transfers to user - " + description)
                .build();

        // ✅ SSOT: Create user transaction với wallet relationship
        Transaction userCredit = Transaction.builder()
                .groupId(groupId)
                .wallet(wallet)  // ✅ Set wallet relationship
                .type(TransactionType.TOPUP)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .idempotencyKey(idempotencyKey)  // ✅ Idempotency protection
                .pspRef(pspRef)
                .note(description)
                .build();

        List<Transaction> transactions = Arrays.asList(
                transactionRepository.save(systemOutflow),
                transactionRepository.save(userCredit)
        );

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo transaction
        if (userCredit.getWallet() != null) {
            balanceCalculationService.invalidateBalanceCache(userCredit.getWallet().getWalletId());
        }

        log.info("Initiated top-up for user {} with amount {} and pspRef {}", userId, amount, pspRef);
        return transactions;
    }

    @Override
    @Transactional
    public void handleTopupSuccess(String pspRef) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        // ✅ FIX P1-6: Check if already processed (idempotency)
        List<Transaction> existingSuccess = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.SUCCESS);
        if (!existingSuccess.isEmpty()) {
            log.warn("Top-up already processed for pspRef: {}", pspRef);
            return; // Idempotent - already processed
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending transactions found for pspRef: " + pspRef);
        }

        Integer userId = null;
        BigDecimal amount = null;
        Integer userTxnId = null;

        // ✅ SSOT: Update all transactions to SUCCESS
        Transaction userTransaction = null;
        Integer walletId = null;
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);

            if (txn.getActorKind() == ActorKind.USER && txn.getActorUser() != null) {
                userId = txn.getActorUser().getUserId();
                amount = txn.getAmount();
                userTxnId = txn.getTxnId();
                userTransaction = txn;
            }
            
            // Get walletId for cache invalidation
            if (txn.getWallet() != null && walletId == null) {
                walletId = txn.getWallet().getWalletId();
            }

            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table (status = SUCCESS)
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi update transaction status
        if (walletId != null) {
            balanceCalculationService.invalidateBalanceCache(walletId);
        }
        
        if (userId != null && amount != null) {
            sendTopupSuccessEmail(userId, amount, userTxnId);
        }

        log.info("Top-up success for pspRef: {}", pspRef);
    }

    @Override
    @Transactional
    public void handleTopupFailed(String pspRef, String reason) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
        }

        // ✅ FIX P1-6: Check if already processed (idempotency)
        List<Transaction> existingFailed = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.FAILED);
        if (!existingFailed.isEmpty()) {
            log.warn("Top-up already failed for pspRef: {}", pspRef);
            return; // Idempotent - already processed
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending transactions found for pspRef: " + pspRef);
        }

        Integer userId = null;
        BigDecimal amount = null;
        Integer userTxnId = null;

        // ✅ SSOT: Update all transactions to FAILED
        Integer walletId = null;
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);

            if (txn.getActorKind() == ActorKind.USER && txn.getActorUser() != null) {
                userId = txn.getActorUser().getUserId();
                amount = txn.getAmount();
                userTxnId = txn.getTxnId();
            }
            
            // Get walletId for cache invalidation
            if (txn.getWallet() != null && walletId == null) {
                walletId = txn.getWallet().getWalletId();
            }

            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Transaction status = FAILED nên sẽ không được tính vào balance
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi update transaction status
        if (walletId != null) {
            balanceCalculationService.invalidateBalanceCache(walletId);
        }
        
        if (userId != null && amount != null) {
            sendTopupFailedEmail(userId, amount, userTxnId, reason);
        }

        log.info("Top-up failed for pspRef: {} with reason: {}", pspRef, reason);
    }

    @Override
    @Transactional
    public Transaction createTransaction(CreateTransactionRequest request) {
        TransactionType type = request.type();
        TransactionDirection direction = request.direction();
        ActorKind actorKind = request.actorKind();
        Integer actorUserId = request.actorUserId();
        SystemWallet systemWallet = request.systemWallet();
        BigDecimal amount = request.amount();
        String currency = request.currency();
        UUID groupId = request.groupId();
        String pspRef = request.pspRef();
        Integer sharedRideId = request.sharedRideId();
        Integer sharedRideRequestId = request.sharedRideRequestId();
        TransactionStatus status = request.status();
        String note = request.note();
        BigDecimal beforeAvail = request.beforeAvail();
        BigDecimal afterAvail = request.afterAvail();
        BigDecimal beforePending = request.beforePending();
        BigDecimal afterPending = request.afterPending();

        validateCreateTransactionRequest(request);
        User actorUser = null;

        if (actorKind == ActorKind.USER) {
            actorUser = userRepository.findById(actorUserId)
                .orElseThrow(() -> new NotFoundException("Actor user not found: " + actorUserId));
            if (request.beforeAvail() == null || request.afterAvail() == null ||
                request.beforePending() == null || request.afterPending() == null) {
                throw new ValidationException("Balance snapshots (before/after) are required for USER transactions.");
            }
        } else {
            beforeAvail = null;
            afterAvail = null;
            beforePending = null;
            afterPending = null;
        }

        SharedRide sharedRide = null;
        SharedRideRequest sharedRideRequest = null;

        if (sharedRideId != null) {
            sharedRide = sharedRideRepository.findById(sharedRideId)
                .orElseThrow(() -> new NotFoundException("Shared ride not found: " + sharedRideId));
        }

        if (sharedRideRequestId != null) {
            sharedRideRequest = sharedRideRequestRepository.findById(sharedRideRequestId)
                .orElseThrow(() -> new NotFoundException("Shared ride request not found: " + sharedRideRequestId));
        }

        Transaction transaction = Transaction.builder()
            .groupId(groupId)
            .type(type)
            .direction(direction)
            .actorKind(actorKind)
            .actorUser(actorUser)
            .systemWallet(systemWallet)
            .amount(amount)
            .currency(currency)
            .sharedRide(sharedRide)
            .sharedRideRequest(sharedRideRequest)
            .pspRef(pspRef)
            .status(status)
            .beforeAvail(beforeAvail)
            .afterAvail(afterAvail)
            .beforePending(beforePending)
            .afterPending(afterPending)
            .note(note)
            .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Created transaction - ID: {}, type: {}, actor: {}, amount: {}",
            savedTransaction.getTxnId(), type, actorKind, amount);

        return savedTransaction;
    }


//    @Override
//    @Transactional
//    public List<Transaction> createHold(Integer riderId, BigDecimal amount, Integer bookingId, String description) {
//        validateHoldInput(riderId, amount, bookingId);
//
//        Wallet wallet = walletRepository.findByUser_UserId(riderId)
//                .orElseThrow(() -> new NotFoundException("Wallet not found for riderId: " + riderId));
//        User rider = userRepository.findById(riderId)
//                .orElseThrow(() -> new NotFoundException("Rider not found for riderId: " + riderId));
//
//        // Check sufficient balance
//        if (wallet.getShadowBalance().compareTo(amount) < 0) {
//            throw new ValidationException("Insufficient balance. Available: " + wallet.getShadowBalance() + ", Required: " + amount);
//        }
//
//        UUID groupId = generateGroupId();
//
//        // Create hold transaction (moves from available to pending)
//        Transaction holdTransaction = Transaction.builder()
//                .type(TransactionType.HOLD_CREATE)
//                .groupId(groupId)
//                .direction(TransactionDirection.INTERNAL)
//                .actorKind(ActorKind.USER)
//                .actorUser(rider.getRiderProfile().getUser())
//                .riderUser(rider)
//                .amount(amount)
//                .currency("VND")
//                .bookingId(bookingId)
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(wallet.getShadowBalance())
//                .afterAvail(wallet.getShadowBalance().subtract(amount))
//                .beforePending(wallet.getPendingBalance())
//                .afterPending(wallet.getPendingBalance().add(amount))
//                .note(description)
//                .build();
//
//        Transaction savedTransaction = transactionRepository.save(holdTransaction);
//
//        // Update wallet balances
//        wallet.setShadowBalance(wallet.getShadowBalance().subtract(amount));
//        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
//        walletRepository.save(wallet);
//
//        log.info("Created hold for rider {} with amount {} for booking {}", riderId, amount, bookingId);
//        return Arrays.asList(savedTransaction);
//    }
//
//    @Override
//    @Transactional
//    public List<Transaction> captureFare(SettlementResult settlementResult, Integer riderId, Integer driverId, String description) {
//        validateCaptureInput(riderId, driverId, settlementResult.driverPayout());
//
//        // Find the original hold transaction
//        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
//        Transaction holdTransaction = holdTransactions.stream()
//                .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
//                .findFirst()
//                .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));
//
//        BigDecimal heldAmount = holdTransaction.getAmount();
//        if (totalFare.compareTo(heldAmount) > 0) {
//            throw new ValidationException("Capture amount exceeds held amount. Held: " + heldAmount + ", Capture: " + totalFare);
//        }
//
//        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
//                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
//        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
//                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
//
//        User rider = userRepository.findById(riderId).orElseThrow(() -> new NotFoundException("Rider not found"));
//        User driver = userRepository.findById(driverId).orElseThrow(() -> new NotFoundException("Driver not found"));
//
//        BigDecimal commission = calculateCommission(totalFare, commissionRate);
//        BigDecimal driverAmount = totalFare.subtract(commission);
//        BigDecimal releaseAmount = heldAmount.subtract(totalFare);
//
//        List<Transaction> transactions = new ArrayList<>();
//
//        // 1. Capture fare from rider's pending balance
//        Transaction riderCharge = Transaction.builder()
//                .type(TransactionType.CAPTURE_FARE)
//                .groupId(groupId)
//                .direction(TransactionDirection.OUT)
//                .actorKind(ActorKind.USER)
//                .actorUser(rider.getRiderProfile().getUser())
//                .riderUser(rider)
//                .amount(totalFare)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(riderWallet.getShadowBalance())
//                .afterAvail(riderWallet.getShadowBalance())
//                .beforePending(riderWallet.getPendingBalance())
//                .afterPending(riderWallet.getPendingBalance().subtract(totalFare))
//                .note("Fare capture - " + description)
//                .build();
//
//        // 2. Pay driver
//        Transaction driverCredit = Transaction.builder()
//                .type(TransactionType.CAPTURE_FARE)
//                .groupId(groupId)
//                .direction(TransactionDirection.IN)
//                .actorUser(driver.getDriverProfile().getUser())
//                .actorKind(ActorKind.USER)
//                .driverUser(driver)
//                .amount(driverAmount)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(driverWallet.getShadowBalance())
//                .afterAvail(driverWallet.getShadowBalance().add(driverAmount))
//                .beforePending(driverWallet.getPendingBalance())
//                .afterPending(driverWallet.getPendingBalance())
//                .note("Driver payment - " + description)
//                .build();
//
//        // 3. Commission to system
//        Transaction commissionTransaction = Transaction.builder()
//                .type(TransactionType.CAPTURE_FARE)
//                .groupId(groupId)
//                .direction(TransactionDirection.IN)
//                .actorKind(ActorKind.SYSTEM)
//                .systemWallet(SystemWallet.COMMISSION)
//                .amount(commission)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .note("Commission - " + description)
//                .build();
//
//        transactions.add(transactionRepository.save(riderCharge));
//        transactions.add(transactionRepository.save(driverCredit));
//        transactions.add(transactionRepository.save(commissionTransaction));
//
//        // 4. Release remaining hold if any
//        if (releaseAmount.compareTo(BigDecimal.ZERO) > 0) {
//            Transaction releaseTransaction = Transaction.builder()
//                    .type(TransactionType.HOLD_RELEASE)
//                    .groupId(groupId)
//                    .direction(TransactionDirection.INTERNAL)
//                    .actorKind(ActorKind.SYSTEM)
//                    .riderUser(rider)
//                    .amount(releaseAmount)
//                    .currency("VND")
//                    .bookingId(holdTransaction.getBookingId())
//                    .status(TransactionStatus.SUCCESS)
//                    .beforeAvail(riderWallet.getShadowBalance())
//                    .afterAvail(riderWallet.getShadowBalance().add(releaseAmount))
//                    .beforePending(riderWallet.getPendingBalance().subtract(totalFare))
//                    .afterPending(riderWallet.getPendingBalance().subtract(heldAmount))
//                    .note("Hold release - " + description)
//                    .build();
//
//            transactions.add(transactionRepository.save(releaseTransaction));
//
//            // Update rider wallet - release remaining amount
//            riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(releaseAmount));
//        }
//
//        // Update wallet balances
//        riderWallet.setPendingBalance(riderWallet.getPendingBalance().subtract(heldAmount));
//        riderWallet.setTotalSpent(riderWallet.getTotalSpent().add(totalFare));
//
//        driverWallet.setShadowBalance(driverWallet.getShadowBalance().add(driverAmount));
//
//        walletRepository.save(riderWallet);
//        walletRepository.save(driverWallet);
//
//        log.info("Captured fare for booking {} - Total: {}, Driver: {}, Commission: {}",
//                holdTransaction.getBookingId(), totalFare, driverAmount, commission);
//
//        return transactions;
//    }
//
//    @Override
//    @Transactional
//    public List<Transaction> releaseHold(UUID groupId, String description) {
//        if (groupId == null) {
//            throw new ValidationException("Group ID cannot be null");
//        }
//
//        // Find the original hold transaction
//        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
//        Transaction holdTransaction = holdTransactions.stream()
//                .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
//                .findFirst()
//                .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));
//
//        // Check if already released
//        boolean alreadyReleased = holdTransactions.stream()
//                .anyMatch(t -> t.getType() == TransactionType.HOLD_RELEASE);
//        if (alreadyReleased) {
//            throw new ValidationException("Hold has already been released for groupId: " + groupId);
//        }
//
//        BigDecimal heldAmount = holdTransaction.getAmount();
//        Integer riderId = holdTransaction.getRiderUser().getUserId();
//
//        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
//                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
//
//        // Create release transaction
//        Transaction releaseTransaction = Transaction.builder()
//                .type(TransactionType.HOLD_RELEASE)
//                .groupId(groupId)
//                .direction(TransactionDirection.INTERNAL)
//                .actorKind(ActorKind.USER)
//                .actorUser(holdTransaction.getActorUser())
//                .riderUser(holdTransaction.getRiderUser())
//                .amount(heldAmount)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(riderWallet.getShadowBalance())
//                .afterAvail(riderWallet.getShadowBalance().add(heldAmount))
//                .beforePending(riderWallet.getPendingBalance())
//                .afterPending(riderWallet.getPendingBalance().subtract(heldAmount))
//                .note(description)
//                .build();
//
//        Transaction savedTransaction = transactionRepository.save(releaseTransaction);
//
//        // Update rider wallet balances
//        riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(heldAmount));
//        riderWallet.setPendingBalance(riderWallet.getPendingBalance().subtract(heldAmount));
//        walletRepository.save(riderWallet);
//
//        log.info("Released hold for groupId {} with amount {}", groupId, heldAmount);
//        return Arrays.asList(savedTransaction);
//    }

    @Override
    @Transactional
    public List<Transaction> initPayout(Integer driverId, BigDecimal amount, String pspRef, String description) {
        validatePayoutInput(driverId, amount, pspRef);

        // ✅ FIX P0-CONCURRENCY: Get wallet với pessimistic lock để tránh race condition
        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
        
        // ✅ FIX P0-CONCURRENCY: Lock wallet row trước khi check balance
        Wallet lockedWallet = walletRepository.findByIdWithLock(driverWallet.getWalletId())
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
        
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found"));

        // ✅ SSOT: Check balance từ ledger (sau khi lock)
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(lockedWallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(lockedWallet.getWalletId());
        
        if (availableBalance.compareTo(amount) < 0) {
            throw new ValidationException("Insufficient balance for payout. Available: " + availableBalance);
        }

        UUID groupId = generateGroupId();

        // ✅ SSOT: Create user payout transaction (pending until PSP confirms)
        // Balance snapshots are for audit trail only, not used for calculation
        Transaction userPayoutTransaction = Transaction.builder()
                .groupId(groupId)
                .wallet(lockedWallet)  // ✅ Set wallet relationship (use locked wallet)
                .type(TransactionType.PAYOUT)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(driver)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .beforeAvail(availableBalance)  // Snapshot for audit
                .afterAvail(availableBalance.subtract(amount))  // Snapshot for audit
                .beforePending(pendingBalance)  // Snapshot for audit
                .afterPending(pendingBalance.add(amount))  // Snapshot for audit
                .note(description)
                .build();

        // ✅ FIX P0-DOUBLE_ENTRY: Create system payout transaction để balance
        // User OUT + System.MASTER OUT = Balanced (cả 2 đều OUT, money leaves system)
        // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint violation)
        Transaction systemPayoutTransaction = Transaction.builder()
                .groupId(groupId)
                .type(TransactionType.PAYOUT)
                .direction(TransactionDirection.OUT)  // ✅ System also OUT (money leaves system)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(null)  // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint)
                .note("System payout to external - " + description)
                .build();

        List<Transaction> transactions = Arrays.asList(
                transactionRepository.save(userPayoutTransaction),
                transactionRepository.save(systemPayoutTransaction)
        );
        
        Transaction savedTransaction = userPayoutTransaction;

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo transaction
        if (savedTransaction.getWallet() != null) {
            balanceCalculationService.invalidateBalanceCache(savedTransaction.getWallet().getWalletId());
        }

        log.info("Initiated payout for driver {} with amount {} and pspRef {}", driverId, amount, pspRef);
        return transactions;
    }

    @Override
    @Transactional
    public void handlePayoutSuccess(String pspRef) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        // ✅ FIX P1-6: Check if already processed (idempotency)
        List<Transaction> existingSuccess = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.SUCCESS);
        if (!existingSuccess.isEmpty()) {
            log.warn("Payout already processed for pspRef: {}", pspRef);
            return; // Idempotent - already processed
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending payout transactions found for pspRef: " + pspRef);
        }

        Integer walletId = null;
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(txn);
            
            // Get walletId for cache invalidation
            if (txn.getWallet() != null && walletId == null) {
                walletId = txn.getWallet().getWalletId();
            }
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Transaction status = SUCCESS nên sẽ được tính vào balance
        // Pending balance sẽ tự động giảm khi tính từ ledger
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi update transaction status
        if (walletId != null) {
            balanceCalculationService.invalidateBalanceCache(walletId);
        }

        log.info("Payout success for pspRef: {}", pspRef);
    }

    @Override
    @Transactional
    public void handlePayoutFailed(String pspRef, String reason) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
        }

        // ✅ FIX P1-6: Check if already processed (idempotency)
        // Check both FAILED and REVERSED (vì bây giờ mark là REVERSED)
        List<Transaction> existingFailed = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.FAILED);
        List<Transaction> existingReversed = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.REVERSED);
        if (!existingFailed.isEmpty() || !existingReversed.isEmpty()) {
            log.warn("Payout already failed/reversed for pspRef: {}", pspRef);
            return; // Idempotent - already processed
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending payout transactions found for pspRef: " + pspRef);
        }

        // Find driver transaction và original groupId
        Transaction driverTxn = transactions.stream()
            .filter(t -> t.getActorKind() == ActorKind.USER && t.getActorUser() != null)
            .findFirst()
            .orElse(null);
        
        UUID originalGroupId = driverTxn != null ? driverTxn.getGroupId() : null;
        Integer driverId = driverTxn != null && driverTxn.getActorUser() != null ? driverTxn.getActorUser().getUserId() : null;
        BigDecimal amount = driverTxn != null ? driverTxn.getAmount() : null;

        // ✅ FIX P0-REFUND: Mark original transactions as REVERSED (reversal entries)
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.REVERSED);  // ✅ Mark as REVERSED instead of FAILED
            txn.setNote(txn.getNote() + " - Failed: " + reason + " | Reversed");
            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Transaction status = REVERSED nên sẽ không được tính vào balance
        // Tạo REFUND transaction để return money (reversal of original payout)
        if (driverTxn != null && driverTxn.getWallet() != null && amount != null && originalGroupId != null) {
            // ✅ FIX P0-REFUND: Generate idempotency key
            String idempotencyKey = "REFUND_PAYOUT_" + pspRef + "_" + amount;
            
            // ✅ FIX P0-REFUND: Check idempotency (prevent duplicate refund)
            Optional<Transaction> existingRefund = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existingRefund.isPresent()) {
                log.warn("Refund already processed for failed payout pspRef: {}, idempotencyKey: {}", pspRef, idempotencyKey);
                return; // Idempotent - already refunded
            }
            
            // ✅ FIX P0-REFUND: Get balances for snapshots
            BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(driverTxn.getWallet().getWalletId());
            BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(driverTxn.getWallet().getWalletId());
            
            UUID refundGroupId = generateGroupId();
            
            // ✅ FIX P0-REFUND: Create user refund transaction (reversal of original OUT)
            // Original: User OUT → Refund: User IN (reversal)
            Transaction userRefundTxn = Transaction.builder()
                .groupId(refundGroupId)
                .wallet(driverTxn.getWallet())
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.IN)  // ✅ Reversal: Original was OUT, refund is IN
                .actorKind(ActorKind.USER)
                .actorUser(driverTxn.getActorUser())
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)  // ✅ FIX P0-REFUND: Idempotency
                .pspRef(pspRef)  // ✅ FIX P0-REFUND: Ledger correlation
                .beforeAvail(availableBalance)
                .afterAvail(availableBalance.add(amount))
                .beforePending(pendingBalance)
                .afterPending(pendingBalance)
                .note("Refund for failed payout (reversal of groupId: " + originalGroupId + "): " + pspRef)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();
            
            // ✅ FIX P0-REFUND: Create system refund transaction (reversal of original system OUT)
            // Original PAYOUT: System.MASTER OUT → Refund: System.MASTER IN (reversal)
            // Logic: System phải nhận lại tiền từ user (reversal của original OUT)
            // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint violation)
            Transaction systemRefundTxn = Transaction.builder()
                .groupId(refundGroupId)
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.IN)  // ✅ Reversal: Original was OUT, refund is IN
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .pspRef(null)  // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint)
                .note("System refund for failed payout (reversal of groupId: " + originalGroupId + "): " + pspRef)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();
            
            transactionRepository.save(userRefundTxn);
            transactionRepository.save(systemRefundTxn);
            
            // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo refund
            balanceCalculationService.invalidateBalanceCache(driverTxn.getWallet().getWalletId());
            
            log.info("Created refund transactions for failed payout: pspRef={}, originalGroupId={}, refundGroupId={}, amount={}", 
                pspRef, originalGroupId, refundGroupId, amount);
        }

        log.info("Payout failed for pspRef: {} with reason: {}", pspRef, reason);
    }

    @Override
    @Transactional
    public List<Transaction> refundRide(UUID originalGroupId, Integer riderId, Integer driverId,
                                       BigDecimal refundAmount, String description) {
        validateRefundInput(originalGroupId, riderId, driverId, refundAmount);

        // ✅ FIX P0-REFUND: Find original transactions và validate
        List<Transaction> originalTransactions = transactionRepository.findByGroupId(originalGroupId);
        if (originalTransactions.isEmpty()) {
            throw new NotFoundException("Original transactions not found for groupId: " + originalGroupId);
        }
        
        // ✅ FIX P0-REFUND: Check if already refunded (idempotency)
        String idempotencyKey = "REFUND_RIDE_" + originalGroupId.toString() + "_" + refundAmount;
        Optional<Transaction> existingRefund = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRefund.isPresent()) {
            log.info("Refund already processed for originalGroupId: {}, idempotencyKey: {}", originalGroupId, idempotencyKey);
            // Return existing refund transactions
            return transactionRepository.findByGroupId(existingRefund.get().getGroupId());
        }

        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));

        User rider = userRepository.findById(riderId).orElseThrow(() -> new NotFoundException("Rider not found"));
        User driver = userRepository.findById(driverId).orElseThrow(() -> new NotFoundException("Driver not found"));

        // ✅ SSOT: Check balance từ ledger
        BigDecimal driverAvailableBalance = balanceCalculationService.calculateAvailableBalance(driverWallet.getWalletId());
        if (driverAvailableBalance.compareTo(refundAmount) < 0) {
            throw new ValidationException("Driver has insufficient balance for refund. Available: " + driverAvailableBalance);
        }

        // ✅ SSOT: Get balances từ ledger for snapshots
        BigDecimal riderAvailableBalance = balanceCalculationService.calculateAvailableBalance(riderWallet.getWalletId());
        BigDecimal riderPendingBalance = balanceCalculationService.calculatePendingBalance(riderWallet.getWalletId());
        BigDecimal driverPendingBalance = balanceCalculationService.calculatePendingBalance(driverWallet.getWalletId());

        UUID refundGroupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // ✅ FIX P0-REFUND: Mark original transactions as REVERSED (reversal entries)
        for (Transaction originalTxn : originalTransactions) {
            if (originalTxn.getStatus() == TransactionStatus.SUCCESS) {
                originalTxn.setStatus(TransactionStatus.REVERSED);
                originalTxn.setNote(originalTxn.getNote() + " | Reversed by refund groupId: " + refundGroupId);
                transactionRepository.save(originalTxn);
            }
        }

        // ✅ SSOT: Create rider credit transaction (reversal of original OUT)
        Transaction riderCredit = Transaction.builder()
                .groupId(refundGroupId)
                .wallet(riderWallet)  // ✅ Set wallet relationship
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.IN)  // ✅ Reversal: Original was OUT, refund is IN
                .actorKind(ActorKind.USER)
                .actorUser(rider)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)  // ✅ FIX P0-REFUND: Idempotency
                .beforeAvail(riderAvailableBalance)  // Snapshot for audit
                .afterAvail(riderAvailableBalance.add(refundAmount))  // Snapshot for audit
                .beforePending(riderPendingBalance)  // Snapshot for audit
                .afterPending(riderPendingBalance)  // Snapshot for audit
                .note("Refund credit (reversal of groupId: " + originalGroupId + ") - " + description)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();

        // ✅ SSOT: Create driver debit transaction (reversal of original IN)
        Transaction driverDebit = Transaction.builder()
                .groupId(refundGroupId)
                .wallet(driverWallet)  // ✅ Set wallet relationship
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.OUT)  // ✅ Reversal: Original was IN, refund is OUT
                .actorKind(ActorKind.USER)
                .actorUser(driver)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(driverAvailableBalance)  // Snapshot for audit
                .afterAvail(driverAvailableBalance.subtract(refundAmount))  // Snapshot for audit
                .beforePending(driverPendingBalance)  // Snapshot for audit
                .afterPending(driverPendingBalance)  // Snapshot for audit
                .note("Refund debit (reversal of groupId: " + originalGroupId + ") - " + description)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();

        transactions.add(transactionRepository.save(riderCredit));
        transactions.add(transactionRepository.save(driverDebit));

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo refund
        balanceCalculationService.invalidateBalanceCache(riderWallet.getWalletId());
        balanceCalculationService.invalidateBalanceCache(driverWallet.getWalletId());

        log.info("Processed refund for original groupId {} - Amount: {}, refundGroupId: {}", 
            originalGroupId, refundAmount, refundGroupId);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundTopup(Integer userId, BigDecimal refundAmount, String pspRef, String description) {
        validateRefundTopupInput(userId, refundAmount, pspRef);

        // ✅ FIX P0-REFUND: Find original TOPUP transactions by pspRef
        List<Transaction> originalTransactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.SUCCESS);
        if (originalTransactions.isEmpty()) {
            throw new NotFoundException("Original TOPUP transactions not found for pspRef: " + pspRef);
        }
        
        UUID originalGroupId = originalTransactions.stream()
            .findFirst()
            .map(Transaction::getGroupId)
            .orElse(null);
        
        // ✅ FIX P0-REFUND: Check idempotency
        String idempotencyKey = "REFUND_TOPUP_" + pspRef + "_" + refundAmount;
        Optional<Transaction> existingRefund = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRefund.isPresent()) {
            log.info("Refund already processed for topup pspRef: {}, idempotencyKey: {}", pspRef, idempotencyKey);
            // Return existing refund transactions
            return transactionRepository.findByGroupId(existingRefund.get().getGroupId());
        }

        Wallet userWallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("User wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // ✅ SSOT: Check balance từ ledger
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(userWallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(userWallet.getWalletId());
        
        if (availableBalance.compareTo(refundAmount) < 0) {
            throw new ValidationException("User has insufficient balance for refund. Available: " + availableBalance);
        }

        // ✅ FIX P0-REFUND: Mark original transactions as REVERSED (reversal entries)
        for (Transaction originalTxn : originalTransactions) {
            if (originalTxn.getStatus() == TransactionStatus.SUCCESS) {
                originalTxn.setStatus(TransactionStatus.REVERSED);
                originalTxn.setNote(originalTxn.getNote() + " | Reversed by refund");
                transactionRepository.save(originalTxn);
            }
        }

        UUID refundGroupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // ✅ SSOT: Create refund transaction (reversal of original user IN)
        // Original: User IN → Refund: User OUT (reversal)
        Transaction userDebit = Transaction.builder()
                .groupId(refundGroupId)
                .wallet(userWallet)  // ✅ Set wallet relationship
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.OUT)  // ✅ Reversal: Original was IN, refund is OUT
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)  // ✅ FIX P0-REFUND: Ledger correlation
                .idempotencyKey(idempotencyKey)  // ✅ FIX P0-REFUND: Idempotency
                .beforeAvail(availableBalance)  // Snapshot for audit
                .afterAvail(availableBalance.subtract(refundAmount))  // Snapshot for audit
                .beforePending(pendingBalance)  // Snapshot for audit
                .afterPending(pendingBalance.add(refundAmount))  // Snapshot for audit
                .note("Topup refund (reversal of groupId: " + originalGroupId + ") - " + description)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();

        // ✅ FIX P0-REFUND: Create system refund transaction (reversal of original system OUT)
        // Original TOPUP: System.MASTER OUT → Refund: System.MASTER IN (reversal)
        // Logic: System phải nhận lại tiền từ user (reversal của original OUT)
        // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint violation)
        Transaction systemCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(refundGroupId)
                .direction(TransactionDirection.IN)  // ✅ FIX: Reversal của System.MASTER OUT → System.MASTER IN
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(null)  // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint)
                .note("System refund credit (reversal of groupId: " + originalGroupId + ") - " + description)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();

        transactions.add(transactionRepository.save(userDebit));
        transactions.add(transactionRepository.save(systemCredit));

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo refund
        balanceCalculationService.invalidateBalanceCache(userWallet.getWalletId());

        log.info("Initiated topup refund for user {} with amount {} and pspRef {}, originalGroupId: {}, refundGroupId: {}", 
            userId, refundAmount, pspRef, originalGroupId, refundGroupId);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundPayout(Integer driverId, BigDecimal refundAmount, String pspRef, String description) {
        validateRefundPayoutInput(driverId, refundAmount, pspRef);

        // ✅ FIX P0-REFUND: Find original PAYOUT transactions by pspRef
        List<Transaction> originalTransactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.SUCCESS);
        if (originalTransactions.isEmpty()) {
            throw new NotFoundException("Original PAYOUT transactions not found for pspRef: " + pspRef);
        }
        
        UUID originalGroupId = originalTransactions.stream()
            .findFirst()
            .map(Transaction::getGroupId)
            .orElse(null);
        
        // ✅ FIX P0-REFUND: Check idempotency
        String idempotencyKey = "REFUND_PAYOUT_" + pspRef + "_" + refundAmount;
        Optional<Transaction> existingRefund = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRefund.isPresent()) {
            log.info("Refund already processed for payout pspRef: {}, idempotencyKey: {}", pspRef, idempotencyKey);
            // Return existing refund transactions
            return transactionRepository.findByGroupId(existingRefund.get().getGroupId());
        }

        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found"));

        // ✅ SSOT: Get balances từ ledger for snapshots
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(driverWallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(driverWallet.getWalletId());

        // ✅ FIX P0-REFUND: Mark original transactions as REVERSED (reversal entries)
        for (Transaction originalTxn : originalTransactions) {
            if (originalTxn.getStatus() == TransactionStatus.SUCCESS) {
                originalTxn.setStatus(TransactionStatus.REVERSED);
                originalTxn.setNote(originalTxn.getNote() + " | Reversed by refund");
                transactionRepository.save(originalTxn);
            }
        }

        UUID refundGroupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // ✅ SSOT: Create refund transaction (reversal of original driver OUT)
        // Original: Driver OUT → Refund: Driver IN (reversal)
        Transaction driverCredit = Transaction.builder()
                .groupId(refundGroupId)
                .wallet(driverWallet)  // ✅ Set wallet relationship
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.IN)  // ✅ Reversal: Original was OUT, refund is IN
                .actorKind(ActorKind.USER)
                .actorUser(driver)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)  // ✅ FIX P0-REFUND: Ledger correlation
                .idempotencyKey(idempotencyKey)  // ✅ FIX P0-REFUND: Idempotency
                .beforeAvail(availableBalance)  // Snapshot for audit
                .afterAvail(availableBalance.add(refundAmount))  // Snapshot for audit
                .beforePending(pendingBalance)  // Snapshot for audit
                .afterPending(pendingBalance)  // Snapshot for audit
                .note("Payout refund (reversal of groupId: " + originalGroupId + ") - " + description)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();

        // ✅ FIX P0-REFUND: Create system refund transaction (reversal of original system OUT)
        // Original: System.MASTER OUT → Refund: System.MASTER IN (reversal)
        // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint violation)
        Transaction systemCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(refundGroupId)
                .direction(TransactionDirection.IN)  // ✅ Reversal: Original was OUT, refund is IN
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(null)  // ✅ FIX: System transaction không có pspRef (tránh UNIQUE constraint)
                .note("System refund credit (reversal of groupId: " + originalGroupId + ") - " + description)  // ✅ FIX P0-REFUND: Ledger correlation
                .build();

        transactions.add(transactionRepository.save(driverCredit));
        transactions.add(transactionRepository.save(systemCredit));

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo refund
        balanceCalculationService.invalidateBalanceCache(driverWallet.getWalletId());

        log.info("Initiated payout refund for driver {} with amount {} and pspRef {}, originalGroupId: {}, refundGroupId: {}", 
            driverId, refundAmount, pspRef, originalGroupId, refundGroupId);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundAdjustment(Integer userId, BigDecimal refundAmount, Integer adminUserId, String reason) {
        validateRefundAdjustmentInput(userId, refundAmount, adminUserId, reason);

        Wallet userWallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("User wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // ✅ SSOT: Get balances từ ledger for snapshots
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(userWallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(userWallet.getWalletId());

        // ✅ SSOT: Create refund transaction (credit to user)
        Transaction userCredit = Transaction.builder()
                .groupId(groupId)
                .wallet(userWallet)  // ✅ Set wallet relationship
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(availableBalance)  // Snapshot for audit
                .afterAvail(availableBalance.add(refundAmount))  // Snapshot for audit
                .beforePending(pendingBalance)  // Snapshot for audit
                .afterPending(pendingBalance)  // Snapshot for audit
                .note("Adjustment refund - " + reason)
                .build();

        // Create system debit transaction (debit from system master wallet)
        Transaction systemDebit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .note("System adjustment refund - " + reason)
                .build();

        transactions.add(transactionRepository.save(userCredit));
        transactions.add(transactionRepository.save(systemDebit));

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query

        log.info("Processed adjustment refund for user {} with amount {} by admin {}", userId, refundAmount, adminUserId);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundPromoCredit(Integer userId, BigDecimal refundAmount, String promoCode, String description) {
        validateRefundPromoInput(userId, refundAmount, promoCode);

        Wallet userWallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("User wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // ✅ SSOT: Get balances từ ledger for snapshots
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(userWallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(userWallet.getWalletId());

        // ✅ SSOT: Create refund transaction (debit from user)
        Transaction userDebit = Transaction.builder()
                .groupId(groupId)
                .wallet(userWallet)  // ✅ Set wallet relationship
                .type(TransactionType.REFUND)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(availableBalance)  // Snapshot for audit
                .afterAvail(availableBalance.subtract(refundAmount))  // Snapshot for audit
                .beforePending(pendingBalance)  // Snapshot for audit
                .afterPending(pendingBalance)  // Snapshot for audit
                .note("Promo credit refund - " + description)
                .build();

        // Create system credit transaction (credit to promo wallet)
        Transaction promoCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.PROMO)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .note("Promo wallet refund credit - " + description)
                .build();

        transactions.add(transactionRepository.save(userDebit));
        transactions.add(transactionRepository.save(promoCredit));

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query

        log.info("Processed promo credit refund for user {} with amount {} and promo code {}", userId, refundAmount, promoCode);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> processRefund(Integer refundId, String pspRef, String description) {
//        if (refundId == null) {
//            throw new ValidationException("Refund ID cannot be null");
//        }
//        if (pspRef == null || pspRef.trim().isEmpty()) {
//            throw new ValidationException("PSP reference cannot be null or empty");
//        }
//
//        // Find the original transaction to refund
//        Transaction originalTransaction = transactionRepository.findById(refundId)
//                .orElseThrow(() -> new NotFoundException("Transaction not found with ID: " + refundId));
//
//        if (originalTransaction.getStatus() != TransactionStatus.SUCCESS) {
//            throw new ValidationException("Can only refund successful transactions");
//        }
//
//        // Determine refund type based on original transaction type
//        switch (originalTransaction.getType()) {
//            case TOPUP:
//                return refundTopup(originalTransaction.getActorUser().getUserId(),
//                                 originalTransaction.getAmount(), pspRef, description);
//            case PAYOUT:
//                return refundPayout(originalTransaction.getSharedRide().getDriver().getDriverId(),
//                                   originalTransaction.getAmount(), pspRef, description);
//            case CAPTURE_FARE:
//                // For ride refunds, we need the original group ID
//                return refundRide(originalTransaction.getGroupId(),
//                                 originalTransaction.getSharedRide().get,
//                                 originalTransaction.getSharedRide().getDriver().getDriverId(),
//                                 originalTransaction.getAmount(), description);
//            default:
//                throw new ValidationException("Cannot refund transaction type: " + originalTransaction.getType());
//        }
        throw new UnsupportedOperationException("Method not implemented yet");
    }

    @Override
    @Transactional
    public void handleRefundSuccess(String pspRef) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending refund transactions found for pspRef: " + pspRef);
        }

        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Transaction status = SUCCESS nên sẽ được tính vào balance
        // Pending balance sẽ tự động giảm khi tính từ ledger

        log.info("Refund success for pspRef: {}", pspRef);
    }

    @Override
    @Transactional
    public void handleRefundFailed(String pspRef, String reason) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending refund transactions found for pspRef: " + pspRef);
        }

        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);
            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Transaction status = FAILED nên sẽ không được tính vào balance
        // Balance sẽ tự động được tính lại từ ledger

        log.info("Refund failed for pspRef: {} - Reason: {}", pspRef, reason);
    }

    @Override
    public List<Transaction> getTransactionsByGroupId(UUID groupId) {
        if (groupId == null) {
            throw new ValidationException("Group ID cannot be null");
        }
        return transactionRepository.findByGroupId(groupId);
    }

    @Override
    public BigDecimal calculateCommission(BigDecimal amount, BigDecimal commissionRate) {
        if (amount == null || commissionRate == null) {
            throw new ValidationException("Amount and commission rate cannot be null");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException("Commission rate must be between 0 and 1");
        }
        return amount.multiply(commissionRate).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAllTransactions(Pageable pageable,
                                                                String type,
                                                                String status,
                                                                String direction,
                                                                String actorKind,
                                                                String dateFrom,
                                                                String dateTo) {
        TransactionType typeEnum = parseTransactionType(type);
        TransactionStatus statusEnum = parseTransactionStatus(status);
        TransactionDirection directionEnum = parseTransactionDirection(direction);
        ActorKind actorKindEnum = parseActorKind(actorKind);
        LocalDateTime startDateTime = parseBoundaryDate(dateFrom, true);
        LocalDateTime endDateTime = parseBoundaryDate(dateTo, false);
        validateDateRangeLimit(startDateTime, endDateTime);

        Specification<Transaction> spec = Specification.where(null);
        if (typeEnum != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), typeEnum));
        }
        if (statusEnum != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusEnum));
        }
        if (directionEnum != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("direction"), directionEnum));
        }
        if (actorKindEnum != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorKind"), actorKindEnum));
        }
        if (startDateTime != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
        }
        if (endDateTime != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
        }

        Page<Transaction> transactionsPage = transactionRepository.findAll(spec, pageable);
        List<TransactionResponse> transactions = transactionsPage.getContent().stream()
                .map(transactionMapper::mapToTransactionResponse)
                .collect(Collectors.toList());
        return buildPageResponse(transactionsPage, transactions);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getUserHistoryTransactions(Authentication authentication,
                                                                        Pageable pageable,
                                                                        String type,
                                                                        String status) {
        Integer userId = extractUserId(authentication);

        TransactionType typeEnum = parseTransactionType(type);
        TransactionStatus statusEnum = parseTransactionStatus(status);

        Page<Transaction> page = transactionRepository.findUserHistory(userId, typeEnum, statusEnum, pageable);
        List<TransactionResponse> items = page.getContent().stream()
                .map(transactionMapper::mapToTransactionResponse)
                .collect(Collectors.toList());
        return buildPageResponse(page, items);
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validateTopupInput(Integer userId, BigDecimal amount, String pspRef) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private TransactionType parseTransactionType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return TransactionType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid transaction type: " + type);
        }
    }

    private TransactionStatus parseTransactionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TransactionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid transaction status: " + status);
        }
    }

    private TransactionDirection parseTransactionDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        try {
            return TransactionDirection.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid transaction direction: " + direction);
        }
    }

    private ActorKind parseActorKind(String actorKind) {
        if (actorKind == null || actorKind.isBlank()) {
            return null;
        }
        try {
            return ActorKind.valueOf(actorKind.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid actor kind: " + actorKind);
        }
    }

    private LocalDateTime parseBoundaryDate(String date, boolean startOfDay) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(date.trim());
            return startOfDay ? localDate.atStartOfDay() : localDate.atTime(LocalTime.MAX);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Invalid date format (expected yyyy-MM-dd): " + date);
        }
    }

    private void validateDateRangeLimit(LocalDateTime start, LocalDateTime end) {
        LocalDate minAllowedDate = LocalDate.now().minusMonths(3);
        LocalDate today = LocalDate.now();

        if (start != null && start.toLocalDate().isBefore(minAllowedDate)) {
            throw new ValidationException("Start date cannot be earlier than 3 months ago");
        }
        if (end != null && end.toLocalDate().isBefore(minAllowedDate)) {
            throw new ValidationException("End date cannot be earlier than 3 months ago");
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("End date must be on or after start date");
        }
        if (end != null && end.toLocalDate().isAfter(today)) {
            throw new ValidationException("End date cannot be in the future");
        }
    }

    private void validateHoldInput(Integer riderId, BigDecimal amount, Integer bookingId) {
        if (riderId == null) {
            throw new ValidationException("Rider ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (bookingId == null) {
            throw new ValidationException("Booking ID cannot be null");
        }
    }

    private void validateCaptureInput(Integer riderId, Integer driverId, BigDecimal totalFare, BigDecimal commissionRate) {
        if (riderId == null) {
            throw new ValidationException("Rider ID cannot be null");
        }
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (totalFare == null || totalFare.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Total fare must be greater than zero");
        }
        if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException("Commission rate must be between 0 and 1");
        }
    }

    private void validatePayoutInput(Integer driverId, BigDecimal amount, String pspRef) {
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateRefundInput(UUID originalGroupId, Integer riderId, Integer driverId, BigDecimal refundAmount) {
        if (originalGroupId == null) {
            throw new ValidationException("Original group ID cannot be null");
        }
        if (riderId == null) {
            throw new ValidationException("Rider ID cannot be null");
        }
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
    }

    private void validateRefundTopupInput(Integer userId, BigDecimal refundAmount, String pspRef) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateRefundPayoutInput(Integer driverId, BigDecimal refundAmount, String pspRef) {
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateRefundAdjustmentInput(Integer userId, BigDecimal refundAmount, Integer adminUserId, String reason) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (adminUserId == null) {
            throw new ValidationException("Admin user ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Refund reason cannot be null or empty");
        }
    }

    private void validateRefundPromoInput(Integer userId, BigDecimal refundAmount, String promoCode) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (promoCode == null || promoCode.trim().isEmpty()) {
            throw new ValidationException("Promo code cannot be null or empty");
        }
    }

    private void validatePromoInput(Integer userId, BigDecimal amount, String promoCode) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (promoCode == null || promoCode.trim().isEmpty()) {
            throw new ValidationException("Promo code cannot be null or empty");
        }
    }

    private Integer extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ValidationException("Unauthenticated request");
        }
        // authentication.getName() is expected to be email; map to userId
        return userRepository.findByEmail(authentication.getName())
                .map(User::getUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + authentication.getName()));
    }

    private void validateAdjustmentInput(Integer userId, BigDecimal amount, Integer adminUserId, String reason) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new ValidationException("Adjustment amount cannot be zero");
        }
        if (adminUserId == null) {
            throw new ValidationException("Admin user ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Adjustment reason cannot be null or empty");
        }
    }

    private void validateCreateTransactionRequest(CreateTransactionRequest request) {
        TransactionType type = request.type();
        TransactionDirection direction = request.direction();
        ActorKind actorKind = request.actorKind();
        Integer actorUserId = request.actorUserId();
        SystemWallet systemWallet = request.systemWallet();
        BigDecimal amount = request.amount();
        TransactionStatus status = request.status();
        Integer sharedRideId = request.sharedRideId();
        Integer sharedRideRequestId = request.sharedRideRequestId();

        if (type == null) {
            throw new ValidationException("Transaction type is required");
        }
        if (direction == null) {
            throw new ValidationException("Transaction direction is required");
        }
        if (actorKind == null) {
            throw new ValidationException("Actor kind is required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }

        if (actorKind == ActorKind.USER && actorUserId == null) {
            throw new ValidationException("Actor user ID is required for USER transactions");
        }
        if (actorKind != ActorKind.USER && actorUserId != null) {
            throw new ValidationException("Actor user ID must be null for non-USER transactions");
        }

        if (actorKind == ActorKind.SYSTEM && systemWallet == null) {
            throw new ValidationException("System wallet is required for SYSTEM transactions");
        }
        if (actorKind != ActorKind.SYSTEM && systemWallet != null) {
            throw new ValidationException("System wallet must be null for non-SYSTEM transactions");
        }
        if (sharedRideId != null && TransactionType.CAPTURE_FARE != type) {
            throw new ValidationException("Shared ride ID can only be set for CAPTURE_FARE transactions");
        }
        if (sharedRideRequestId != null && !(TransactionType.HOLD_CREATE == type || TransactionType.HOLD_RELEASE == type) ) {
            throw new ValidationException("Shared ride request ID can only be set for HOLD_CREATE or HOLD_RELEASE transactions");
        }

        validateTypeCombo(type, direction, actorKind, systemWallet);

//        if (type == TransactionType.CAPTURE_FARE) {
//            validateCaptureFareAlignment(direction, actorKind, systemWallet, actorUserId, riderUserId, driverUserId);
//        }

        validateStatusByType(type, status);
    }

    private void validateTypeCombo(TransactionType type, TransactionDirection direction,
                                   ActorKind actorKind, SystemWallet systemWallet) {
        switch (type) {
            case TOPUP -> {
                boolean validTopup = (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.MASTER && direction == TransactionDirection.IN) ||
                    (actorKind == ActorKind.USER && direction == TransactionDirection.IN);
                if (!validTopup) {
                    throw new ValidationException("Invalid TOPUP combination: must be (SYSTEM/MASTER/IN) or (USER/*/IN)");
                }
            }
            case HOLD_CREATE, HOLD_RELEASE -> {
                if (!(actorKind == ActorKind.USER && direction == TransactionDirection.INTERNAL)) {
                    throw new ValidationException("Invalid " + type + " combination: must be (USER/*/INTERNAL)");
                }
            }
            case CAPTURE_FARE -> {
                boolean validCapture = (actorKind == ActorKind.USER && (direction == TransactionDirection.IN || direction == TransactionDirection.OUT)) ||
                    (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.COMMISSION && direction == TransactionDirection.IN);
                if (!validCapture) {
                    throw new ValidationException("Invalid CAPTURE_FARE combination: must be (USER/*/IN|OUT) or (SYSTEM/COMMISSION/IN)");
                }
            }
            case PAYOUT -> {
                boolean validPayout = (actorKind == ActorKind.USER && direction == TransactionDirection.OUT) ||
                    (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.MASTER && direction == TransactionDirection.OUT);
                if (!validPayout) {
                    throw new ValidationException("Invalid PAYOUT combination: must be (USER/*/OUT) or (SYSTEM/MASTER/OUT)");
                }
            }
//            case PROMO_CREDIT -> {
//                boolean validPromo = (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.PROMO && direction == TransactionDirection.OUT) ||
//                    (actorKind == ActorKind.USER && direction == TransactionDirection.IN);
//                if (!validPromo) {
//                    throw new ValidationException("Invalid PROMO_CREDIT combination: must be (SYSTEM/PROMO/OUT) or (USER/*/IN)");
//                }
//            }
            case ADJUSTMENT -> {
                // ADJUSTMENT allows any combination - most flexible
            }
            default -> throw new ValidationException("Unsupported transaction type: " + type);
        }
    }

//    private void validateCaptureFareAlignment(TransactionDirection direction, ActorKind actorKind,
//                                              SystemWallet systemWallet, Integer actorUserId) {
//        if (actorKind == ActorKind.USER && direction == TransactionDirection.OUT) {
//            if (riderUserId == null || !riderUserId.equals(actorUserId)) {
//                throw new ValidationException("For CAPTURE_FARE OUT transactions, actor user must be the rider");
//            }
//        } else if (actorKind == ActorKind.USER && direction == TransactionDirection.IN) {
//            if (driverUserId == null || !driverUserId.equals(actorUserId)) {
//                throw new ValidationException("For CAPTURE_FARE IN transactions, actor user must be the driver");
//            }
//        } else if (actorKind == ActorKind.SYSTEM && direction == TransactionDirection.IN) {
//            if (systemWallet != SystemWallet.COMMISSION) {
//                throw new ValidationException("For CAPTURE_FARE SYSTEM IN transactions, must use COMMISSION wallet");
//            }
//        }
//    }

    private void validateStatusByType(TransactionType type, TransactionStatus status) {
        if (status == null) {
            return;
        }

        switch (type) {
            case TOPUP -> {
                if (status != TransactionStatus.PENDING && status != TransactionStatus.SUCCESS &&
                    status != TransactionStatus.FAILED && status != TransactionStatus.REVERSED) {
                    throw new ValidationException("TOPUP transactions can only have status: PENDING, SUCCESS, FAILED, or REVERSED");
                }
            }
            case PAYOUT -> {
                //implement later
            }
            default -> {
                if (status != TransactionStatus.SUCCESS) {
                    throw new ValidationException(type + " transactions can only have SUCCESS status");
                }
            }
        }
    }


    private void sendTopupSuccessEmail(Integer userId, BigDecimal amount, Integer txnId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null) {
                Wallet wallet = walletRepository.findByUser_UserId(userId).orElse(null);
                // ✅ SSOT: Tính balance từ ledger
                BigDecimal newBalance = wallet != null ? 
                    balanceCalculationService.calculateAvailableBalance(wallet.getWalletId()) : BigDecimal.ZERO;

                emailService.sendTopUpSuccessEmail(
                        user.getEmail(),
                        user.getFullName(),
                        amount,
                        String.valueOf(txnId),
                        newBalance
                );

                log.info("Top-up success email sent to user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to send top-up success email to user {}: {}", userId, e.getMessage());
        }
    }

    private void sendTopupFailedEmail(Integer userId, BigDecimal amount, Integer txnId, String reason) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null) {
                emailService.sendPaymentFailedEmail(
                        user.getEmail(),
                        user.getFullName(),
                        amount,
                        String.valueOf(txnId),
                        reason
                );

                log.info("Top-up failed email sent to user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to send top-up failed email to user {}: {}", userId, e.getMessage());
        }
    }

    private <T> PageResponse<T> buildPageResponse(Page<?> page, List<T> content) {
        return PageResponse.<T>builder()
                .data(content)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(page.getNumber() + 1)
                        .pageSize(page.getSize())
                        .totalPages(page.getTotalPages())
                        .totalRecords(page.getTotalElements())
                        .build())
                .build();
    }
}

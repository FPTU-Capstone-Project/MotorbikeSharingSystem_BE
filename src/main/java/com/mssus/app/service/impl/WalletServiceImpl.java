package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.SystemWallet;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.mssus.app.common.exception.InvalidPayoutStateException;
import com.mssus.app.common.exception.PayosClientException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.PayoutMode;
import com.mssus.app.dto.request.PayoutOrderRequest;
import com.mssus.app.dto.request.wallet.PayoutInitRequest;
import com.mssus.app.dto.response.wallet.DriverEarningsResponse;
import com.mssus.app.dto.response.wallet.PayoutInitResponse;
import com.mssus.app.dto.response.wallet.WalletResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.dto.response.wallet.PendingPayoutResponse;
import com.mssus.app.dto.response.wallet.PayoutProcessResponse;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.BankService;
import com.mssus.app.service.FileUploadService;
import com.mssus.app.service.PayoutNotificationService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final SharedRideRequestRepository sharedRideRequestRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final FileUploadService fileUploadService;
    private final BalanceCalculationService balanceCalculationService;
    private final PayOSPayoutClient payOSPayoutClient;
    private final PayoutNotificationService payoutNotificationService;
    private final BankService bankService;

    @Value("${app.payout.mode:HYBRID}")
    private String defaultPayoutMode;

    @Value("${app.payout.manual-review-threshold:500000}")
    private BigDecimal manualReviewThreshold;
    
    // ✅ SSOT: Balance luôn được tính từ ledger, không update trực tiếp

//    @Override
//    public void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount) {
//        Wallet wallet = walletRepository.findByUser_UserId(userId)
//                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + userId));
//
//        wallet.setShadowBalance(wallet.getShadowBalance().add(amount));
//        wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
//        walletRepository.save(wallet);
//    }
//
//    @Override
//    public void increasePendingBalance(Integer userId, BigDecimal amount) {
//        int updatedRows = walletRepository.increasePendingBalance(userId, amount);
//        if (updatedRows == 0) {
//            throw new NotFoundException("Không tìm thấy ví cho người dùng hoặc cập nhật thất bại: " + userId);
//        }
//    }
//
//    @Override
//    public void decreasePendingBalance(Integer userId, BigDecimal amount) {
//        int updatedRows = walletRepository.decreasePendingBalance(userId, amount);
//        if (updatedRows == 0) {
//            throw new ValidationException("Không thể giảm số dư chờ xử lý cho người dùng: " + userId + ". Không tìm thấy ví hoặc số dư chờ xử lý không đủ.");
//        }
//    }
//
//    @Override
//    @Transactional
//    public void increaseShadowBalance(Integer userId, BigDecimal amount) {
//        int updatedRows = walletRepository.increaseShadowBalance(userId, amount);
//        if (updatedRows == 0) {
//            throw new NotFoundException("Không tìm thấy ví cho người dùng hoặc cập nhật thất bại: " + userId);
//        }
//    }
//
//    @Override
//    @Transactional
//    public void decreaseShadowBalance(Integer userId, BigDecimal amount) {
//        int updatedRows = walletRepository.decreaseShadowBalance(userId, amount);
//        if (updatedRows == 0) {
//            // This can also mean insufficient shadow balance
//            throw new ValidationException("Không thể giảm số dư khả dụng cho người dùng: " + userId + ". Không tìm thấy ví hoặc số dư khả dụng không đủ.");
//        }
//    }
//
//    @Override
//    public void transferPendingToAvailable(Integer userId, BigDecimal amount) {
//        Wallet wallet = walletRepository.findByUser_UserId(userId)
//                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + userId));
//        BigDecimal shadowBalance = BigDecimal.ZERO;
//        if (wallet.getShadowBalance() != null){
//            shadowBalance = wallet.getShadowBalance();
//        }
//        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
//        wallet.setShadowBalance(shadowBalance.add(amount));
//        wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
//        walletRepository.save(wallet);
//    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getBalance(Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Xác thực không được để trống");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với email: " + email));

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + user.getUserId()));
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());
        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .userId(user.getUserId())
                .availableBalance(availableBalance)
                .pendingBalance(pendingBalance)
                .totalToppedUp(wallet.getTotalToppedUp())
                .totalSpent(wallet.getTotalSpent())
                .isActive(wallet.getIsActive())
                .lastSyncedAt(wallet.getLastSyncedAt())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(Integer userId) {
        if (userId == null) {
            throw new ValidationException("ID người dùng không được để trống");
        }

        return walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + userId));
    }

    // ✅ initiateTopUp đã được move sang TopUpService để tách PayOS integration

    @Override
    @Transactional
    public PayoutInitResponse initiatePayout(PayoutInitRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Xác thực không được để trống");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với email: " + email));

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + user.getUserId()));

        // Validate wallet is active
        if (!wallet.getIsActive()) {
            throw new ValidationException("Ví đã bị đóng băng. Vui lòng liên hệ hỗ trợ.");
        }

        // Validate minimum payout amount (50,000 VND)
        BigDecimal minimumAmount = new BigDecimal("2000");
        if (request.getAmount().compareTo(minimumAmount) < 0) {
            throw new ValidationException("Số tiền rút tối thiểu là 2.000 VNĐ. Yêu cầu: " + request.getAmount());
        }

        // Validate bank account number format (9-16 digits)
        String bankAccountNumber = request.getBankAccountNumber().trim();
        Pattern accountNumberPattern = Pattern.compile("^\\d{9,16}$");
        if (!accountNumberPattern.matcher(bankAccountNumber).matches()) {
            throw new ValidationException("Số tài khoản ngân hàng phải có 9-16 chữ số");
        }

        // Validate account holder name (at least 2 characters)
        String accountHolderName = request.getAccountHolderName().trim();
        if (accountHolderName.length() < 2) {
            throw new ValidationException("Tên chủ tài khoản phải có ít nhất 2 ký tự");
        }

        // Validate bank BIN format and existence
        String bankBin = request.getBankBin().trim();
        if (!bankBin.matches("^\\d{6}$")) {
            throw new ValidationException("Mã BIN ngân hàng phải có 6 chữ số");
        }

        // Validate bank BIN exists in supported banks list
        if (!bankService.isValidBankBin(bankBin)) {
            throw new ValidationException("Mã BIN ngân hàng không hợp lệ hoặc không được hỗ trợ: " + bankBin);
        }

        // Optional: Validate transfer is supported
        var bankInfo = bankService.getBankByBin(bankBin);
        if (bankInfo.isPresent() && (bankInfo.get().getTransferSupported() == null || bankInfo.get().getTransferSupported() != 1)) {
            log.warn("Bank BIN {} may not support transfers: {}", bankBin, bankInfo.get().getName());
            // Warning only, not blocking - let PayOS handle the validation
        }

        List<String> categories = resolveCategories(request.getCategories());

        // Determine payout mode: from request or config-based rule
        PayoutMode payoutMode = determinePayoutMode(request);

        // ✅ SSOT: Check balance từ ledger
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        if (availableBalance.compareTo(request.getAmount()) < 0) {
            throw new ValidationException("Số dư không đủ. Khả dụng: " +
                    availableBalance + ", Yêu cầu: " + request.getAmount());
        }

        // Generate payout reference
        String payoutRef = "PAYOUT-" + System.currentTimeMillis();
        UUID groupId = UUID.randomUUID();

        // Create description with bank account information
        // Store full account number in note for later use in processPayout
        String description = String.format("Payout to %s - %s (%s) [BIN:%s]",
                request.getBankName(),
                maskAccountNumber(bankAccountNumber),
                accountHolderName,
                bankBin);
        
        // Store bank details in note for processPayout to extract
        String noteWithBankDetails = description + 
                " | bankBin:" + bankBin + 
                " | bankAccountNumber:" + bankAccountNumber +
                " | bankName:" + request.getBankName() +
                " | accountHolderName:" + accountHolderName;

        // ✅ SSOT: Get balances từ ledger
        BigDecimal beforeAvail = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        BigDecimal beforePending = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());

        // Calculate balances after transaction (for snapshot only)
        BigDecimal afterAvail = beforeAvail.subtract(request.getAmount());
        BigDecimal afterPending = beforePending.add(request.getAmount());

        // Generate idempotency key
        String idempotencyKey = "PAYOUT_" + payoutRef + "_" + request.getAmount();

        // Check duplicate (idempotency)
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate payout request with idempotency_key: {}", idempotencyKey);
            // Return existing response
            Transaction existingTxn = existing.get();
            String maskedAccount = maskAccountNumber(bankAccountNumber);
            return PayoutInitResponse.builder()
                    .payoutRef(existingTxn.getPspRef())
                    .amount(existingTxn.getAmount())
                    .status(existingTxn.getStatus().name())
                    .estimatedCompletionTime(LocalDateTime.now().plusHours(24).toString())
                    .maskedAccountNumber(maskedAccount)
                    .build();
        }

        // Create USER transaction (OUT direction, PENDING status)
        Transaction userTransaction = Transaction.builder()
                .type(TransactionType.PAYOUT)
                .wallet(wallet)  // ✅ FIX P0-1: Thêm wallet relationship
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(request.getAmount())
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(payoutRef)
                .idempotencyKey(idempotencyKey)  // ✅ FIX P1-4: Thêm idempotency key
                .beforeAvail(beforeAvail)
                .afterAvail(afterAvail)
                .beforePending(beforePending)
                .afterPending(afterPending)
                .note(description)
                .build();

        // Create SYSTEM.MASTER transaction (OUT direction, PENDING status) - mirror transaction
        Transaction systemTransaction = Transaction.builder()
                .type(TransactionType.PAYOUT)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(request.getAmount())
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(payoutRef)
                .note("System payout debit - " + description)
                .build();

        // Store mode in transaction note for later reference
        String noteWithMode = noteWithBankDetails + " | mode:" + payoutMode.name();

        // Update transaction notes with mode and bank details
        userTransaction.setNote(noteWithMode);
        systemTransaction.setNote("System payout debit - " + noteWithMode);

        // Save transactions
        transactionRepository.save(userTransaction);
        transactionRepository.save(systemTransaction);

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query

        // Mask account number (show only last 4 digits)
        String maskedAccount = maskAccountNumber(bankAccountNumber);

        log.info("Initiated payout for user {} with amount {} and pspRef {}. Mode: {}. Balance: {} -> {} (available), {} -> {} (pending)",
                user.getUserId(), request.getAmount(), payoutRef, payoutMode,
                beforeAvail, afterAvail, beforePending, afterPending);

        // Send notification
        payoutNotificationService.notifyPayoutInitiated(user, payoutRef, request.getAmount());

        return PayoutInitResponse.builder()
                .payoutRef(payoutRef)
                .amount(request.getAmount())
                .status("PENDING")
                .estimatedCompletionTime(LocalDateTime.now().plusHours(24).toString())
                .maskedAccountNumber(maskedAccount)
                .build();
    }

    /**
     * Determine payout mode from request or config-based rules.
     */
    private PayoutMode determinePayoutMode(PayoutInitRequest request) {
        // 1. Use mode from request if provided
        if (request.getMode() != null) {
            return request.getMode();
        }

        // 2. Rule-based: amount >= threshold → MANUAL, else AUTOMATIC
        if ("HYBRID".equalsIgnoreCase(defaultPayoutMode) || "AUTOMATIC".equalsIgnoreCase(defaultPayoutMode)) {
            if (request.getAmount().compareTo(manualReviewThreshold) >= 0) {
                return PayoutMode.MANUAL;
            } else {
                return PayoutMode.AUTOMATIC;
            }
        }

        // 3. Default from config
        if ("MANUAL".equalsIgnoreCase(defaultPayoutMode)) {
            return PayoutMode.MANUAL;
        }

        // Default to AUTOMATIC
        return PayoutMode.AUTOMATIC;
    }

    /**
     * Extract payout mode from transaction note.
     */
    private PayoutMode extractPayoutModeFromNote(String note) {
        if (note == null || !note.contains("mode:")) {
            return PayoutMode.MANUAL; // Default to MANUAL for backward compatibility
        }
        try {
            String modeStr = note.substring(note.indexOf("mode:") + 5).split("\\|")[0].trim();
            return PayoutMode.valueOf(modeStr.toUpperCase());
        } catch (Exception e) {
            log.warn("Failed to extract payout mode from note: {}", note, e);
            return PayoutMode.MANUAL;
        }
    }

    /**
     * Mask bank account number (show only last 4 digits)
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private long normalizeToVnd(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("Số tiền rút không được để trống");
        }
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ValidationException("Số tiền rút phải là số nguyên VNĐ");
        }
    }

    private List<String> resolveCategories(List<String> rawCategories) {
        if (rawCategories == null) {
            return Collections.singletonList("payout");
        }

        List<String> cleaned = rawCategories.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase())
                .collect(Collectors.toList());

        if (cleaned.isEmpty()) {
            return Collections.singletonList("payout");
        }

        return cleaned;
    }

    @Override
    @Transactional(readOnly = true)
    public DriverEarningsResponse getDriverEarnings(Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Xác thực không được để trống");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với email: " + email));

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho tài xế: " + user.getUserId()));

        // Calculate earnings from transactions
        List<Transaction> allTransactions = transactionRepository
                .findByActorUserIdOrderByCreatedAtDesc(user.getUserId());

        // Filter driver earnings (CAPTURE_FARE with direction IN)
        List<Transaction> earningsTransactions = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.CAPTURE_FARE)
                .filter(t -> t.getDirection() == TransactionDirection.IN)
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .toList();

        // Calculate total earnings
        BigDecimal totalEarnings = earningsTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count total trips
        int totalTrips = (int) earningsTransactions.stream()
                .map(Transaction::getSharedRide)
                .distinct()
                .count();

        // Calculate this month earnings
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal monthEarnings = earningsTransactions.stream()
                .filter(t -> t.getCreatedAt().isAfter(startOfMonth))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate this week earnings
        LocalDateTime startOfWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay();
        BigDecimal weekEarnings = earningsTransactions.stream()
                .filter(t -> t.getCreatedAt().isAfter(startOfWeek))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate average earnings per trip
        BigDecimal avgEarningsPerTrip = totalTrips > 0 ?
                totalEarnings.divide(BigDecimal.valueOf(totalTrips), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // Calculate total commission paid (estimate based on platform fee, e.g., 20%)
        BigDecimal estimatedCommissionRate = new BigDecimal("0.20");
        BigDecimal totalCommissionPaid = totalEarnings.multiply(estimatedCommissionRate)
                .divide(BigDecimal.ONE.subtract(estimatedCommissionRate), 2, RoundingMode.HALF_UP);

        // ✅ SSOT: Tính balance từ ledger
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());

        return DriverEarningsResponse.builder()
                .availableBalance(availableBalance)
                .pendingEarnings(pendingBalance)
                .totalEarnings(totalEarnings)
                .totalTrips(totalTrips)
                .monthEarnings(monthEarnings)
                .weekEarnings(weekEarnings)
                .avgEarningsPerTrip(avgEarningsPerTrip)
                .totalCommissionPaid(totalCommissionPaid)
                .build();
    }

    @Override
    @Transactional
    public Wallet createWalletForUser(Integer userId) {
        if (userId == null) {
            throw new ValidationException("ID người dùng không được để trống");
        }

        // Check if wallet already exists
        if (walletRepository.existsByUserId(userId)) {
            throw new ValidationException("Ví đã tồn tại cho người dùng: " + userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng: " + userId));

        // ✅ SSOT: Wallet chỉ lưu metadata, không có balance fields
        Wallet wallet = Wallet.builder()
                .user(user)
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .isActive(true)
                .lastSyncedAt(LocalDateTime.now())
                .build();

        Wallet savedWallet = walletRepository.save(wallet);

        log.info("Created wallet for user {} with ID {}", userId, savedWallet.getWalletId());

        return savedWallet;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(Integer userId, BigDecimal amount) {
        if (userId == null) {
            throw new ValidationException("ID người dùng không được để trống");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Số tiền phải không âm");
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + userId));

        // ✅ SSOT: Tính balance từ ledger
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        boolean hasFunds = availableBalance.compareTo(amount) >= 0;

        log.debug("Balance check for user {} - required: {}, available: {}, sufficient: {}",
                userId, amount, availableBalance, hasFunds);

        return hasFunds;
    }

    @Override
    @Transactional
    public void reconcileWalletBalance(Integer userId) {
        if (userId == null) {
            throw new ValidationException("ID người dùng không được để trống");
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + userId));

        // ✅ SSOT: Tính balance từ ledger
        BigDecimal currentShadowBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        BigDecimal currentPendingBalance = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());

        List<Transaction> transactions = transactionRepository
            .findByUserIdAndStatus(userId, TransactionStatus.SUCCESS);

        BigDecimal reconciledShadowBalance = BigDecimal.ZERO;
        BigDecimal reconciledPendingBalance = BigDecimal.ZERO;

        for (Transaction txn : transactions) {
            if (txn.getActorKind() != ActorKind.USER ||
                !userId.equals(txn.getActorUser().getUserId())) {
                continue;
            }

            TransactionType type = txn.getType();
            TransactionDirection direction = txn.getDirection();
            BigDecimal amount = txn.getAmount();

            switch (type) {
                case TOPUP -> {
                    if (direction == TransactionDirection.IN) {
                        reconciledShadowBalance = reconciledShadowBalance.add(amount);
                    }
                }
                case HOLD_CREATE -> {
                    reconciledShadowBalance = reconciledShadowBalance.subtract(amount);
                    reconciledPendingBalance = reconciledPendingBalance.add(amount);
                }
                case HOLD_RELEASE -> {
                    reconciledShadowBalance = reconciledShadowBalance.add(amount);
                    reconciledPendingBalance = reconciledPendingBalance.subtract(amount);
                }
                case CAPTURE_FARE -> {
                    if (direction == TransactionDirection.OUT) {
                        reconciledPendingBalance = reconciledPendingBalance.subtract(amount);
                    } else if (direction == TransactionDirection.IN) {
                        reconciledShadowBalance = reconciledShadowBalance.add(amount);
                    }
                }
                case PAYOUT -> {
                    if (direction == TransactionDirection.OUT) {
                        reconciledShadowBalance = reconciledShadowBalance.subtract(amount);
                    }
                }
                case REFUND, ADJUSTMENT -> {
                    if (direction == TransactionDirection.IN) {
                        reconciledShadowBalance = reconciledShadowBalance.add(amount);
                    } else if (direction == TransactionDirection.OUT) {
                        reconciledShadowBalance = reconciledShadowBalance.subtract(amount);
                    }
                }
            }
        }

        BigDecimal shadowDiff = reconciledShadowBalance.subtract(currentShadowBalance);
        BigDecimal pendingDiff = reconciledPendingBalance.subtract(currentPendingBalance);

        boolean hasDiscrepancy = shadowDiff.compareTo(BigDecimal.ZERO) != 0 ||
            pendingDiff.compareTo(BigDecimal.ZERO) != 0;

        if (hasDiscrepancy) {
            log.warn("Wallet reconciliation discrepancy found for user {}: " +
                    "Shadow difference: {}, Pending difference: {}",
                userId, shadowDiff, pendingDiff);

            // ✅ SSOT: KHÔNG update wallet balance trực tiếp
            // Nếu có discrepancy, cần tạo ADJUSTMENT transaction thay vì update trực tiếp
            // createAdjustmentTransaction(userId, shadowDiff, "Reconciliation adjustment");
            
            wallet.setLastSyncedAt(LocalDateTime.now());
            walletRepository.save(wallet);
            
            log.warn("Balance discrepancy detected. Please create ADJUSTMENT transaction manually. " +
                "Shadow diff: {}, Pending diff: {}", shadowDiff, pendingDiff);

            log.info("Wallet reconciled for user {}: Shadow: {} -> {}, Pending: {} -> {}",
                userId, currentShadowBalance, reconciledShadowBalance,
                currentPendingBalance, reconciledPendingBalance);
        } else {
            wallet.setLastSyncedAt(LocalDateTime.now());
            walletRepository.save(wallet);

            log.info("Wallet reconciliation completed for user {} - No discrepancies found", userId);
        }
    }

    private void createAdjustmentTransaction(Integer userId, BigDecimal amount, String reason) {
        //Should not automatically create adjustment transactions, maybe escalate to admin review first
//        User user = userRepository.findById(userId)
//            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
//
//        Wallet wallet = walletRepository.findByUser_UserId(userId)
//            .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
//
//        TransactionDirection direction = amount.compareTo(BigDecimal.ZERO) > 0 ?
//            TransactionDirection.IN : TransactionDirection.OUT;
//
//        Transaction adjustment = Transaction.builder()
//            .type(TransactionType.ADJUSTMENT)
//            .groupId(UUID.randomUUID())
//            .direction(direction)
//            .actorKind(ActorKind.SYSTEM)
//            .actorUser(user)
//            .amount(amount.abs())
//            .currency("VND")
//            .status(TransactionStatus.SUCCESS)
//            .beforeAvail(wallet.getShadowBalance())
//            .afterAvail(wallet.getShadowBalance().add(amount))
//            .beforePending(wallet.getPendingBalance())
//            .afterPending(wallet.getPendingBalance())
//            .note(reason)
//            .build();
//
//        transactionRepository.save(adjustment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingPayoutResponse> getPendingPayouts() {
        List<Transaction> pendingPayouts = transactionRepository.findByTypeAndStatusAndActorKindUser(
                TransactionType.PAYOUT, TransactionStatus.PENDING);

        return pendingPayouts.stream()
                .map(txn -> {
                    User user = txn.getActorUser();
                    String note = txn.getNote() != null ? txn.getNote() : "";
                    // Extract bank account info from note (format: "Payout to {bank} - {masked} ({holder})")
                    String bankName = extractBankNameFromNote(note);
                    String maskedAccount = extractMaskedAccountFromNote(note);
                    String accountHolder = extractAccountHolderFromNote(note);

                    return PendingPayoutResponse.builder()
                            .payoutRef(txn.getPspRef())
                            .amount(txn.getAmount())
                            .bankName(bankName)
                            .maskedAccountNumber(maskedAccount)
                            .accountHolderName(accountHolder)
                            .userEmail(user != null ? user.getEmail() : "")
                            .userId(user != null ? user.getUserId() : null)
                            .status(txn.getStatus().name())
                            .requestedAt(txn.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PayoutProcessResponse processPayout(String payoutRef, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        // Find PENDING transactions
        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(payoutRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending payout transactions found for pspRef: " + payoutRef);
        }

        Transaction userTransaction = transactions.stream()
                .filter(txn -> txn.getActorKind() == ActorKind.USER && txn.getType() == TransactionType.PAYOUT)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch rút tiền của người dùng cho pspRef: " + payoutRef));

        // Extract payout mode from transaction note
        PayoutMode payoutMode = extractPayoutModeFromNote(userTransaction.getNote());

        // Update all transactions to PROCESSING status
        String processedBy = authentication.getName();
        LocalDateTime processedAt = LocalDateTime.now();
        
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.PROCESSING);
            // Store processedBy in note
            String note = txn.getNote() != null ? txn.getNote() : "";
            note += " | processedBy:" + processedBy + " | processedAt:" + processedAt;
            txn.setNote(note);
            transactionRepository.save(txn);
        }

        // If AUTOMATIC mode, call PayOS API
        if (payoutMode == PayoutMode.AUTOMATIC) {
            try {
                // Extract bank details from note
                String note = userTransaction.getNote();
                String bankBin = extractValueFromNote(note, "bankBin:");
                String bankAccountNumber = extractValueFromNote(note, "bankAccountNumber:");
                String bankName = extractValueFromNote(note, "bankName:");
                String accountHolderName = extractValueFromNote(note, "accountHolderName:");
                String description = extractDescriptionFromNote(note);
                
                if (bankBin == null || bankAccountNumber == null) {
                    throw new ValidationException("Cannot extract bank details from transaction note for automatic payout");
                }

                // Build PayOS request
                String payoutDescription = description != null ? description : 
                        String.format("Payout to %s - %s (%s)", 
                                bankName != null ? bankName : "Bank",
                                maskAccountNumber(bankAccountNumber),
                                accountHolderName != null ? accountHolderName : "");
                
                PayoutOrderRequest payoutOrderRequest = PayoutOrderRequest.builder()
                        .referenceId(payoutRef)
                        .amount(normalizeToVnd(userTransaction.getAmount()))
                        .description("Payout")
                        .toBin(bankBin)
                        .toAccountNumber(bankAccountNumber)
                        .build();

                String idempotencyKey = userTransaction.getIdempotencyKey();
                
                // Call PayOS API
                JsonNode payosResponse = payOSPayoutClient.createPayoutOrder(payoutOrderRequest, idempotencyKey);
                
                // Handle PayOS response
                String payosCode = payosResponse.path("code").asText();
                String payosDesc = payosResponse.path("desc").asText();
                String payosTransactionId = payosResponse.path("data").path("transactionId").asText("");

                // Update transaction with PayOS response
                for (Transaction txn : transactions) {
                    String txnNote = txn.getNote() != null ? txn.getNote() : "";
                    txnNote += " | payos_code:" + payosCode + " | payos_desc:" + payosDesc;
                    if (!payosTransactionId.isEmpty()) {
                        txnNote += " | payos_txn_id:" + payosTransactionId;
                    }
                    // ✅ FIX: Không lưu toàn bộ payosResponse (có thể rất dài)
                    // Chỉ lưu các field cần thiết để tránh vượt quá giới hạn
                    // payosResponse đã được log ở PayOSPayoutClient
                    txn.setNote(txnNote);
                    
                    // Update status based on PayOS response
                    if ("00".equals(payosCode) || "SUCCESS".equalsIgnoreCase(payosCode)) {
                        txn.setStatus(TransactionStatus.SUCCESS);
                    } else if ("PROCESSING".equalsIgnoreCase(payosCode) || "PENDING".equalsIgnoreCase(payosCode)) {
                        txn.setStatus(TransactionStatus.PROCESSING);
                    } else {
                        txn.setStatus(TransactionStatus.FAILED);
                        txnNote += " | error:" + payosDesc;
                        txn.setNote(txnNote);
                    }
                    transactionRepository.save(txn);
                }

                // Invalidate balance cache
                if (userTransaction.getWallet() != null) {
                    balanceCalculationService.invalidateBalanceCache(userTransaction.getWallet().getWalletId());
                }

                log.info("Admin {} processed AUTOMATIC payout {} via PayOS. Code: {}, TransactionId: {}",
                        processedBy, payoutRef, payosCode, payosTransactionId);

                // Get final status from user transaction
                TransactionStatus finalStatus = userTransaction.getStatus();
                
                // Send notification based on status
                if (userTransaction.getActorUser() != null) {
                    if (finalStatus == TransactionStatus.SUCCESS) {
                        payoutNotificationService.notifyPayoutSuccess(
                                userTransaction.getActorUser(),
                                payoutRef,
                                userTransaction.getAmount());
                    } else if (finalStatus == TransactionStatus.PROCESSING) {
                        payoutNotificationService.notifyPayoutProcessing(
                                userTransaction.getActorUser(),
                                payoutRef,
                                userTransaction.getAmount());
                    } else if (finalStatus == TransactionStatus.FAILED) {
                        payoutNotificationService.notifyPayoutFailed(
                                userTransaction.getActorUser(),
                                payoutRef,
                                userTransaction.getAmount(),
                                payosDesc);
                    }
                }
                
                return PayoutProcessResponse.builder()
                        .payoutRef(payoutRef)
                        .amount(userTransaction.getAmount())
                        .status(finalStatus.name())
                        .processedAt(processedAt)
                        .build();

            } catch (PayosClientException ex) {
                log.error("PayOS API error for payout {}: {}", payoutRef, ex.getMessage(), ex);
                
                // Mark as FAILED
                for (Transaction txn : transactions) {
                    txn.setStatus(TransactionStatus.FAILED);
                    String note = txn.getNote() != null ? txn.getNote() : "";
                    note += " | payos_error:" + ex.getMessage();
                    txn.setNote(note);
                    transactionRepository.save(txn);
                }

                throw new ValidationException("PayOS payout failed: " + ex.getMessage());
            }
        } else {
            // MANUAL mode: just mark as PROCESSING, admin will handle transfer manually
            log.info("Admin {} marked MANUAL payout {} as PROCESSING", processedBy, payoutRef);

            // Send processing notification
            if (userTransaction.getActorUser() != null) {
                payoutNotificationService.notifyPayoutProcessing(
                        userTransaction.getActorUser(),
                        payoutRef,
                        userTransaction.getAmount());
            }

            return PayoutProcessResponse.builder()
                    .payoutRef(payoutRef)
                    .amount(userTransaction.getAmount())
                    .status("PROCESSING")
                    .processedAt(processedAt)
                    .build();
        }
    }

    /**
     * Extract value from note by key pattern "key:value"
     */
    private String extractValueFromNote(String note, String key) {
        if (note == null || key == null) return null;
        int keyIndex = note.indexOf(key);
        if (keyIndex >= 0) {
            int valueStart = keyIndex + key.length();
            int valueEnd = note.indexOf(" |", valueStart);
            if (valueEnd < 0) {
                valueEnd = note.length();
            }
            return note.substring(valueStart, valueEnd).trim();
        }
        return null;
    }

    private String extractDescriptionFromNote(String note) {
        if (note == null) return null;
        // Extract description before first "|"
        int firstPipe = note.indexOf(" |");
        if (firstPipe > 0) {
            return note.substring(0, firstPipe).trim();
        }
        return note;
    }

    @Override
    @Transactional
    public PayoutProcessResponse completePayout(String payoutRef, MultipartFile evidenceFile, String notes, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        if (evidenceFile == null || evidenceFile.isEmpty()) {
            throw new ValidationException("Evidence file is required for payout completion");
        }

        // Find transactions
        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(payoutRef, TransactionStatus.PROCESSING);
        if (transactions.isEmpty()) {
            // Try PENDING status as fallback
            transactions = transactionRepository.findByPspRefAndStatus(payoutRef, TransactionStatus.PENDING);
            if (transactions.isEmpty()) {
                throw new NotFoundException("No processing or pending payout transactions found for pspRef: " + payoutRef);
            }
        }

        Transaction userTransaction = transactions.stream()
                .filter(txn -> txn.getActorKind() == ActorKind.USER && txn.getType() == TransactionType.PAYOUT)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch rút tiền của người dùng cho pspRef: " + payoutRef));

        // Check if payout mode is MANUAL (completePayout only for MANUAL)
        PayoutMode payoutMode = extractPayoutModeFromNote(userTransaction.getNote());
        if (payoutMode == PayoutMode.AUTOMATIC) {
            throw new InvalidPayoutStateException("completePayout is only available for MANUAL payouts. This payout is AUTOMATIC and handled by PayOS webhook.");
        }

        User user = userTransaction.getActorUser();
        if (user == null) {
            throw new NotFoundException("Không tìm thấy người dùng cho giao dịch rút tiền");
        }

        // ✅ SSOT: Get wallet từ userTransaction hoặc user
        Wallet wallet = userTransaction.getWallet();
        if (wallet == null) {
            // Fallback: Get wallet from user
            wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví cho người dùng: " + user.getUserId()));
        }

        // Upload evidence file
        String evidenceUrl;
        try {
            evidenceUrl = fileUploadService.uploadFile(evidenceFile).get();
        } catch (Exception e) {
            log.error("Failed to upload evidence file for payout {}: {}", payoutRef, e.getMessage());
            throw new ValidationException("Không thể tải lên file minh chứng: " + e.getMessage());
        }

        // Update all transactions to SUCCESS status and store evidence URL
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);
            // ✅ SSOT: Set wallet relationship nếu chưa có
            if (txn.getActorKind() == ActorKind.USER && txn.getWallet() == null) {
                txn.setWallet(wallet);
            }
            if (txn.getActorKind() == ActorKind.USER) {
                txn.setEvidenceUrl(evidenceUrl);
                if (notes != null && !notes.trim().isEmpty()) {
                    txn.setNote(txn.getNote() + " - " + notes);
                }
            }
            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table (status = SUCCESS)
        BigDecimal payoutAmount = userTransaction.getAmount();

        // ✅ SSOT: Tính balance từ ledger để log
        BigDecimal newPendingBalance = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());

        log.info("Admin {} completed payout {} with evidence URL: {}. New pending balance: {}",
                authentication.getName(), payoutRef, evidenceUrl, newPendingBalance);

        // Send success notification
        if (user != null) {
            payoutNotificationService.notifyPayoutSuccess(user, payoutRef, payoutAmount);
        }

        return PayoutProcessResponse.builder()
                .payoutRef(payoutRef)
                .amount(payoutAmount)
                .status("SUCCESS")
                .evidenceUrl(evidenceUrl)
                .notes(notes)
                .processedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public PayoutProcessResponse failPayout(String payoutRef, String reason, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Lý do thất bại là bắt buộc");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(payoutRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            // Try PROCESSING status as fallback
            transactions = transactionRepository.findByPspRefAndStatus(payoutRef, TransactionStatus.PROCESSING);
            if (transactions.isEmpty()) {
                throw new NotFoundException("No pending or processing payout transactions found for pspRef: " + payoutRef);
            }
        }

        Transaction userTransaction = transactions.stream()
                .filter(txn -> txn.getActorKind() == ActorKind.USER && txn.getType() == TransactionType.PAYOUT)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch rút tiền của người dùng cho pspRef: " + payoutRef));

        User user = userTransaction.getActorUser();
        if (user == null) {
            throw new NotFoundException("Không tìm thấy người dùng cho giao dịch rút tiền");
        }

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + user.getUserId()));

        // Update all transactions to FAILED status
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);
            transactionRepository.save(txn);
        }

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Transaction status = FAILED nên sẽ không được tính vào balance
        // Nếu cần refund, tạo REFUND transaction thay vì update balance trực tiếp
        BigDecimal payoutAmount = userTransaction.getAmount();
        
        // Get wallet for refund transaction
        Wallet refundWallet = walletRepository.findByUser_UserId(user.getUserId())
            .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + user.getUserId()));
        
        UUID refundGroupId = UUID.randomUUID();
        Transaction refundTxn = Transaction.builder()
            .groupId(refundGroupId)
            .wallet(refundWallet)
            .type(TransactionType.REFUND)
            .direction(TransactionDirection.IN)
            .actorKind(ActorKind.SYSTEM)
            .actorUser(user)
            .amount(payoutAmount)
            .currency("VND")
            .status(TransactionStatus.SUCCESS)
            .note("Refund for failed payout: " + payoutRef)
            .build();
        transactionRepository.save(refundTxn);

        // ✅ SSOT: Tính balance từ ledger để log
        BigDecimal newAvailableBalance = balanceCalculationService.calculateAvailableBalance(refundWallet.getWalletId());
        BigDecimal newPendingBalance = balanceCalculationService.calculatePendingBalance(refundWallet.getWalletId());

        log.info("Admin {} failed payout {} with reason: {}. Refund transaction created. New balance: available={}, pending={}",
                authentication.getName(), payoutRef, reason, newAvailableBalance, newPendingBalance);

        // Send failed notification
        if (user != null) {
            payoutNotificationService.notifyPayoutFailed(user, payoutRef, payoutAmount, reason);
        }

        return PayoutProcessResponse.builder()
                .payoutRef(payoutRef)
                .amount(payoutAmount)
                .status("FAILED")
                .notes("Failed: " + reason)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private String extractBankNameFromNote(String note) {
        if (note == null || note.isEmpty()) {
            return "";
        }
        // Format: "Payout to {bank} - {masked} ({holder})"
        if (note.startsWith("Payout to ")) {
            int dashIndex = note.indexOf(" - ");
            if (dashIndex > 0) {
                return note.substring(10, dashIndex).trim();
            }
        }
        return "";
    }

    private String extractMaskedAccountFromNote(String note) {
        if (note == null || note.isEmpty()) {
            return "";
        }
        // Format: "Payout to {bank} - {masked} ({holder})"
        int dashIndex = note.indexOf(" - ");
        int parenIndex = note.indexOf(" (");
        if (dashIndex > 0 && parenIndex > dashIndex) {
            return note.substring(dashIndex + 3, parenIndex).trim();
        }
        return "";
    }

    private String extractAccountHolderFromNote(String note) {
        if (note == null || note.isEmpty()) {
            return "";
        }
        // Format: "Payout to {bank} - {masked} ({holder})"
        int parenIndex = note.indexOf(" (");
        int closeParen = note.indexOf(")");
        if (parenIndex > 0 && closeParen > parenIndex) {
            return note.substring(parenIndex + 2, closeParen).trim();
        }
        return "";
    }

    // ========== SSOT Methods ==========
    
    @Override
    @Transactional
    public Transaction createTopUpTransaction(
            Integer userId,
            BigDecimal amount,
            String pspRef,
            String idempotencyKey,
            TransactionStatus status) {
        
        // 1. Check idempotency
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate topup request with idempotency_key: {}", idempotencyKey);
                return existing.get();
            }
        }
        
        // 2. Get wallet
        Wallet wallet = walletRepository.findByUser_UserId(userId)
            .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
        
        // 3. Create transaction (SSOT)
        UUID groupId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
            .groupId(groupId)
            .wallet(wallet)
            .type(TransactionType.TOPUP)
            .direction(TransactionDirection.IN)
            .actorKind(ActorKind.USER)
            .actorUser(wallet.getUser())
            .amount(amount)
            .currency("VND")
            .status(status)
            .idempotencyKey(idempotencyKey)
            .pspRef(pspRef)
            .note("Wallet top-up")
            .build();
        
        transactionRepository.save(transaction);
        
        // ✅ KHÔNG update wallet.balance trực tiếp
        // Balance được tính từ transactions table
        
        log.info("Top-up transaction created: txnId={}, amount={}, status={}, walletId={}",
            transaction.getTxnId(), amount, status, wallet.getWalletId());
        
        return transaction;
    }
    
    @Override
    @Transactional
    public void completeTopUpTransaction(Integer txnId) {
        Transaction transaction = transactionRepository.findById(txnId)
            .orElseThrow(() -> new NotFoundException("Transaction not found: " + txnId));
        
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new ValidationException(
                "Transaction is not in PENDING status. Current status: " + transaction.getStatus());
        }
        
        if (transaction.getType() != TransactionType.TOPUP) {
            throw new ValidationException("Transaction is not a TOPUP type");
        }
        
        // Update status to SUCCESS
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);
        
        log.info("Top-up transaction completed: txnId={}", txnId);
        
        // ✅ Balance tự động được tính lại từ ledger khi query
    }
    
    @Override
    @Transactional
    public void failTopUpTransaction(Integer txnId, String reason) {
        Transaction transaction = transactionRepository.findById(txnId)
            .orElseThrow(() -> new NotFoundException("Transaction not found: " + txnId));
        
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new ValidationException(
                "Transaction is not in PENDING status. Current status: " + transaction.getStatus());
        }
        
        // Update status to FAILED
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setNote(transaction.getNote() + " - Failed: " + reason);
        transactionRepository.save(transaction);
        
        log.info("Top-up transaction failed: txnId={}, reason={}", txnId, reason);
        
        // ✅ Balance không thay đổi vì transaction status = FAILED
        // (chỉ tính SUCCESS transactions)
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findTransactionByIdempotencyKey(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }
    
    @Override
    @Transactional
    public Transaction holdAmount(Integer walletId, BigDecimal amount, UUID groupId, String reason, Integer sharedRideRequestId) {
        // ✅ FIX P0-CONCURRENCY: Get wallet với pessimistic lock để tránh race condition
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
            .orElseThrow(() -> new NotFoundException("Wallet not found: " + walletId));
        
        // ✅ FIX P0-CONCURRENCY: Check balance sau khi lock để đảm bảo consistency
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(walletId);
        if (availableBalance.compareTo(amount) < 0) {
            throw new ValidationException(
                String.format("Insufficient balance. Available: %s, Required: %s",
                    availableBalance, amount));
        }
        
        // ✅ FIX: Load SharedRideRequest để set vào transaction (required by database constraint)
        com.mssus.app.entity.SharedRideRequest sharedRideRequest = null;
        if (sharedRideRequestId != null) {
            sharedRideRequest = sharedRideRequestRepository.findById(sharedRideRequestId)
                .orElseThrow(() -> new NotFoundException("SharedRideRequest not found: " + sharedRideRequestId));
        }
        
        // ✅ FIX P1-5: Generate idempotency key và check duplicate
        String idempotencyKey = "HOLD_" + groupId.toString();
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate hold request with idempotency_key: {}", idempotencyKey);
            return existing.get(); // Idempotent
        }
        
        // 3. Create hold transaction
        Transaction holdTransaction = Transaction.builder()
            .groupId(groupId)
            .wallet(wallet)
            .type(TransactionType.HOLD_CREATE)
            .direction(TransactionDirection.INTERNAL)
            .actorKind(ActorKind.USER)
            .actorUser(wallet.getUser())
            .amount(amount)
            .currency("VND")
            .status(TransactionStatus.SUCCESS)
            .idempotencyKey(idempotencyKey)  // ✅ FIX P1-5: Thêm idempotency key
            .sharedRideRequest(sharedRideRequest)  // ✅ FIX: Required for database constraint txn_booking_required_for_ride
            .note("Hold: " + reason)
            .build();
        
        transactionRepository.save(holdTransaction);
        
        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi tạo hold
        balanceCalculationService.invalidateBalanceCache(walletId);
        
        log.info("Hold transaction created: txnId={}, amount={}, walletId={}",
            holdTransaction.getTxnId(), amount, walletId);
        
        return holdTransaction;
    }
    
    @Override
    @Transactional
    public Transaction releaseHold(UUID groupId, String reason) {
        // Find original hold transaction
        Transaction holdTxn = transactionRepository
            .findByGroupIdAndType(groupId, TransactionType.HOLD_CREATE)
            .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));
        
        // ✅ FIX P1-7: Check if already released (idempotency)
        Optional<Transaction> existingRelease = transactionRepository
            .findByGroupIdAndType(groupId, TransactionType.HOLD_RELEASE);
        if (existingRelease.isPresent()) {
            log.warn("Hold already released for groupId: {}", groupId);
            return existingRelease.get(); // Idempotent
        }
        
        // Create release transaction
        Transaction releaseTxn = Transaction.builder()
            .groupId(groupId)
            .wallet(holdTxn.getWallet())
            .type(TransactionType.HOLD_RELEASE)
            .direction(TransactionDirection.INTERNAL)
            .actorKind(ActorKind.USER)
            .actorUser(holdTxn.getActorUser())
            .amount(holdTxn.getAmount())
            .currency("VND")
            .status(TransactionStatus.SUCCESS)
            .note("Release hold: " + reason)
            .build();
        
    transactionRepository.save(releaseTxn);
    
    // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi release hold
    if (holdTxn.getWallet() != null) {
        balanceCalculationService.invalidateBalanceCache(holdTxn.getWallet().getWalletId());
    }
    
    log.info("Hold released: groupId={}, amount={}", groupId, holdTxn.getAmount());
    
    return releaseTxn;
    }
}

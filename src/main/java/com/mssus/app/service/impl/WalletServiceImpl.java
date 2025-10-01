package com.mssus.app.service.impl;

import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.wallet.PayoutInitRequest;
import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.wallet.DriverEarningsResponse;
import com.mssus.app.dto.response.wallet.PayoutInitResponse;
import com.mssus.app.dto.response.wallet.TopUpInitResponse;
import com.mssus.app.dto.response.wallet.WalletResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.PayOSService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PayOSService payOSService;

    @Override
    public void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        wallet.setShadowBalance(wallet.getShadowBalance().add(amount));
        wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
        walletRepository.save(wallet);
    }

    @Override
    public void increasePendingBalance(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        walletRepository.save(wallet);
    }

    @Override
    public void decreasePendingBalance(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
        walletRepository.save(wallet);
    }

    @Override
    public void transferPendingToAvailable(Integer userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
        BigDecimal shadowBalance = BigDecimal.ZERO;
        if (wallet.getShadowBalance() != null){
            shadowBalance = wallet.getShadowBalance();
        }
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
        wallet.setShadowBalance(shadowBalance.add(amount));
        wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
        walletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getBalance(Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + user.getUserId()));

        return WalletResponse.builder()
                .userId(user.getUserId())
                .availableBalance(wallet.getShadowBalance())
                .pendingBalance(wallet.getPendingBalance())
                .totalToppedUp(wallet.getTotalToppedUp())
                .totalSpent(wallet.getTotalSpent())
                .isActive(wallet.getIsActive())
                .lastSyncedAt(wallet.getLastSyncedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(Integer userId) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }

        return walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
    }

    @Override
    @Transactional
    public TopUpInitResponse initiateTopUp(TopUpInitRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        // Ensure wallet exists
        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElse(null);
        if (wallet == null) {
            wallet = createWalletForUser(user.getUserId());
        }

        // Validate wallet is active
        if (!wallet.getIsActive()) {
            throw new ValidationException("Wallet is frozen. Please contact support.");
        }

        try {
            // Create payment link with PayOS
            String description = request.getReturnUrl() != null ?
                    "Top-up wallet - " + user.getFullName() :
                    "Wallet Top-up";
            // transaction dc cap nhat qua payosservice
            CheckoutResponseData paymentData = payOSService.createTopUpPaymentLink(
                    user.getUserId(),
                    request.getAmount(),
                    description
            );

            log.info("Top-up initiated for user {} - amount: {}, orderCode: {}",
                    user.getUserId(), request.getAmount(), paymentData.getOrderCode());

            return TopUpInitResponse.builder()
                    .transactionRef(String.valueOf(paymentData.getOrderCode()))
                    .paymentUrl(paymentData.getCheckoutUrl())
                    .qrCodeUrl(paymentData.getQrCode())
                    .status("PENDING")
                    .expirySeconds(900) // 15 minutes
                    .build();

        } catch (Exception e) {
            log.error("Error initiating top-up for user {}: {}", user.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to initiate top-up: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PayoutInitResponse initiatePayout(PayoutInitRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + user.getUserId()));

        // Validate wallet is active
        if (!wallet.getIsActive()) {
            throw new ValidationException("Wallet is frozen. Please contact support.");
        }

        // Check sufficient balance
        if (wallet.getShadowBalance().compareTo(request.getAmount()) < 0) {
            throw new ValidationException("Insufficient balance. Available: " +
                    wallet.getShadowBalance() + ", Required: " + request.getAmount());
        }

        // Generate payout reference
        String payoutRef = "PAYOUT-" + System.currentTimeMillis();

        // TODO: Integrate with actual bank payout service
        log.warn("Bank payout integration not implemented. Creating mock payout for user {} - amount: {}",
                user.getUserId(), request.getAmount());

        // Mask account number (show only last 4 digits)
        String maskedAccount = "****" + request.getBankAccountNumber()
                .substring(Math.max(0, request.getBankAccountNumber().length() - 4));

        return PayoutInitResponse.builder()
                .payoutRef(payoutRef)
                .amount(request.getAmount())
                .status("PROCESSING")
                .estimatedCompletionTime(LocalDateTime.now().plusHours(24).toString())
                .maskedAccountNumber(maskedAccount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DriverEarningsResponse getDriverEarnings(Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Authentication cannot be null");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for driver: " + user.getUserId()));

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
                .map(Transaction::getBookingId)
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

        return DriverEarningsResponse.builder()
                .availableBalance(wallet.getShadowBalance())
                .pendingEarnings(wallet.getPendingBalance())
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
            throw new ValidationException("User ID cannot be null");
        }

        // Check if wallet already exists
        if (walletRepository.existsByUserId(userId)) {
            throw new ValidationException("Wallet already exists for user: " + userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        Wallet wallet = Wallet.builder()
                .user(user)
                .shadowBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
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
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Amount must be non-negative");
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));

        boolean hasFunds = wallet.getShadowBalance().compareTo(amount) >= 0;

        log.debug("Balance check for user {} - required: {}, available: {}, sufficient: {}",
                userId, amount, wallet.getShadowBalance(), hasFunds);

        return hasFunds;
    }
}

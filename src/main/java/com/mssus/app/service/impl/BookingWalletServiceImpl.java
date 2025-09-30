package com.mssus.app.service.impl;

import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.wallet.*;
import com.mssus.app.dto.response.wallet.BalanceCheckResponse;
import com.mssus.app.dto.response.wallet.WalletOperationResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BookingWalletService;
import com.mssus.app.service.TransactionService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of BookingWalletService.
 * This service acts as a wrapper around TransactionService for booking-related operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingWalletServiceImpl implements BookingWalletService {

    private final TransactionService transactionService;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public WalletOperationResponse holdFunds(WalletHoldRequest request) {
        log.info("Hold funds request - userId: {}, bookingId: {}, amount: {}",
                request.getUserId(), request.getBookingId(), request.getAmount());

        // Validate request
        validateHoldRequest(request);

        // Check sufficient balance
        if (!walletService.hasSufficientBalance(request.getUserId(), request.getAmount())) {
            Wallet wallet = walletService.getWalletByUserId(request.getUserId());
            throw new ValidationException("Insufficient balance. Available: " +
                    wallet.getShadowBalance() + ", Required: " + request.getAmount());
        }

        try {
            // Create hold transaction using TransactionService
            List<Transaction> transactions = transactionService.createHold(
                    request.getUserId(),
                    request.getAmount(),
                    request.getBookingId(),
                    request.getNote() != null ? request.getNote() : "Hold for booking #" + request.getBookingId()
            );

            Transaction holdTransaction = transactions.get(0);

            // Get updated wallet balance
            Wallet wallet = walletService.getWalletByUserId(request.getUserId());

            log.info("Successfully held funds - txnId: {}, userId: {}, amount: {}",
                    holdTransaction.getTxnId(), request.getUserId(), request.getAmount());

            return WalletOperationResponse.builder()
                    .success(true)
                    .transactionId(holdTransaction.getTxnId())
                    .message("Funds held successfully for booking #" + request.getBookingId())
                    .newAvailableBalance(wallet.getShadowBalance())
                    .newPendingBalance(wallet.getPendingBalance())
                    .build();

        } catch (Exception e) {
            log.error("Error holding funds for userId: {}, bookingId: {}",
                    request.getUserId(), request.getBookingId(), e);
            throw new RuntimeException("Failed to hold funds: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public WalletOperationResponse captureFunds(WalletCaptureRequest request) {
        log.info("Capture funds request - userId: {}, bookingId: {}, amount: {}, driverId: {}",
                request.getUserId(), request.getBookingId(), request.getAmount(), request.getDriverId());

        // Validate request
        validateCaptureRequest(request);

        try {
            // Find the hold transaction by bookingId
            UUID groupId = findGroupIdByBookingId(request.getBookingId(), request.getUserId());

            // Calculate commission (e.g., 20% platform fee)
            BigDecimal commissionRate = new BigDecimal("0.20");

            // Capture fare using TransactionService
            List<Transaction> transactions = transactionService.captureFare(
                    groupId,
                    request.getUserId(),
                    request.getDriverId(),
                    request.getAmount(),
                    commissionRate,
                    request.getNote() != null ? request.getNote() : "Capture for booking #" + request.getBookingId()
            );

            // Get the rider charge transaction (first one)
            Transaction captureTransaction = transactions.stream()
                    .filter(t -> t.getRiderUser() != null)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Capture transaction not found"));

            // Get updated wallet balance
            Wallet wallet = walletService.getWalletByUserId(request.getUserId());

            log.info("Successfully captured funds - txnId: {}, bookingId: {}, amount: {}",
                    captureTransaction.getTxnId(), request.getBookingId(), request.getAmount());

            return WalletOperationResponse.builder()
                    .success(true)
                    .transactionId(captureTransaction.getTxnId())
                    .message("Funds captured successfully for booking #" + request.getBookingId())
                    .newAvailableBalance(wallet.getShadowBalance())
                    .newPendingBalance(wallet.getPendingBalance())
                    .build();

        } catch (Exception e) {
            log.error("Error capturing funds for bookingId: {}", request.getBookingId(), e);
            throw new RuntimeException("Failed to capture funds: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public WalletOperationResponse releaseFunds(WalletReleaseRequest request) {
        log.info("Release funds request - userId: {}, bookingId: {}, amount: {}",
                request.getUserId(), request.getBookingId(), request.getAmount());

        // Validate request
        validateReleaseRequest(request);

        try {
            // Find the hold transaction by bookingId
            UUID groupId = findGroupIdByBookingId(request.getBookingId(), request.getUserId());

            // Release hold using TransactionService
            List<Transaction> transactions = transactionService.releaseHold(
                    groupId,
                    request.getNote() != null ? request.getNote() : "Release hold for booking #" + request.getBookingId()
            );

            Transaction releaseTransaction = transactions.get(0);

            // Get updated wallet balance
            Wallet wallet = walletService.getWalletByUserId(request.getUserId());

            log.info("Successfully released funds - txnId: {}, bookingId: {}, amount: {}",
                    releaseTransaction.getTxnId(), request.getBookingId(), request.getAmount());

            return WalletOperationResponse.builder()
                    .success(true)
                    .transactionId(releaseTransaction.getTxnId())
                    .message("Funds released successfully for booking #" + request.getBookingId())
                    .newAvailableBalance(wallet.getShadowBalance())
                    .newPendingBalance(wallet.getPendingBalance())
                    .build();

        } catch (Exception e) {
            log.error("Error releasing funds for bookingId: {}", request.getBookingId(), e);
            throw new RuntimeException("Failed to release funds: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public WalletOperationResponse refundToUser(WalletRefundRequest request) {
        log.info("Refund request - userId: {}, bookingId: {}, amount: {}, reason: {}",
                request.getUserId(), request.getBookingId(), request.getAmount(), request.getReason());

        // Validate request
        validateRefundRequest(request);

        try {
            // Find the original capture transaction by bookingId
            UUID originalGroupId = findGroupIdByBookingId(request.getBookingId(), request.getUserId());

            // Get driver ID from the original transaction
            Integer driverId = findDriverIdFromGroupId(originalGroupId);

            // Process refund using TransactionService
            List<Transaction> transactions = transactionService.refundRide(
                    originalGroupId,
                    request.getUserId(),
                    driverId,
                    request.getAmount(),
                    request.getReason() != null ? request.getReason() : "Refund for booking #" + request.getBookingId()
            );

            // Get the rider credit transaction
            Transaction refundTransaction = transactions.stream()
                    .filter(t -> t.getRiderUser() != null)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Refund transaction not found"));

            // Get updated wallet balance
            Wallet wallet = walletService.getWalletByUserId(request.getUserId());

            log.info("Successfully refunded user - txnId: {}, bookingId: {}, amount: {}",
                    refundTransaction.getTxnId(), request.getBookingId(), request.getAmount());

            return WalletOperationResponse.builder()
                    .success(true)
                    .transactionId(refundTransaction.getTxnId())
                    .message("Refund processed successfully for booking #" + request.getBookingId())
                    .newAvailableBalance(wallet.getShadowBalance())
                    .newPendingBalance(wallet.getPendingBalance())
                    .build();

        } catch (Exception e) {
            log.error("Error processing refund for bookingId: {}", request.getBookingId(), e);
            throw new RuntimeException("Failed to process refund: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceCheckResponse checkBalance(Integer userId, BigDecimal amount) {
        log.debug("Balance check request - userId: {}, amount: {}", userId, amount);

        // Validate input
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Amount must be non-negative");
        }

        try {
            // Get wallet
            Wallet wallet = walletService.getWalletByUserId(userId);

            // Check sufficient balance
            boolean hasSufficientFunds = wallet.getShadowBalance().compareTo(amount) >= 0;

            log.debug("Balance check result - userId: {}, required: {}, available: {}, sufficient: {}",
                    userId, amount, wallet.getShadowBalance(), hasSufficientFunds);

            return BalanceCheckResponse.builder()
                    .userId(userId)
                    .availableBalance(wallet.getShadowBalance())
                    .pendingBalance(wallet.getPendingBalance())
                    .hasSufficientFunds(hasSufficientFunds)
                    .requestedAmount(amount)
                    .isActive(wallet.getIsActive())
                    .build();

        } catch (Exception e) {
            log.error("Error checking balance for userId: {}", userId, e);
            throw new RuntimeException("Failed to check balance: " + e.getMessage(), e);
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validateHoldRequest(WalletHoldRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (request.getBookingId() == null) {
            throw new ValidationException("Booking ID cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
    }

    private void validateCaptureRequest(WalletCaptureRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (request.getBookingId() == null) {
            throw new ValidationException("Booking ID cannot be null");
        }
        if (request.getDriverId() == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
    }

    private void validateReleaseRequest(WalletReleaseRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (request.getBookingId() == null) {
            throw new ValidationException("Booking ID cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
    }

    private void validateRefundRequest(WalletRefundRequest request) {
        if (request.getUserId() == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (request.getBookingId() == null) {
            throw new ValidationException("Booking ID cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
    }

    /**
     * Find the group ID of a hold transaction by booking ID and user ID
     */
    private UUID findGroupIdByBookingId(Long bookingId, Integer userId) {
        // Query transactions by booking ID
        List<Transaction> transactions = transactionRepository.findByGroupId(null);

        // Find transaction with matching bookingId
        Transaction transaction = transactionRepository.findAll().stream()
                .filter(t -> t.getBookingId() != null && t.getBookingId().equals(bookingId))
                .filter(t -> t.getRiderUser() != null && t.getRiderUser().getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Transaction not found for bookingId: " + bookingId));

        if (transaction.getGroupId() == null) {
            throw new NotFoundException("Group ID not found for bookingId: " + bookingId);
        }

        return transaction.getGroupId();
    }

    /**
     * Find driver ID from a group of transactions
     */
    private Integer findDriverIdFromGroupId(UUID groupId) {
        List<Transaction> transactions = transactionService.getTransactionsByGroupId(groupId);

        return transactions.stream()
                .filter(t -> t.getDriverUser() != null)
                .map(t -> t.getDriverUser().getUserId())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Driver not found in transaction group: " + groupId));
    }
}

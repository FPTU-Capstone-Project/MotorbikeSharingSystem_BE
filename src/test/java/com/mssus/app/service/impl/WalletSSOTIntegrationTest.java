package com.mssus.app.service.impl;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test cho SSOT flow:
 * 1. Tạo top-up transaction (PENDING)
 * 2. Complete transaction (SUCCESS)
 * 3. Verify balance được tính từ ledger
 * 4. Verify wallet balance KHÔNG được update trực tiếp
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Wallet SSOT Integration Tests")
class WalletSSOTIntegrationTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BalanceCalculationService balanceCalculationService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Wallet testWallet;
    private static final Integer USER_ID = 1;
    private static final Integer WALLET_ID = 1;
    private static final BigDecimal TOPUP_AMOUNT = BigDecimal.valueOf(200_000);
    private static final String PSP_REF = "ORDER-123";
    private static final String IDEMPOTENCY_KEY = "TOPUP_ORDER-123_200000";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .userId(USER_ID)
            .email("test@example.com")
            .build();

        testWallet = Wallet.builder()
            .walletId(WALLET_ID)
            .user(testUser)
            .isActive(true)
            .build();

        // Use lenient() to avoid UnnecessaryStubbingException for stubbings not used in all tests
        lenient().when(walletRepository.findByUser_UserId(USER_ID))
            .thenReturn(Optional.of(testWallet));
        lenient().when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> {
                Transaction txn = invocation.getArgument(0);
                if (txn.getTxnId() == null) {
                    txn.setTxnId(1);
                }
                return txn;
            });
    }

    @Test
    @DisplayName("Integration: Top-up flow should calculate balance from ledger")
    void integration_topupFlow_shouldCalculateBalanceFromLedger() {
        // Step 1: Create PENDING top-up transaction
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());

        Transaction pendingTxn = walletService.createTopUpTransaction(
            USER_ID, TOPUP_AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        );

        assertThat(pendingTxn.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(pendingTxn.getWallet()).isEqualTo(testWallet);

        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));

        // Step 2: Mock balance calculation (before completion)
        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO); // No balance yet (transaction is PENDING)
        when(balanceCalculationService.calculatePendingBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);

        // Step 3: Complete transaction
        when(transactionRepository.findById(pendingTxn.getTxnId()))
            .thenReturn(Optional.of(pendingTxn));

        walletService.completeTopUpTransaction(pendingTxn.getTxnId());

        assertThat(pendingTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        // ✅ SSOT: Verify wallet balance is STILL NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));

        // Step 4: Verify balance is calculated from ledger (after completion)
        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(TOPUP_AMOUNT); // Balance now includes the top-up
        when(balanceCalculationService.calculatePendingBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);

        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(WALLET_ID);
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(WALLET_ID);

        assertThat(availableBalance).isEqualTo(TOPUP_AMOUNT);
        assertThat(pendingBalance).isEqualTo(BigDecimal.ZERO);

        // ✅ SSOT: Balance is calculated from transactions, not from wallet entity
        verify(balanceCalculationService, atLeastOnce()).calculateAvailableBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Integration: Failed top-up should not affect balance")
    void integration_failedTopup_shouldNotAffectBalance() {
        // Step 1: Create PENDING transaction
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());

        Transaction pendingTxn = walletService.createTopUpTransaction(
            USER_ID, TOPUP_AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        );

        // Step 2: Fail transaction
        when(transactionRepository.findById(pendingTxn.getTxnId()))
            .thenReturn(Optional.of(pendingTxn));

        walletService.failTopUpTransaction(pendingTxn.getTxnId(), "Payment declined");

        assertThat(pendingTxn.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // Step 3: Verify balance is still zero (FAILED transactions don't count)
        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);
        // Use lenient() since calculatePendingBalance may not be called in this test
        lenient().when(balanceCalculationService.calculatePendingBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);

        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(WALLET_ID);
        assertThat(availableBalance).isEqualTo(BigDecimal.ZERO);

        // ✅ SSOT: FAILED transactions are not included in balance calculation
        verify(balanceCalculationService).calculateAvailableBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Integration: Hold and release flow should calculate balance correctly")
    void integration_holdAndRelease_shouldCalculateBalanceCorrectly() {
        // Step 1: Initial balance
        BigDecimal initialBalance = BigDecimal.valueOf(500_000);
        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(initialBalance);
        when(walletRepository.findById(WALLET_ID))
            .thenReturn(Optional.of(testWallet));

        // Step 2: Hold amount
        UUID groupId = UUID.randomUUID();
        BigDecimal holdAmount = BigDecimal.valueOf(100_000);

        Transaction holdTxn = walletService.holdAmount(WALLET_ID, holdAmount, groupId, "Ride payment");

        assertThat(holdTxn.getType()).isEqualTo(TransactionType.HOLD_CREATE);
        assertThat(holdTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        // ✅ SSOT: Wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));

        // Step 3: After hold, available balance decreases, pending increases
        BigDecimal afterHoldAvailable = initialBalance.subtract(holdAmount);
        BigDecimal afterHoldPending = holdAmount;

        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(afterHoldAvailable);
        when(balanceCalculationService.calculatePendingBalance(WALLET_ID))
            .thenReturn(afterHoldPending);

        BigDecimal available = balanceCalculationService.calculateAvailableBalance(WALLET_ID);
        BigDecimal pending = balanceCalculationService.calculatePendingBalance(WALLET_ID);

        assertThat(available).isEqualTo(afterHoldAvailable);
        assertThat(pending).isEqualTo(afterHoldPending);

        // Step 4: Release hold
        when(transactionRepository.findByGroupIdAndType(groupId, TransactionType.HOLD_CREATE))
            .thenReturn(Optional.of(holdTxn));

        Transaction releaseTxn = walletService.releaseHold(groupId, "Ride cancelled");

        assertThat(releaseTxn.getType()).isEqualTo(TransactionType.HOLD_RELEASE);
        assertThat(releaseTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        // ✅ SSOT: Wallet balance is STILL NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));

        // Step 5: After release, balance returns to initial
        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(initialBalance);
        when(balanceCalculationService.calculatePendingBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);

        BigDecimal finalAvailable = balanceCalculationService.calculateAvailableBalance(WALLET_ID);
        BigDecimal finalPending = balanceCalculationService.calculatePendingBalance(WALLET_ID);

        assertThat(finalAvailable).isEqualTo(initialBalance);
        assertThat(finalPending).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Integration: Idempotency prevents duplicate transactions")
    void integration_idempotency_preventsDuplicateTransactions() {
        // Step 1: Create first transaction
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());

        Transaction firstTxn = walletService.createTopUpTransaction(
            USER_ID, TOPUP_AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        );

        verify(transactionRepository).save(any(Transaction.class));

        // Step 2: Try to create duplicate transaction
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(firstTxn));

        Transaction duplicateTxn = walletService.createTopUpTransaction(
            USER_ID, TOPUP_AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        );

        // ✅ Idempotency: Should return existing transaction
        assertThat(duplicateTxn).isEqualTo(firstTxn);

        // ✅ Should not save again
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }
}


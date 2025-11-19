package com.mssus.app.service.impl;

import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService SSOT Methods Tests")
class WalletServiceSSOTTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private com.mssus.app.repository.SharedRideRequestRepository sharedRideRequestRepository;

    @Mock
    private BalanceCalculationService balanceCalculationService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Wallet testWallet;
    private static final Integer USER_ID = 1;
    private static final Integer WALLET_ID = 1;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(200_000);
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
        lenient().when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(testUser));
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
    @DisplayName("Should create top-up transaction with PENDING status")
    void should_createTopUpTransaction_withPendingStatus() {
        // Given
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());

        // When
        Transaction result = walletService.createTopUpTransaction(
            USER_ID, AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(result.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getPspRef()).isEqualTo(PSP_REF);
        assertThat(result.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(result.getWallet()).isEqualTo(testWallet);
        assertThat(result.getActorUser()).isEqualTo(testUser);

        verify(transactionRepository).save(any(Transaction.class));
        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should return existing transaction when idempotency key exists")
    void should_returnExistingTransaction_whenIdempotencyKeyExists() {
        // Given
        Transaction existingTxn = Transaction.builder()
            .txnId(1)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .type(TransactionType.TOPUP)
            .status(TransactionStatus.PENDING)
            .build();

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existingTxn));

        // When
        Transaction result = walletService.createTopUpTransaction(
            USER_ID, AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        );

        // Then
        assertThat(result).isEqualTo(existingTxn);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should complete top-up transaction (PENDING -> SUCCESS)")
    void should_completeTopUpTransaction() {
        // Given
        Transaction pendingTxn = Transaction.builder()
            .txnId(1)
            .type(TransactionType.TOPUP)
            .status(TransactionStatus.PENDING)
            .amount(AMOUNT)
            .build();

        when(transactionRepository.findById(1))
            .thenReturn(Optional.of(pendingTxn));

        // When
        walletService.completeTopUpTransaction(1);

        // Then
        assertThat(pendingTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        verify(transactionRepository).save(pendingTxn);
        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should throw exception when completing non-PENDING transaction")
    void should_throwException_whenCompletingNonPendingTransaction() {
        // Given
        Transaction successTxn = Transaction.builder()
            .txnId(1)
            .type(TransactionType.TOPUP)
            .status(TransactionStatus.SUCCESS)
            .build();

        when(transactionRepository.findById(1))
            .thenReturn(Optional.of(successTxn));

        // When/Then
        assertThatThrownBy(() -> walletService.completeTopUpTransaction(1))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("PENDING status");
    }

    @Test
    @DisplayName("Should fail top-up transaction (PENDING -> FAILED)")
    void should_failTopUpTransaction() {
        // Given
        Transaction pendingTxn = Transaction.builder()
            .txnId(1)
            .type(TransactionType.TOPUP)
            .status(TransactionStatus.PENDING)
            .note("Original note")
            .build();

        when(transactionRepository.findById(1))
            .thenReturn(Optional.of(pendingTxn));

        // When
        walletService.failTopUpTransaction(1, "Payment declined");

        // Then
        assertThat(pendingTxn.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(pendingTxn.getNote()).contains("Failed: Payment declined");
        verify(transactionRepository).save(pendingTxn);
        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should hold amount and create HOLD_CREATE transaction")
    void should_holdAmount_andCreateHoldTransaction() {
        // Given
        UUID groupId = UUID.randomUUID();
        BigDecimal availableBalance = BigDecimal.valueOf(500_000);

        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(availableBalance);
        when(walletRepository.findByIdWithLock(WALLET_ID))
            .thenReturn(Optional.of(testWallet));
        // Mock SharedRideRequestRepository (not used in this test, but required by implementation)
        lenient().when(sharedRideRequestRepository.findById(any()))
            .thenReturn(Optional.empty());

        // When
        Transaction result = walletService.holdAmount(WALLET_ID, AMOUNT, groupId, "Ride payment", null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.HOLD_CREATE);
        assertThat(result.getDirection()).isEqualTo(TransactionDirection.INTERNAL);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getWallet()).isEqualTo(testWallet);

        verify(transactionRepository).save(any(Transaction.class));
        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should throw exception when holding amount exceeds available balance")
    void should_throwException_whenHoldingAmountExceedsBalance() {
        // Given
        UUID groupId = UUID.randomUUID();
        BigDecimal insufficientBalance = BigDecimal.valueOf(100_000);

        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(insufficientBalance);
        when(walletRepository.findByIdWithLock(WALLET_ID))
            .thenReturn(Optional.of(testWallet));
        // Mock SharedRideRequestRepository (not used in this test, but required by implementation)
        lenient().when(sharedRideRequestRepository.findById(any()))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> walletService.holdAmount(WALLET_ID, AMOUNT, groupId, "Ride payment", null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("Should release hold and create HOLD_RELEASE transaction")
    void should_releaseHold_andCreateReleaseTransaction() {
        // Given
        UUID groupId = UUID.randomUUID();
        Transaction holdTxn = Transaction.builder()
            .txnId(1)
            .groupId(groupId)
            .type(TransactionType.HOLD_CREATE)
            .wallet(testWallet)
            .actorUser(testUser)
            .amount(AMOUNT)
            .build();

        when(transactionRepository.findByGroupIdAndType(groupId, TransactionType.HOLD_CREATE))
            .thenReturn(Optional.of(holdTxn));

        // When
        Transaction result = walletService.releaseHold(groupId, "Ride cancelled");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(TransactionType.HOLD_RELEASE);
        assertThat(result.getDirection()).isEqualTo(TransactionDirection.INTERNAL);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getWallet()).isEqualTo(testWallet);

        verify(transactionRepository).save(any(Transaction.class));
        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should find transaction by idempotency key")
    void should_findTransactionByIdempotencyKey() {
        // Given
        Transaction existingTxn = Transaction.builder()
            .txnId(1)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .build();

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existingTxn));

        // When
        Optional<Transaction> result = walletService.findTransactionByIdempotencyKey(IDEMPOTENCY_KEY);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(existingTxn);
    }

    @Test
    @DisplayName("Should throw exception when wallet not found")
    void should_throwException_whenWalletNotFound() {
        // Given
        when(walletRepository.findByUser_UserId(USER_ID))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> walletService.createTopUpTransaction(
            USER_ID, AMOUNT, PSP_REF, IDEMPOTENCY_KEY, TransactionStatus.PENDING
        )).isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Wallet not found");
    }
}


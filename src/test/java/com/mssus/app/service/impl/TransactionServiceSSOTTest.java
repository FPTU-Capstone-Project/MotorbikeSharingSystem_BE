package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.SystemWallet;
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
import com.mssus.app.service.EmailService;
import com.mssus.app.service.BalanceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService SSOT Tests")
class TransactionServiceSSOTTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private BalanceCalculationService balanceCalculationService;

    @Spy
    @InjectMocks
    private TransactionServiceImpl service;

    private static final Integer USER_ID = 1;
    private static final Integer WALLET_ID = 1;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(200_000);
    private static final String PSP_REF = "ORDER-123";
    private static final String DESCRIPTION = "Wallet top-up";
    private static final String IDEMPOTENCY_KEY = "TOPUP_ORDER-123_200000";

    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .userId(USER_ID)
            .email("test@example.com")
            .fullName("Test User")
            .build();

        testWallet = Wallet.builder()
            .walletId(WALLET_ID)
            .user(testUser)
            .isActive(true)
            .build();

        lenient().doReturn(UUID.randomUUID()).when(service).generateGroupId();
    }

    @Test
    @DisplayName("Should create top-up transactions with idempotency check and wallet relationship")
    void should_createTopupTransactions_withIdempotencyAndWallet() {
        // Given
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(walletRepository.findByUser_UserId(USER_ID))
            .thenReturn(Optional.of(testWallet));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(testUser));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> {
                Transaction txn = invocation.getArgument(0);
                if (txn.getTxnId() == null) {
                    txn.setTxnId(1);
                }
                return txn;
            });

        // When
        List<Transaction> transactions = service.initTopup(USER_ID, AMOUNT, PSP_REF, DESCRIPTION);

        // Then
        assertThat(transactions).hasSize(2);

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txnCaptor.capture());

        List<Transaction> savedTransactions = txnCaptor.getAllValues();

        // System transaction
        Transaction systemTxn = savedTransactions.get(0);
        assertThat(systemTxn.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(systemTxn.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(systemTxn.getActorKind()).isEqualTo(ActorKind.SYSTEM);
        assertThat(systemTxn.getSystemWallet()).isEqualTo(SystemWallet.MASTER);
        assertThat(systemTxn.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(systemTxn.getWallet()).isNull(); // System transaction has no wallet

        // User transaction
        Transaction userTxn = savedTransactions.get(1);
        assertThat(userTxn.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(userTxn.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(userTxn.getActorKind()).isEqualTo(ActorKind.USER);
        assertThat(userTxn.getActorUser()).isEqualTo(testUser);
        assertThat(userTxn.getWallet()).isEqualTo(testWallet); // ✅ SSOT: Has wallet relationship
        assertThat(userTxn.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(userTxn.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY); // ✅ Idempotency key
        assertThat(userTxn.getPspRef()).isEqualTo(PSP_REF);

        // ✅ SSOT: Verify wallet balance is NOT updated
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should return existing transactions when idempotency key exists")
    void should_returnExistingTransactions_whenIdempotencyKeyExists() {
        // Given
        UUID groupId = UUID.randomUUID();
        Transaction existingTxn = Transaction.builder()
            .txnId(1)
            .groupId(groupId)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .build();

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existingTxn));
        Transaction systemTxn = Transaction.builder()
            .groupId(groupId)
            .actorKind(ActorKind.SYSTEM)
            .build();
        when(transactionRepository.findByGroupId(groupId))
            .thenReturn(List.of(existingTxn, systemTxn));

        // When
        List<Transaction> transactions = service.initTopup(USER_ID, AMOUNT, PSP_REF, DESCRIPTION);

        // Then
        assertThat(transactions).hasSize(2);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should update transactions to SUCCESS without updating wallet balance")
    void should_updateTransactionsToSuccess_withoutUpdatingBalance() {
        // Given
        Transaction systemTxn = Transaction.builder()
            .txnId(10)
            .actorKind(ActorKind.SYSTEM)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .build();

        Transaction userTxn = Transaction.builder()
            .txnId(11)
            .actorKind(ActorKind.USER)
            .actorUser(testUser)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .build();

        when(transactionRepository.findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING))
            .thenReturn(List.of(systemTxn, userTxn));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(testWallet));
        when(balanceCalculationService.calculateAvailableBalance(WALLET_ID)).thenReturn(BigDecimal.valueOf(500_000));

        // When
        service.handleTopupSuccess(PSP_REF);

        // Then
        assertThat(systemTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(userTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(emailService).sendTopUpSuccessEmail(
            eq(testUser.getEmail()),
            eq(testUser.getFullName()),
            eq(AMOUNT),
            anyString(),
            any(BigDecimal.class)
        );

        // ✅ SSOT: Verify wallet balance is NOT updated directly
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should update transactions to FAILED without updating wallet balance")
    void should_updateTransactionsToFailed_withoutUpdatingBalance() {
        // Given
        Transaction systemTxn = Transaction.builder()
            .txnId(20)
            .actorKind(ActorKind.SYSTEM)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .note("PSP Inflow")
            .build();

        Transaction userTxn = Transaction.builder()
            .txnId(21)
            .actorKind(ActorKind.USER)
            .actorUser(testUser)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .note("Topup")
            .build();

        when(transactionRepository.findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING))
            .thenReturn(List.of(systemTxn, userTxn));
        when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(testUser));

        // When
        service.handleTopupFailed(PSP_REF, "Payment declined");

        // Then
        assertThat(systemTxn.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(userTxn.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(userTxn.getNote()).contains("Failed: Payment declined");

        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(emailService).sendPaymentFailedEmail(
            eq(testUser.getEmail()),
            eq(testUser.getFullName()),
            eq(AMOUNT),
            anyString(),
            eq("Payment declined")
        );

        // ✅ SSOT: Verify wallet balance is NOT updated directly
        // Transaction status = FAILED nên sẽ không được tính vào balance
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should throw exception when no pending transactions found")
    void should_throwException_whenNoPendingTransactions() {
        // Given
        when(transactionRepository.findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING))
            .thenReturn(List.of());

        // When/Then
        assertThatThrownBy(() -> service.handleTopupSuccess(PSP_REF))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("No pending transactions");
    }

    @Test
    @DisplayName("Should throw NotFoundException when wallet does not exist")
    void should_throwNotFound_whenWalletMissing() {
        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(walletRepository.findByUser_UserId(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initTopup(USER_ID, AMOUNT, PSP_REF, DESCRIPTION))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Wallet not found");
    }
}


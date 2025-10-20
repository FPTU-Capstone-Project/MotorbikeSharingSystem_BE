package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.CreateTransactionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.mapper.TransactionMapper;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(200_000);
    private static final String PSP_REF = "PSP-REF";

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionMapper transactionMapper;

    @Spy
    @InjectMocks
    private TransactionServiceImpl service;

    private final AtomicInteger txnIdSequence = new AtomicInteger(1);

    @Test
    void should_createTopupTransactions_when_requestValid() {
        doReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .when(service).generateGroupId();
        doReturn(List.of()).when(transactionRepository)
            .findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);

        Wallet wallet = Wallet.builder()
            .shadowBalance(BigDecimal.valueOf(500_000))
            .pendingBalance(BigDecimal.valueOf(100_000))
            .build();
        doReturn(Optional.of(wallet)).when(walletRepository).findByUser_UserId(7);

        User user = createUser(7, "user@example.com");
        doReturn(Optional.of(user)).when(userRepository).findById(7);

        doReturnSavingTransactions();

        List<Transaction> transactions = service.initTopup(7, AMOUNT, PSP_REF, "Wallet top-up");

        assertThat(transactions).hasSize(2);
        assertThat(transactions)
            .extracting(Transaction::getGroupId)
            .containsOnly(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(txnCaptor.capture());
        List<Transaction> savedTransactions = txnCaptor.getAllValues();
        assertThat(savedTransactions).extracting(Transaction::getType)
            .containsExactly(TransactionType.TOPUP, TransactionType.TOPUP);
        assertThat(savedTransactions.get(1).getActorUser()).isEqualTo(user);
        assertThat(savedTransactions.get(1).getAfterPending())
            .isEqualTo(wallet.getPendingBalance().add(AMOUNT));

        verify(walletService).increasePendingBalance(7, AMOUNT);
        verifyNoMoreInteractions(walletService);
    }

    @Test
    void should_throwValidationException_when_initTopupDuplicatePspRef() {
        doReturn(List.of(new Transaction())).when(transactionRepository)
            .findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);

        assertThatThrownBy(() -> service.initTopup(5, AMOUNT, PSP_REF, "duplicate"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already exists");

        verify(transactionRepository).findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(walletService, walletRepository, userRepository);
    }

    @Test
    void should_updateTransactionsAndBalances_when_handleTopupSuccess() {
        User user = createUser(7, "user@example.com");
        Transaction systemTxn = Transaction.builder()
            .txnId(10)
            .actorKind(ActorKind.SYSTEM)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .build();
        Transaction userTxn = Transaction.builder()
            .txnId(11)
            .actorKind(ActorKind.USER)
            .actorUser(user)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .build();
        doReturn(List.of(systemTxn, userTxn)).when(transactionRepository)
            .findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);

        Wallet walletBefore = Wallet.builder()
            .shadowBalance(BigDecimal.valueOf(600_000))
            .pendingBalance(BigDecimal.valueOf(150_000))
            .build();
        Wallet walletAfter = Wallet.builder()
            .shadowBalance(walletBefore.getShadowBalance().add(AMOUNT))
            .pendingBalance(walletBefore.getPendingBalance().subtract(AMOUNT))
            .build();
        org.mockito.Mockito.when(walletRepository.findByUser_UserId(user.getUserId()))
            .thenReturn(Optional.of(walletBefore), Optional.of(walletAfter));
        doReturnSavingTransactions();

        doReturn(Optional.of(user)).when(userRepository).findById(user.getUserId());


        service.handleTopupSuccess(PSP_REF);

        assertThat(systemTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(userTxn.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(userTxn.getAfterAvail()).isEqualTo(walletBefore.getShadowBalance().add(AMOUNT));
        assertThat(userTxn.getAfterPending()).isEqualTo(walletBefore.getPendingBalance().subtract(AMOUNT));

        verify(transactionRepository).findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(walletService).transferPendingToAvailable(user.getUserId(), AMOUNT);
        verify(emailService).sendTopUpSuccessEmail(
            eq(user.getEmail()),
            eq(user.getFullName()),
            eq(AMOUNT),
            anyString(),
            eq(walletAfter.getShadowBalance())
        );
    }

    @Test
    void should_throwNotFound_when_handleTopupSuccessHasNoPendingTransactions() {
        doReturn(List.of()).when(transactionRepository)
            .findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);

        assertThatThrownBy(() -> service.handleTopupSuccess(PSP_REF))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("No pending transactions");
    }

    @Test
    void should_markTransactionsFailed_when_handleTopupFailed() {
        User user = createUser(9, "user@example.com");
        Transaction userTxn = Transaction.builder()
            .txnId(25)
            .actorKind(ActorKind.USER)
            .actorUser(user)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .note("Topup")
            .build();
        Transaction systemTxn = Transaction.builder()
            .txnId(26)
            .actorKind(ActorKind.SYSTEM)
            .amount(AMOUNT)
            .status(TransactionStatus.PENDING)
            .note("PSP Inflow")
            .build();
        doReturn(List.of(systemTxn, userTxn)).when(transactionRepository)
            .findByPspRefAndStatus(PSP_REF, TransactionStatus.PENDING);

        doReturnSavingTransactions();
        doReturn(Optional.of(user)).when(userRepository).findById(user.getUserId());

        service.handleTopupFailed(PSP_REF, "Bank declined");

        assertThat(userTxn.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(userTxn.getNote()).contains("Failed: Bank declined");
        verify(walletService).decreasePendingBalance(user.getUserId(), AMOUNT);
        verify(emailService).sendPaymentFailedEmail(
            eq(user.getEmail()),
            eq(user.getFullName()),
            eq(AMOUNT),
            anyString(),
            eq("Bank declined")
        );
    }

    @Test
    void should_createTransaction_when_requestValid() {
        User actor = createUser(5, "actor@example.com");
        doReturn(Optional.of(actor)).when(userRepository).findById(actor.getUserId());

        doReturnSavingTransactions();

        CreateTransactionRequest request = new CreateTransactionRequest(
            UUID.randomUUID(),
            TransactionType.TOPUP,
            TransactionDirection.IN,
            ActorKind.USER,
            actor.getUserId(),
            null,
            AMOUNT,
            "VND",
            null,
            null,
            null,
            null,
            TransactionStatus.SUCCESS,
            "Topup",
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(300_000),
            BigDecimal.valueOf(50_000),
            BigDecimal.valueOf(250_000)
        );

        Transaction saved = service.createTransaction(request);

        assertThat(saved.getType()).isEqualTo(TransactionType.TOPUP);
        assertThat(saved.getActorUser()).isEqualTo(actor);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void should_throwValidationException_when_createTransactionMissingSnapshots() {
        User actor = createUser(5, "actor@example.com");
        doReturn(Optional.of(actor)).when(userRepository).findById(actor.getUserId());

        CreateTransactionRequest request = new CreateTransactionRequest(
            UUID.randomUUID(),
            TransactionType.TOPUP,
            TransactionDirection.IN,
            ActorKind.USER,
            actor.getUserId(),
            null,
            AMOUNT,
            "VND",
            null,
            null,
            null,
            null,
            TransactionStatus.SUCCESS,
            "invalid",
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> service.createTransaction(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Balance snapshots");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void should_calculateCommission_when_inputsValid() {
        BigDecimal commission = service.calculateCommission(
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(0.15)
        );

        assertThat(commission).isEqualTo(BigDecimal.valueOf(15_000.00).setScale(2));
    }

    @Test
    void should_throwValidationException_when_calculateCommissionRateInvalid() {
        assertThatThrownBy(() -> service.calculateCommission(BigDecimal.TEN, BigDecimal.valueOf(1.5)))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Commission rate must be between 0 and 1");
    }

    @Test
    void should_returnUserHistory_when_filtersValid() {
        Authentication authentication = mockAuthentication("history@example.com");
        User user = createUser(8, "history@example.com");
        doReturn(Optional.of(user)).when(userRepository).findByEmail(user.getEmail());

        Transaction transaction = Transaction.builder()
            .txnId(33)
            .type(TransactionType.TOPUP)
            .build();
        Page<Transaction> page = new PageImpl<>(List.of(transaction), PageRequest.of(0, 10), 1);
        doReturn(page).when(transactionRepository)
            .findUserHistory(user.getUserId(), TransactionType.TOPUP, TransactionStatus.SUCCESS, PageRequest.of(0, 10));

        TransactionResponse responseItem = new TransactionResponse();
        doReturn(responseItem).when(transactionMapper).mapToTransactionResponse(transaction);

        PageResponse<TransactionResponse> response = service.getUserHistoryTransactions(
            authentication,
            PageRequest.of(0, 10),
            "TOPUP",
            "SUCCESS"
        );

        assertThat(response.getData()).containsExactly(responseItem);
        verify(transactionRepository).findUserHistory(user.getUserId(), TransactionType.TOPUP, TransactionStatus.SUCCESS, PageRequest.of(0, 10));
        verify(transactionMapper).mapToTransactionResponse(transaction);
    }

    @Test
    void should_throwValidationException_when_getUserHistoryInvalidType() {
        Authentication authentication = mockAuthentication("invalid@example.com");
        doReturn(Optional.of(createUser(9, "invalid@example.com"))).when(userRepository).findByEmail("invalid@example.com");

        assertThatThrownBy(() -> service.getUserHistoryTransactions(
            authentication,
            PageRequest.of(0, 5),
            "not-a-type",
            null
        )).isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid transaction type");

        verify(transactionRepository, never()).findUserHistory(any(), any(), any(), any());
    }

    private void doReturnSavingTransactions() {
        org.mockito.Mockito.when(transactionRepository.save(any(Transaction.class)))
            .thenAnswer(invocation -> {
                Transaction txn = invocation.getArgument(0);
                txn.setTxnId(txnIdSequence.getAndIncrement());
                return txn;
            });
    }

    private static Authentication mockAuthentication(String email) {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        doReturn(email).when(authentication).getName();
        return authentication;
    }

    private static User createUser(int id, String email) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        user.setFullName("User " + id);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}

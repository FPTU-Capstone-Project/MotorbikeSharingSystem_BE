package com.mssus.app.service.impl;

import com.amazonaws.services.sns.model.NotFoundException;
import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.dto.request.CreateTransactionRequest;
import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.ride.RideRequestSettledResponse;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.WalletService;
import com.mssus.app.service.domain.pricing.PricingService;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.SettlementResult;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class RideFundCoordinatingServiceImplTest {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100_000);

    @Mock
    private WalletService walletService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BalanceCalculationService balanceCalculationService;

    @Mock
    private PricingService pricingService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private RideFundCoordinatingServiceImpl service;

    private User rider;

    @BeforeEach
    void setUp() {
        rider = createUser(10, "rider@example.com");
    }

    @Test
    void should_holdFunds_when_balanceSufficient() {
        RideConfirmHoldRequest request = RideConfirmHoldRequest.builder()
            .riderId(10)
            .rideRequestId(500)
            .amount(ONE_HUNDRED)
            .note("Hold for ride")
            .build();
        Wallet wallet = Wallet.builder()
            .walletId(1)
            .build();

        BigDecimal availableBalance = BigDecimal.valueOf(200_000);
        BigDecimal pendingBalance = BigDecimal.valueOf(50_000);
        Transaction holdTransaction = Transaction.builder()
            .txnId(100)
            .type(TransactionType.HOLD_CREATE)
            .amount(ONE_HUNDRED)
            .wallet(wallet)
            .build();

        doReturn(wallet).when(walletService).getWalletByUserId(10);
        doReturn(availableBalance).when(balanceCalculationService).calculateAvailableBalance(1);
        doReturn(pendingBalance).when(balanceCalculationService).calculatePendingBalance(1);
        doReturn(Optional.of(rider)).when(userRepository).findById(10);
        doReturn(holdTransaction).when(walletService).holdAmount(eq(1), eq(ONE_HUNDRED), any(UUID.class), anyString());

        service.holdRideFunds(request);

        verify(walletService).getWalletByUserId(10);
        verify(balanceCalculationService).calculateAvailableBalance(1);
        verify(balanceCalculationService).calculatePendingBalance(1);
        verify(walletService).holdAmount(eq(1), eq(ONE_HUNDRED), any(UUID.class), anyString());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendNotification(
            eq(rider),
            eq(NotificationType.WALLET_HOLD),
            eq("Funds on Hold"),
            messageCaptor.capture(),
            eq(null),
            eq(Priority.MEDIUM),
            eq(DeliveryMethod.IN_APP),
            eq(null)
        );
        assertThat(messageCaptor.getValue()).contains("booking request #500");

        verifyNoMoreInteractions(walletService, notificationService, userRepository, balanceCalculationService);
        verifyNoInteractions(pricingService, transactionRepository, walletRepository);
    }

    @Test
    void should_throwValidationException_when_balanceInsufficientForHold() {
        RideConfirmHoldRequest request = RideConfirmHoldRequest.builder()
            .riderId(77)
            .rideRequestId(900)
            .amount(BigDecimal.valueOf(500_000))
            .build();
        Wallet wallet = Wallet.builder()
            .walletId(2)
            .build();

        BigDecimal availableBalance = BigDecimal.valueOf(100_000);

        doReturn(wallet).when(walletService).getWalletByUserId(77);
        doReturn(availableBalance).when(balanceCalculationService).calculateAvailableBalance(2);

        assertThatThrownBy(() -> service.holdRideFunds(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Insufficient balance");

        verify(walletService).getWalletByUserId(77);
        verify(balanceCalculationService).calculateAvailableBalance(2);
        verify(walletService, never()).holdAmount(anyInt(), any(), any(), anyString());
        verifyNoInteractions(userRepository, notificationService, pricingService, transactionRepository, walletRepository);
    }

    @Test
    void should_settleRideFunds_when_inputsValid() {
        RideCompleteSettlementRequest request = RideCompleteSettlementRequest.builder()
            .riderId(10)
            .driverId(20)
            .rideRequestId(700)
            .note("ride complete")
            .build();
        FareBreakdown fareBreakdown = createFareBreakdown(BigDecimal.valueOf(150_000));
        SettlementResult settlementResult = new SettlementResult(
            MoneyVnd.VND(150_000),
            MoneyVnd.VND(120_000),
            MoneyVnd.VND(30_000),
            null
        );
        Wallet riderWallet = Wallet.builder()
            .walletId(1)
            .build();
        Wallet driverWallet = Wallet.builder()
            .walletId(2)
            .build();
        User driver = createUser(20, "driver@example.com");

        BigDecimal riderAvailableBefore = BigDecimal.valueOf(300_000);
        BigDecimal riderPendingBefore = BigDecimal.valueOf(200_000);
        BigDecimal driverAvailableBefore = BigDecimal.valueOf(50_000);
        BigDecimal driverPendingBefore = BigDecimal.ZERO;

        doReturn(settlementResult).when(pricingService).settle(fareBreakdown);
        doReturn(riderWallet).when(walletService).getWalletByUserId(10);
        doReturn(driverWallet).when(walletService).getWalletByUserId(20);
        doReturn(riderAvailableBefore).when(balanceCalculationService).calculateAvailableBalance(1);
        doReturn(riderPendingBefore).when(balanceCalculationService).calculatePendingBalance(1);
        doReturn(driverAvailableBefore).when(balanceCalculationService).calculateAvailableBalance(2);
        doReturn(driverPendingBefore).when(balanceCalculationService).calculatePendingBalance(2);
        doReturn(Optional.of(rider)).when(userRepository).findById(10);
        doReturn(Optional.of(driver)).when(userRepository).findById(20);
        doReturn(Transaction.builder().txnId(1).build()).when(transactionRepository).save(any(Transaction.class));

        RideRequestSettledResponse response = service.settleRideFunds(request, fareBreakdown);

        assertThat(response.driverEarnings()).isEqualTo(settlementResult.driverPayout().amount());
        assertThat(response.systemCommission()).isEqualTo(settlementResult.commission().amount());

        verify(pricingService).settle(fareBreakdown);
        verify(walletService).getWalletByUserId(10);
        verify(walletService).getWalletByUserId(20);
        verify(balanceCalculationService).calculateAvailableBalance(1);
        verify(balanceCalculationService).calculatePendingBalance(1);
        verify(balanceCalculationService).calculateAvailableBalance(2);
        verify(balanceCalculationService).calculatePendingBalance(2);

        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(3)).save(txnCaptor.capture());
        List<Transaction> transactions = txnCaptor.getAllValues();
        UUID groupId = transactions.get(0).getGroupId();
        assertThat(groupId).isNotNull();
        assertThat(transactions).extracting(Transaction::getGroupId).allMatch(groupId::equals);
        assertThat(transactions).extracting(Transaction::getType)
            .containsExactly(
                TransactionType.CAPTURE_FARE,
                TransactionType.CAPTURE_FARE,
                TransactionType.CAPTURE_FARE);
        assertThat(transactions).extracting(Transaction::getDirection)
            .containsExactly(
                TransactionDirection.OUT,
                TransactionDirection.IN,
                TransactionDirection.IN);

        verify(notificationService).sendNotification(
            eq(rider),
            eq(NotificationType.WALLET_CAPTURE),
            eq("Payment Successful"),
            anyString(),
            anyString(),
            eq(Priority.MEDIUM),
            eq(DeliveryMethod.IN_APP),
            eq(null)
        );
        verify(notificationService).sendNotification(
            eq(driver),
            eq(NotificationType.WALLET_PAYOUT),
            eq("You've Been Paid"),
            anyString(),
            anyString(),
            eq(Priority.MEDIUM),
            eq(DeliveryMethod.IN_APP),
            eq(null)
        );

        verifyNoMoreInteractions(pricingService, walletService, notificationService, userRepository, balanceCalculationService, transactionRepository);
        verifyNoInteractions(walletRepository);
    }

    static Stream<Arguments> missingUserOnSettleProvider() {
        return Stream.of(
            Arguments.of(true, "missing rider"),
            Arguments.of(false, "missing driver")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("missingUserOnSettleProvider")
    void should_throwBaseDomainException_when_settleRideFundsUserMissing(boolean missingRider, String description) {
        RideCompleteSettlementRequest request = RideCompleteSettlementRequest.builder()
            .riderId(10)
            .driverId(20)
            .rideRequestId(1)
            .build();
        FareBreakdown fareBreakdown = createFareBreakdown(BigDecimal.valueOf(100_000));
        SettlementResult settlementResult = new SettlementResult(
            MoneyVnd.VND(100_000),
            MoneyVnd.VND(80_000),
            MoneyVnd.VND(20_000),
            null
        );
        Wallet riderWallet = Wallet.builder()
            .walletId(1)
            .build();
        Wallet driverWallet = Wallet.builder()
            .walletId(2)
            .build();

        doReturn(settlementResult).when(pricingService).settle(fareBreakdown);
        doReturn(riderWallet).when(walletService).getWalletByUserId(10);
        doReturn(driverWallet).when(walletService).getWalletByUserId(20);
        doReturn(BigDecimal.valueOf(200_000)).when(balanceCalculationService).calculateAvailableBalance(1);
        doReturn(BigDecimal.valueOf(120_000)).when(balanceCalculationService).calculatePendingBalance(1);
        doReturn(BigDecimal.valueOf(30_000)).when(balanceCalculationService).calculateAvailableBalance(2);
        doReturn(BigDecimal.ZERO).when(balanceCalculationService).calculatePendingBalance(2);
        if (missingRider) {
            doReturn(Optional.empty()).when(userRepository).findById(10);
        } else {
            doReturn(Optional.of(rider)).when(userRepository).findById(10);
            doReturn(Optional.empty()).when(userRepository).findById(20);
        }

        assertThatThrownBy(() -> service.settleRideFunds(request, fareBreakdown))
            .as(description)
            .isInstanceOf(com.mssus.app.common.exception.BaseDomainException.class)
            .extracting("errorId")
            .isEqualTo("user.not-found.by-id");

        verify(userRepository).findById(10);
        if (!missingRider) {
            verify(userRepository).findById(20);
        }
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void should_releaseRideFunds_when_holdExists() {
        RideHoldReleaseRequest request = RideHoldReleaseRequest.builder()
            .riderId(10)
            .rideRequestId(900)
            .note("cancel ride")
            .build();
        UUID groupId = UUID.randomUUID();
        User riderUser = createUser(10, "rider@example.com");
        SharedRideRequest sharedRideRequest = new SharedRideRequest();
        sharedRideRequest.setSharedRideRequestId(900);
        Transaction initialTransaction = Transaction.builder()
            .groupId(groupId)
            .sharedRideRequest(sharedRideRequest)
            .actorUser(riderUser)
            .build();
        Wallet riderWallet = Wallet.builder()
            .walletId(1)
            .build();
        Transaction releaseTransaction = Transaction.builder()
            .txnId(200)
            .type(TransactionType.HOLD_RELEASE)
            .amount(ONE_HUNDRED)
            .wallet(riderWallet)
            .build();

        BigDecimal availableBalance = BigDecimal.valueOf(150_000);

        doReturn(List.of(initialTransaction)).when(transactionRepository).findAll();
        doReturn(Optional.of(riderWallet)).when(walletRepository).findByUser_UserId(10);
        doReturn(Optional.of(riderUser)).when(userRepository).findById(10);
        doReturn(releaseTransaction).when(walletService).releaseHold(eq(groupId), anyString());
        doReturn(availableBalance).when(balanceCalculationService).calculateAvailableBalance(1);

        service.releaseRideFunds(request);

        verify(transactionRepository).findAll();
        verify(walletService).releaseHold(eq(groupId), anyString());
        verify(balanceCalculationService).calculateAvailableBalance(1);

        verify(notificationService).sendNotification(
            eq(riderUser),
            eq(NotificationType.WALLET_RELEASE),
            eq("Hold Released"),
            anyString(),
            anyString(),
            eq(Priority.MEDIUM),
            eq(DeliveryMethod.IN_APP),
            eq(null)
        );

        verifyNoMoreInteractions(walletService, notificationService, transactionRepository, walletRepository, userRepository, balanceCalculationService);
        verifyNoInteractions(pricingService);
    }

    @Test
    void should_throwValidationException_when_releaseHoldAlreadyReleased() {
        RideHoldReleaseRequest request = RideHoldReleaseRequest.builder()
            .riderId(10)
            .rideRequestId(1000)
            .build();
        UUID groupId = UUID.randomUUID();
        User riderUser = createUser(10, "rider@example.com");
        Transaction initialTransaction = Transaction.builder()
            .groupId(groupId)
            .sharedRideRequest(buildSharedRideRequest(1000))
            .actorUser(riderUser)
            .build();

        doReturn(List.of(initialTransaction)).when(transactionRepository).findAll();
        doThrow(new ValidationException("Hold has already been released"))
            .when(walletService).releaseHold(eq(groupId), anyString());

        assertThatThrownBy(() -> service.releaseRideFunds(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Hold has already been released");

        verify(transactionRepository).findAll();
        verify(walletService).releaseHold(eq(groupId), anyString());
        verifyNoMoreInteractions(transactionRepository, walletService);
        verifyNoInteractions(walletRepository, notificationService, pricingService, userRepository);
    }

    @Test
    void should_throwNotFoundException_when_releaseTransactionMissing() {
        RideHoldReleaseRequest request = RideHoldReleaseRequest.builder()
            .riderId(10)
            .rideRequestId(1001)
            .build();

        doReturn(List.of()).when(transactionRepository).findAll();

        assertThatThrownBy(() -> service.releaseRideFunds(request))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Transaction not found");

        verify(transactionRepository).findAll();
        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(walletService, walletRepository, notificationService, userRepository, pricingService);
    }

    private static FareBreakdown createFareBreakdown(BigDecimal total) {
        return new FareBreakdown(
            Instant.now(),
            5_000,
            MoneyVnd.VND(0),
            MoneyVnd.VND(total),
            MoneyVnd.VND(total),
            BigDecimal.valueOf(0.2)
        );
    }

    private static User createUser(int userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static SharedRideRequest buildSharedRideRequest(int rideRequestId) {
        SharedRideRequest sharedRideRequest = new SharedRideRequest();
        sharedRideRequest.setSharedRideRequestId(rideRequestId);
        return sharedRideRequest;
    }
}

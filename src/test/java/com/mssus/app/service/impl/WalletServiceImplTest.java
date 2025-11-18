package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.common.exception.NotFoundException;
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
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.PayOSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WalletServiceImpl Tests")
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PayOSService payOSService;

    @Mock
    private BalanceCalculationService balanceCalculationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Wallet testWallet;
    private Transaction testTransaction;
    private TopUpInitRequest testTopUpRequest;
    private PayoutInitRequest testPayoutRequest;

    @BeforeEach
    void setUp() {
        setupTestData();
        setupMockBehavior();
    }

    private void setupTestData() {
        testUser = createTestUser();
        testWallet = createTestWallet();
        testTransaction = createTestTransaction();
        testTopUpRequest = createTestTopUpRequest();
        testPayoutRequest = createTestPayoutRequest();
    }

    private void setupMockBehavior() {
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUser_UserId(1)).thenReturn(Optional.of(testWallet));
        when(walletRepository.existsByUserId(1)).thenReturn(true);
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);
        when(transactionRepository.findByActorUserIdOrderByCreatedAtDesc(anyInt())).thenReturn(List.of(testTransaction));
        when(transactionRepository.findByUserIdAndStatus(anyInt(), any(TransactionStatus.class))).thenReturn(List.of(testTransaction));
        
        // ✅ SSOT: Mock BalanceCalculationService
        when(balanceCalculationService.calculateAvailableBalance(1))
            .thenReturn(new BigDecimal("100000"));
        when(balanceCalculationService.calculatePendingBalance(1))
            .thenReturn(new BigDecimal("20000"));
    }

    // ========== BALANCE UPDATE TESTS ==========
    // ❌ DEPRECATED: These tests are for methods that have been removed in SSOT refactor
    // Balance updates should now be done through Transaction ledger, not direct wallet updates
    // All tests below have been commented out as they test deprecated methods:
    // - updateWalletBalanceOnTopUp
    // - increasePendingBalance / decreasePendingBalance
    // - increaseShadowBalance / decreaseShadowBalance
    // - transferPendingToAvailable

    /*
    @Test
    @DisplayName("should_updateWalletBalanceOnTopUp_when_validInput")
    void should_updateWalletBalanceOnTopUp_when_validInput() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_walletNotFoundForTopUp")
    void should_throwNotFoundException_when_walletNotFoundForTopUp() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_increasePendingBalance_when_validInput")
    void should_increasePendingBalance_when_validInput() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_increasePendingBalanceFails")
    void should_throwNotFoundException_when_increasePendingBalanceFails() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_decreasePendingBalance_when_validInput")
    void should_decreasePendingBalance_when_validInput() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_throwValidationException_when_decreasePendingBalanceFails")
    void should_throwValidationException_when_decreasePendingBalanceFails() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_increaseShadowBalance_when_validInput")
    void should_increaseShadowBalance_when_validInput() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_increaseShadowBalanceFails")
    void should_throwNotFoundException_when_increaseShadowBalanceFails() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_decreaseShadowBalance_when_validInput")
    void should_decreaseShadowBalance_when_validInput() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_throwValidationException_when_decreaseShadowBalanceFails")
    void should_throwValidationException_when_decreaseShadowBalanceFails() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_transferPendingToAvailable_when_validInput")
    void should_transferPendingToAvailable_when_validInput() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_transferPendingToAvailable_when_shadowBalanceIsNull")
    void should_transferPendingToAvailable_when_shadowBalanceIsNull() {
        // ... test code ...
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_walletNotFoundForTransfer")
    void should_throwNotFoundException_when_walletNotFoundForTransfer() {
        // ... test code ...
    }
    */

    // ========== GET BALANCE TESTS ==========

    @Test
    @DisplayName("should_getBalance_when_validAuthentication")
    void should_getBalance_when_validAuthentication() {
        // ✅ SSOT: Mock balance from ledger
        BigDecimal availableBalance = new BigDecimal("100000");
        BigDecimal pendingBalance = new BigDecimal("20000");
        when(balanceCalculationService.calculateAvailableBalance(testWallet.getWalletId()))
            .thenReturn(availableBalance);
        when(balanceCalculationService.calculatePendingBalance(testWallet.getWalletId()))
            .thenReturn(pendingBalance);

        // Act
        WalletResponse result = walletService.getBalance(authentication);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWalletId()).isEqualTo(testWallet.getWalletId());
        assertThat(result.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(result.getAvailableBalance()).isEqualTo(availableBalance);  // ✅ SSOT: From ledger
        assertThat(result.getPendingBalance()).isEqualTo(pendingBalance);  // ✅ SSOT: From ledger
        assertThat(result.getTotalToppedUp()).isEqualTo(testWallet.getTotalToppedUp());
        assertThat(result.getTotalSpent()).isEqualTo(testWallet.getTotalSpent());
        assertThat(result.getIsActive()).isEqualTo(testWallet.getIsActive());

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
        // ✅ SSOT: Verify balance is calculated from ledger
        verify(balanceCalculationService).calculateAvailableBalance(testWallet.getWalletId());
        verify(balanceCalculationService).calculatePendingBalance(testWallet.getWalletId());
        verifyNoMoreInteractions(authentication, userRepository, walletRepository, balanceCalculationService);
    }

    @Test
    @DisplayName("should_throwValidationException_when_authenticationIsNull")
    void should_throwValidationException_when_authenticationIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.getBalance(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Authentication cannot be null");

        verifyNoInteractions(userRepository, walletRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_userNotFoundByEmail")
    void should_throwNotFoundException_when_userNotFoundByEmail() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.getBalance(authentication))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("User not found with email: test@example.com");

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository, never()).findByUser_UserId(anyInt());
        verifyNoMoreInteractions(authentication, userRepository, walletRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_walletNotFoundForUser")
    void should_throwNotFoundException_when_walletNotFoundForUser() {
        // Arrange
        when(walletRepository.findByUser_UserId(testUser.getUserId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.getBalance(authentication))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Wallet not found for user: " + testUser.getUserId());

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
        verifyNoMoreInteractions(authentication, userRepository, walletRepository);
    }

    // ========== GET WALLET BY USER ID TESTS ==========

    @Test
    @DisplayName("should_getWalletByUserId_when_validUserId")
    void should_getWalletByUserId_when_validUserId() {
        // Arrange
        Integer userId = 1;

        // Act
        Wallet result = walletService.getWalletByUserId(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWalletId()).isEqualTo(testWallet.getWalletId());
        assertThat(result.getUser().getUserId()).isEqualTo(userId);

        verify(walletRepository).findByUser_UserId(userId);
        verifyNoMoreInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_userIdIsNull")
    void should_throwValidationException_when_userIdIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.getWalletByUserId(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("User ID cannot be null");

        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_walletNotFoundByUserId")
    void should_throwNotFoundException_when_walletNotFoundByUserId() {
        // Arrange
        Integer userId = 999;
        when(walletRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.getWalletByUserId(userId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Wallet not found for user: " + userId);

        verify(walletRepository).findByUser_UserId(userId);
        verifyNoMoreInteractions(walletRepository);
    }

    // ========== INITIATE TOP UP TESTS ==========
    // ❌ DEPRECATED: initiateTopUp method has been moved to TopUpService
    // These tests should be moved to TopUpServiceImplTest

    /*
    @Test
    @DisplayName("should_initiateTopUp_when_validRequest")
    void should_initiateTopUp_when_validRequest() throws Exception {
        // Arrange
        CheckoutResponseData mockCheckoutData = createMockCheckoutData();
        when(payOSService.createTopUpPaymentLink(
            anyInt(), any(BigDecimal.class), anyString(), anyString(), anyString()))
            .thenReturn(mockCheckoutData);

        // Act
        TopUpInitResponse result = walletService.initiateTopUp(testTopUpRequest, authentication);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTransactionRef()).isEqualTo(String.valueOf(mockCheckoutData.getOrderCode()));
        assertThat(result.getPaymentUrl()).isEqualTo(mockCheckoutData.getCheckoutUrl());
        assertThat(result.getQrCodeUrl()).isEqualTo(mockCheckoutData.getQrCode());
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getExpirySeconds()).isEqualTo(900);

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
        verify(payOSService).createTopUpPaymentLink(
            testUser.getUserId(),
            testTopUpRequest.getAmount(),
            "Wallet top up",
            testTopUpRequest.getReturnUrl(),
            testTopUpRequest.getCancelUrl()
        );
    }

    @Test
    @DisplayName("should_initiateTopUp_when_walletDoesNotExist")
    void should_initiateTopUp_when_walletDoesNotExist() throws Exception {
        // Arrange
        when(walletRepository.findByUser_UserId(testUser.getUserId())).thenReturn(Optional.empty());
        when(walletRepository.existsByUserId(testUser.getUserId())).thenReturn(false);
        CheckoutResponseData mockCheckoutData = createMockCheckoutData();
        when(payOSService.createTopUpPaymentLink(
            anyInt(), any(BigDecimal.class), anyString(), anyString(), anyString()))
            .thenReturn(mockCheckoutData);

        // Act
        TopUpInitResponse result = walletService.initiateTopUp(testTopUpRequest, authentication);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");

        verify(walletRepository).findByUser_UserId(testUser.getUserId());
        verify(walletRepository).existsByUserId(testUser.getUserId());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    @DisplayName("should_throwValidationException_when_walletIsFrozen")
    void should_throwValidationException_when_walletIsFrozen() throws Exception {
        // Arrange
        testWallet.setIsActive(false);

        // Act & Assert
        assertThatThrownBy(() -> walletService.initiateTopUp(testTopUpRequest, authentication))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Wallet is frozen. Please contact support.");

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
        verify(payOSService, never()).createTopUpPaymentLink(anyInt(), any(BigDecimal.class), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_throwRuntimeException_when_payOSServiceFails")
    void should_throwRuntimeException_when_payOSServiceFails() throws Exception {
        // Arrange
        when(payOSService.createTopUpPaymentLink(
            anyInt(), any(BigDecimal.class), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("PayOS service error"));

        // Act & Assert
        assertThatThrownBy(() -> walletService.initiateTopUp(testTopUpRequest, authentication))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to initiate top-up: PayOS service error");

        verify(payOSService).createTopUpPaymentLink(
            testUser.getUserId(),
            testTopUpRequest.getAmount(),
            "Wallet top up",
            testTopUpRequest.getReturnUrl(),
            testTopUpRequest.getCancelUrl()
        );
    }
    */

    // ========== INITIATE PAYOUT TESTS ==========

    @Test
    @DisplayName("should_initiatePayout_when_validRequest")
    void should_initiatePayout_when_validRequest() {
        // Act
        PayoutInitResponse result = walletService.initiatePayout(testPayoutRequest, authentication);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPayoutRef()).startsWith("PAYOUT-");
        assertThat(result.getAmount()).isEqualTo(testPayoutRequest.getAmount());
        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        assertThat(result.getEstimatedCompletionTime()).isNotNull();
        assertThat(result.getMaskedAccountNumber()).isEqualTo("****7890");

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
    }

    @Test
    @DisplayName("should_throwValidationException_when_insufficientBalance")
    void should_throwValidationException_when_insufficientBalance() {
        // Arrange
        testPayoutRequest.setAmount(new BigDecimal("1000000")); // More than available balance

        // ✅ SSOT: Mock insufficient balance from ledger
        BigDecimal insufficientBalance = new BigDecimal("30000");
        when(balanceCalculationService.calculateAvailableBalance(testWallet.getWalletId()))
            .thenReturn(insufficientBalance);

        // Act & Assert
        assertThatThrownBy(() -> walletService.initiatePayout(testPayoutRequest, authentication))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Insufficient balance. Available: " + insufficientBalance + ", Required: " + testPayoutRequest.getAmount());

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
    }

    @Test
    @DisplayName("should_throwValidationException_when_walletIsFrozenForPayout")
    void should_throwValidationException_when_walletIsFrozenForPayout() {
        // Arrange
        testWallet.setIsActive(false);

        // Act & Assert
        assertThatThrownBy(() -> walletService.initiatePayout(testPayoutRequest, authentication))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Wallet is frozen. Please contact support.");

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
    }

    // ========== DRIVER EARNINGS TESTS ==========

    @Test
    @DisplayName("should_getDriverEarnings_when_validAuthentication")
    void should_getDriverEarnings_when_validAuthentication() {
        // ✅ SSOT: Mock balance from ledger
        BigDecimal availableBalance = new BigDecimal("100000");
        BigDecimal pendingBalance = new BigDecimal("20000");
        when(balanceCalculationService.calculateAvailableBalance(testWallet.getWalletId()))
            .thenReturn(availableBalance);
        when(balanceCalculationService.calculatePendingBalance(testWallet.getWalletId()))
            .thenReturn(pendingBalance);

        // Act
        DriverEarningsResponse result = walletService.getDriverEarnings(authentication);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAvailableBalance()).isEqualTo(availableBalance);  // ✅ SSOT: From ledger
        assertThat(result.getPendingEarnings()).isEqualTo(pendingBalance);  // ✅ SSOT: From ledger
        assertThat(result.getTotalEarnings()).isEqualByComparingTo(testTransaction.getAmount());
        assertThat(result.getTotalTrips()).isEqualTo(1);
        assertThat(result.getMonthEarnings()).isEqualByComparingTo(testTransaction.getAmount());
        assertThat(result.getWeekEarnings()).isEqualByComparingTo(testTransaction.getAmount());
        assertThat(result.getAvgEarningsPerTrip()).isEqualByComparingTo(testTransaction.getAmount());
        assertThat(result.getTotalCommissionPaid()).isNotNull();

        verify(authentication).getName();
        verify(userRepository).findByEmail("test@example.com");
        verify(walletRepository).findByUser_UserId(testUser.getUserId());
        verify(transactionRepository).findByActorUserIdOrderByCreatedAtDesc(testUser.getUserId());
        // ✅ SSOT: Verify balance is calculated from ledger
        verify(balanceCalculationService).calculateAvailableBalance(testWallet.getWalletId());
        verify(balanceCalculationService).calculatePendingBalance(testWallet.getWalletId());
    }

    @Test
    @DisplayName("should_getDriverEarnings_when_noTransactions")
    void should_getDriverEarnings_when_noTransactions() {
        // Arrange
        when(transactionRepository.findByActorUserIdOrderByCreatedAtDesc(testUser.getUserId())).thenReturn(List.of());
        
        // ✅ SSOT: Mock balance from ledger
        BigDecimal availableBalance = new BigDecimal("100000");
        BigDecimal pendingBalance = new BigDecimal("20000");
        when(balanceCalculationService.calculateAvailableBalance(testWallet.getWalletId()))
            .thenReturn(availableBalance);
        when(balanceCalculationService.calculatePendingBalance(testWallet.getWalletId()))
            .thenReturn(pendingBalance);

        // Act
        DriverEarningsResponse result = walletService.getDriverEarnings(authentication);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAvailableBalance()).isEqualTo(availableBalance);  // ✅ SSOT: From ledger
        assertThat(result.getPendingEarnings()).isEqualTo(pendingBalance);  // ✅ SSOT: From ledger
        assertThat(result.getTotalEarnings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalTrips()).isEqualTo(0);
        assertThat(result.getMonthEarnings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getWeekEarnings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAvgEarningsPerTrip()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalCommissionPaid()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(transactionRepository).findByActorUserIdOrderByCreatedAtDesc(testUser.getUserId());
        // ✅ SSOT: Verify balance is calculated from ledger
        verify(balanceCalculationService).calculateAvailableBalance(testWallet.getWalletId());
        verify(balanceCalculationService).calculatePendingBalance(testWallet.getWalletId());
    }

    // ========== CREATE WALLET FOR USER TESTS ==========

    @Test
    @DisplayName("should_createWalletForUser_when_validUserId")
    void should_createWalletForUser_when_validUserId() {
        // Arrange
        Integer userId = 2;
        User testUser2 = createTestUser();
        testUser2.setUserId(2);
        when(walletRepository.existsByUserId(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser2));
        Wallet newWallet = createTestWallet();
        newWallet.setWalletId(2);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet savedWallet = invocation.getArgument(0);
            savedWallet.setWalletId(2);
            return savedWallet;
        });

        // Act
        Wallet result = walletService.createWalletForUser(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWalletId()).isEqualTo(2);
        assertThat(result.getUser().getUserId()).isEqualTo(userId);
        // ✅ SSOT: Wallet entity không còn shadowBalance/pendingBalance fields
        // Balance sẽ được tính từ Transaction ledger khi query
        assertThat(result.getTotalToppedUp()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.getTotalSpent()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.getIsActive()).isTrue();

        verify(walletRepository).existsByUserId(userId);
        verify(userRepository).findById(userId);
        verify(walletRepository).save(any(Wallet.class));
        verifyNoMoreInteractions(walletRepository, userRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_userIdIsNullForCreateWallet")
    void should_throwValidationException_when_userIdIsNullForCreateWallet() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.createWalletForUser(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("User ID cannot be null");

        verifyNoInteractions(walletRepository, userRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_walletAlreadyExists")
    void should_throwValidationException_when_walletAlreadyExists() {
        // Arrange
        Integer userId = 1;

        // Act & Assert
        assertThatThrownBy(() -> walletService.createWalletForUser(userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Wallet already exists for user: " + userId);

        verify(walletRepository).existsByUserId(userId);
        verify(userRepository, never()).findById(anyInt());
        verify(walletRepository, never()).save(any(Wallet.class));
        verifyNoMoreInteractions(walletRepository, userRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_userNotFound")
    void should_throwNotFoundException_when_userNotFound() {
        // Arrange
        Integer userId = 999;
        when(walletRepository.existsByUserId(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.createWalletForUser(userId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("User not found: " + userId);

        verify(walletRepository).existsByUserId(userId);
        verify(userRepository).findById(userId);
        verify(walletRepository, never()).save(any(Wallet.class));
        verifyNoMoreInteractions(walletRepository, userRepository);
    }

    // ========== HAS SUFFICIENT BALANCE TESTS ==========

    @Test
    @DisplayName("should_returnTrue_when_sufficientBalance")
    void should_returnTrue_when_sufficientBalance() {
        // Arrange
        Integer userId = 1;
        BigDecimal amount = new BigDecimal("50000");

        // Act
        boolean result = walletService.hasSufficientBalance(userId, amount);

        // Assert
        assertThat(result).isTrue();

        verify(walletRepository).findByUser_UserId(userId);
        verifyNoMoreInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_returnFalse_when_insufficientBalance")
    void should_returnFalse_when_insufficientBalance() {
        // Arrange
        Integer userId = 1;
        BigDecimal amount = new BigDecimal("1000000"); // More than available balance

        // Act
        boolean result = walletService.hasSufficientBalance(userId, amount);

        // Assert
        assertThat(result).isFalse();

        verify(walletRepository).findByUser_UserId(userId);
        verifyNoMoreInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_userIdIsNullForBalanceCheck")
    void should_throwValidationException_when_userIdIsNullForBalanceCheck() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.hasSufficientBalance(null, new BigDecimal("1000")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("User ID cannot be null");

        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_amountIsNull")
    void should_throwValidationException_when_amountIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.hasSufficientBalance(1, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Amount must be non-negative");

        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_amountIsNegative")
    void should_throwValidationException_when_amountIsNegative() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.hasSufficientBalance(1, new BigDecimal("-1000")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Amount must be non-negative");

        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_walletNotFound")
    void should_throwNotFoundException_when_walletNotFound() {
        // Arrange
        Integer userId = 999;
        BigDecimal amount = new BigDecimal("1000");
        when(walletRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.hasSufficientBalance(userId, amount))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Wallet not found for user: " + userId);

        verify(walletRepository).findByUser_UserId(userId);
        verifyNoMoreInteractions(walletRepository);
    }

    // ========== RECONCILE WALLET BALANCE TESTS ==========

    @Test
    @DisplayName("should_reconcileWalletBalance_when_validUserId")
    void should_reconcileWalletBalance_when_validUserId() {
        // Arrange
        Integer userId = 1;

        // Act
        walletService.reconcileWalletBalance(userId);

        // Assert
        verify(walletRepository).findByUser_UserId(userId);
        verify(transactionRepository).findByUserIdAndStatus(userId, TransactionStatus.SUCCESS);
        verify(walletRepository).save(testWallet);
        verifyNoMoreInteractions(walletRepository, transactionRepository);
    }

    @Test
    @DisplayName("should_throwValidationException_when_userIdIsNullForReconcile")
    void should_throwValidationException_when_userIdIsNullForReconcile() {
        // Act & Assert
        assertThatThrownBy(() -> walletService.reconcileWalletBalance(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("User ID cannot be null");

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_walletNotFoundForReconcile")
    void should_throwNotFoundException_when_walletNotFoundForReconcile() {
        // Arrange
        Integer userId = 999;
        when(walletRepository.findByUser_UserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.reconcileWalletBalance(userId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Wallet not found for user: " + userId);

        verify(walletRepository).findByUser_UserId(userId);
        verify(transactionRepository, never()).findByUserIdAndStatus(anyInt(), any(TransactionStatus.class));
        verify(walletRepository, never()).save(any(Wallet.class));
        verifyNoMoreInteractions(walletRepository, transactionRepository);
    }

    // ========== PARAMETERIZED TESTS ==========

    // ❌ DEPRECATED: updateWalletBalanceOnTopUp method removed in SSOT refactor
    /*
    @ParameterizedTest
    @MethodSource("amountProvider")
    @DisplayName("should_updateWalletBalanceOnTopUp_when_differentAmounts")
    void should_updateWalletBalanceOnTopUp_when_differentAmounts(BigDecimal amount) {
        // ... test code ...
    }
    */

    @ParameterizedTest
    @MethodSource("amountProvider")
    @DisplayName("should_hasSufficientBalance_when_differentAmounts")
    void should_hasSufficientBalance_when_differentAmounts(BigDecimal amount) {
        // Arrange
        Integer userId = 1;
        BigDecimal availableBalance = new BigDecimal("100000");
        
        // ✅ SSOT: Mock balance from ledger
        when(balanceCalculationService.calculateAvailableBalance(testWallet.getWalletId()))
            .thenReturn(availableBalance);

        // Act
        boolean result = walletService.hasSufficientBalance(userId, amount);

        // Assert
        boolean expected = availableBalance.compareTo(amount) >= 0;  // ✅ SSOT: Compare with ledger balance
        assertThat(result).isEqualTo(expected);

        verify(walletRepository).findByUser_UserId(userId);
        // ✅ SSOT: Verify balance is calculated from ledger
        verify(balanceCalculationService).calculateAvailableBalance(testWallet.getWalletId());
    }

    // ========== HELPER METHODS ==========

    private User createTestUser() {
        return User.builder()
            .userId(1)
            .email("test@example.com")
            .phone("0901234567")
            .fullName("Test User")
            .build();
    }

    private Wallet createTestWallet() {
        return Wallet.builder()
            .walletId(1)
            .user(testUser)
            // ✅ SSOT: shadowBalance và pendingBalance không còn trong Wallet entity
            // Balance sẽ được tính từ Transaction ledger
            .totalToppedUp(new BigDecimal("150000"))
            .totalSpent(new BigDecimal("50000"))
            .isActive(true)
            .lastSyncedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private Transaction createTestTransaction() {
        return Transaction.builder()
            .txnId(1)
            .type(TransactionType.CAPTURE_FARE)
            .direction(TransactionDirection.IN)
            .actorKind(ActorKind.USER)
            .actorUser(testUser)
            .amount(new BigDecimal("50000"))
            .currency("VND")
//            .bookingId(1)
            .status(TransactionStatus.SUCCESS)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private TopUpInitRequest createTestTopUpRequest() {
        TopUpInitRequest request = new TopUpInitRequest();
        request.setAmount(new BigDecimal("100000"));
        request.setPaymentMethod("PAYOS");
        request.setReturnUrl("https://app.example.com/wallet/callback");
        request.setCancelUrl("https://app.example.com/wallet/cancel");
        return request;
    }

    private PayoutInitRequest createTestPayoutRequest() {
        PayoutInitRequest request = new PayoutInitRequest();
        request.setAmount(new BigDecimal("50000"));
        request.setBankAccountNumber("1234567890");
        request.setBankName("Vietcombank");
        request.setAccountHolderName("NGUYEN VAN A");
        return request;
    }

    private CheckoutResponseData createMockCheckoutData() {
        // Create a mock CheckoutResponseData since constructor is not accessible
        CheckoutResponseData mockData = mock(CheckoutResponseData.class);
        when(mockData.getOrderCode()).thenReturn(123456L);
        when(mockData.getCheckoutUrl()).thenReturn("https://payos.vn/checkout/123456");
        when(mockData.getQrCode()).thenReturn("https://api.qrserver.com/qr/123456");
        return mockData;
    }

    private static Stream<BigDecimal> amountProvider() {
        return Stream.of(
            new BigDecimal("1000"),
            new BigDecimal("50000"),
            new BigDecimal("100000"),
            new BigDecimal("500000"),
            new BigDecimal("1000000")
        );
    }
}

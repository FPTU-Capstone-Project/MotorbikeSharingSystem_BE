package com.mssus.app.service.impl;

import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.response.wallet.DriverEarningsResponse;
import com.mssus.app.dto.response.wallet.WalletResponse;
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
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Balance Calculation SSOT Tests")
class WalletServiceBalanceSSOTTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BalanceCalculationService balanceCalculationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User testUser;
    private Wallet testWallet;
    private static final Integer USER_ID = 1;
    private static final Integer WALLET_ID = 1;
    private static final String EMAIL = "test@example.com";
    private static final BigDecimal AVAILABLE_BALANCE = BigDecimal.valueOf(500_000);
    private static final BigDecimal PENDING_BALANCE = BigDecimal.valueOf(100_000);

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .userId(USER_ID)
            .email(EMAIL)
            .build();

        testWallet = Wallet.builder()
            .walletId(WALLET_ID)
            .user(testUser)
            .totalToppedUp(BigDecimal.valueOf(1_000_000))
            .totalSpent(BigDecimal.valueOf(500_000))
            .isActive(true)
            .lastSyncedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        lenient().when(authentication.getName()).thenReturn(EMAIL);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));
        lenient().when(walletRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(testWallet));
        lenient().when(balanceCalculationService.calculateAvailableBalance(WALLET_ID))
            .thenReturn(AVAILABLE_BALANCE);
        lenient().when(balanceCalculationService.calculatePendingBalance(WALLET_ID))
            .thenReturn(PENDING_BALANCE);
    }

    @Test
    @DisplayName("Should get balance from ledger (SSOT)")
    void should_getBalance_fromLedger() {
        // When
        WalletResponse response = walletService.getBalance(authentication);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getWalletId()).isEqualTo(WALLET_ID);
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getAvailableBalance()).isEqualTo(AVAILABLE_BALANCE);
        assertThat(response.getPendingBalance()).isEqualTo(PENDING_BALANCE);
        assertThat(response.getTotalToppedUp()).isEqualTo(testWallet.getTotalToppedUp());
        assertThat(response.getTotalSpent()).isEqualTo(testWallet.getTotalSpent());

        // ✅ SSOT: Verify balance is calculated from ledger, not from wallet entity
        verify(balanceCalculationService).calculateAvailableBalance(WALLET_ID);
        verify(balanceCalculationService).calculatePendingBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Should check sufficient balance from ledger")
    void should_checkSufficientBalance_fromLedger() {
        // Given
        BigDecimal requiredAmount = BigDecimal.valueOf(300_000);

        // When
        boolean hasFunds = walletService.hasSufficientBalance(USER_ID, requiredAmount);

        // Then
        assertThat(hasFunds).isTrue(); // 500_000 >= 300_000

        // ✅ SSOT: Verify balance is calculated from ledger
        verify(balanceCalculationService).calculateAvailableBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Should return false when insufficient balance")
    void should_returnFalse_whenInsufficientBalance() {
        // Given
        BigDecimal requiredAmount = BigDecimal.valueOf(600_000);

        // When
        boolean hasFunds = walletService.hasSufficientBalance(USER_ID, requiredAmount);

        // Then
        assertThat(hasFunds).isFalse(); // 500_000 < 600_000

        verify(balanceCalculationService).calculateAvailableBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Should get driver earnings with balance from ledger")
    void should_getDriverEarnings_withBalanceFromLedger() {
        // Given
        Transaction earningTxn = Transaction.builder()
            .txnId(1)
            .amount(BigDecimal.valueOf(50_000))
            .createdAt(LocalDateTime.now())
            .build();

        when(transactionRepository.findByActorUserIdOrderByCreatedAtDesc(USER_ID))
            .thenReturn(List.of(earningTxn));

        // When
        DriverEarningsResponse response = walletService.getDriverEarnings(authentication);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAvailableBalance()).isEqualTo(AVAILABLE_BALANCE);
        assertThat(response.getPendingEarnings()).isEqualTo(PENDING_BALANCE);

        // ✅ SSOT: Verify balance is calculated from ledger
        verify(balanceCalculationService).calculateAvailableBalance(WALLET_ID);
        verify(balanceCalculationService).calculatePendingBalance(WALLET_ID);
    }

    @Test
    @DisplayName("Should throw exception when wallet not found")
    void should_throwException_whenWalletNotFound() {
        // Given
        when(walletRepository.findByUser_UserId(USER_ID))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> walletService.getBalance(authentication))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Không tìm thấy ví");
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void should_throwException_whenUserNotFound() {
        // Given
        when(userRepository.findByEmail(EMAIL))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> walletService.getBalance(authentication))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Không tìm thấy người dùng");
    }

    @Test
    @DisplayName("Should throw exception when authentication is null")
    void should_throwException_whenAuthenticationIsNull() {
        // When/Then
        assertThatThrownBy(() -> walletService.getBalance(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Xác thực không được để trống");
    }

    @Test
    @DisplayName("Should throw exception when amount is negative")
    void should_throwException_whenAmountIsNegative() {
        // Given
        BigDecimal negativeAmount = BigDecimal.valueOf(-100);

        // When/Then
        assertThatThrownBy(() -> walletService.hasSufficientBalance(USER_ID, negativeAmount))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Số tiền phải không âm");
    }
}


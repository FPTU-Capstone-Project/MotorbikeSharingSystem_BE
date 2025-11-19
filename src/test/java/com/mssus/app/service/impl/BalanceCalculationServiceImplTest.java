package com.mssus.app.service.impl;

import com.mssus.app.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceCalculationService Tests - SSOT")
class BalanceCalculationServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BalanceCalculationServiceImpl balanceCalculationService;

    private static final Integer WALLET_ID = 1;
    private static final BigDecimal AVAILABLE_BALANCE = BigDecimal.valueOf(500_000);
    private static final BigDecimal PENDING_BALANCE = BigDecimal.valueOf(100_000);

    @BeforeEach
    void setUp() {
        when(transactionRepository.calculateAvailableBalance(WALLET_ID))
            .thenReturn(AVAILABLE_BALANCE);
        when(transactionRepository.calculatePendingBalance(WALLET_ID))
            .thenReturn(PENDING_BALANCE);
    }

    @Test
    @DisplayName("Should calculate total balance (available + pending)")
    void should_calculateTotalBalance() {
        // When
        BigDecimal result = balanceCalculationService.calculateTotalBalance(WALLET_ID);

        // Then
        BigDecimal expected = AVAILABLE_BALANCE.add(PENDING_BALANCE);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return zero when repository returns null")
    void should_returnZero_whenRepositoryReturnsNull() {
        // Given
        when(transactionRepository.calculateAvailableBalance(WALLET_ID)).thenReturn(null);
        when(transactionRepository.calculatePendingBalance(WALLET_ID)).thenReturn(null);

        // When
        BigDecimal available = balanceCalculationService.calculateAvailableBalance(WALLET_ID);
        BigDecimal pending = balanceCalculationService.calculatePendingBalance(WALLET_ID);
        BigDecimal total = balanceCalculationService.calculateTotalBalance(WALLET_ID);

        // Then
        assertThat(available).isEqualTo(BigDecimal.ZERO);
        assertThat(pending).isEqualTo(BigDecimal.ZERO);
        assertThat(total).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle zero balance correctly")
    void should_handleZeroBalance() {
        // Given
        when(transactionRepository.calculateAvailableBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.calculatePendingBalance(WALLET_ID))
            .thenReturn(BigDecimal.ZERO);

        // When
        BigDecimal available = balanceCalculationService.calculateAvailableBalance(WALLET_ID);
        BigDecimal pending = balanceCalculationService.calculatePendingBalance(WALLET_ID);
        BigDecimal total = balanceCalculationService.calculateTotalBalance(WALLET_ID);

        // Then
        assertThat(available).isEqualTo(BigDecimal.ZERO);
        assertThat(pending).isEqualTo(BigDecimal.ZERO);
        assertThat(total).isEqualTo(BigDecimal.ZERO);
    }
}


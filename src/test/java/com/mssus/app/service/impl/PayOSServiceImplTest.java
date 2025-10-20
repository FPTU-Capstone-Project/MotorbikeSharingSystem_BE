package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.entity.IdempotencyKey;
import com.mssus.app.repository.IdempotencyKeyRepository;
import com.mssus.app.service.TransactionService;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.PaymentData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PayOSServiceImplTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Spy
    @InjectMocks
    private PayOSServiceImpl service;

    private PayOS payOS;

    @BeforeEach
    void setUp() throws Exception {
        payOS = org.mockito.Mockito.mock(PayOS.class);
        ReflectionTestUtils.setField(service, "payOS", payOS);
        ReflectionTestUtils.setField(service, "clientId", "client");
        ReflectionTestUtils.setField(service, "apiKey", "api");
        ReflectionTestUtils.setField(service, "checksumKey", "checksum");
    }

    @Test
    void should_returnCheckoutResponse_when_createTopUpPaymentLinkSuccess() throws Exception {
        doReturn(20240101L).when(service).generateUniqueOrderCode();
        BigDecimal amount = BigDecimal.valueOf(150_000);
        CheckoutResponseData responseData = org.mockito.Mockito.mock(CheckoutResponseData.class);

        doReturn(Optional.empty()).when(idempotencyKeyRepository).findByKeyHash("20240101");
        doReturn(responseData).when(payOS).createPaymentLink(any(PaymentData.class));

        CheckoutResponseData result = service.createTopUpPaymentLink(
            99,
            amount,
            "Top up wallet",
            "https://return",
            "https://cancel"
        );

        assertThat(result).isSameAs(responseData);

        ArgumentCaptor<PaymentData> paymentCaptor = ArgumentCaptor.forClass(PaymentData.class);
        verify(payOS).createPaymentLink(paymentCaptor.capture());
        PaymentData paymentData = paymentCaptor.getValue();
        assertThat(paymentData.getOrderCode()).isEqualTo(20240101L);
        assertThat(paymentData.getAmount()).isEqualTo(amount.intValue());
        assertThat(paymentData.getDescription()).isEqualTo("Top up wallet");
        assertThat(paymentData.getReturnUrl()).isEqualTo("https://return");
        assertThat(paymentData.getCancelUrl()).isEqualTo("https://cancel");

        verify(transactionService).initTopup(99, amount, "20240101", "Top up wallet");

        ArgumentCaptor<IdempotencyKey> keyCaptor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository).save(keyCaptor.capture());
        IdempotencyKey savedKey = keyCaptor.getValue();
        assertThat(savedKey.getKeyHash()).isEqualTo("20240101");
        assertThat(savedKey.getReference()).isEqualTo("20240101");
        assertThat(savedKey.getCreatedAt()).isInstanceOf(LocalDateTime.class);

        verify(idempotencyKeyRepository).findByKeyHash("20240101");
        verifyNoMoreInteractions(payOS, idempotencyKeyRepository, transactionService);
    }

    static Stream<Arguments> invalidAmountProvider() {
        return Stream.of(
            Arguments.of(null, "null amount"),
            Arguments.of(BigDecimal.ZERO, "zero amount"),
            Arguments.of(BigDecimal.valueOf(-10), "negative amount")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidAmountProvider")
    void should_throwRuntimeException_when_amountInvalid(BigDecimal amount, String description) throws Exception {
        doReturn(555L).when(service).generateUniqueOrderCode();

        assertThatThrownBy(() ->
            service.createTopUpPaymentLink(1, amount, "desc", "return", "cancel"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Error creating top-up payment link for user");

        verifyNoInteractions(payOS, idempotencyKeyRepository, transactionService);
    }

    @Test
    void should_throwRuntimeException_when_duplicateOrderCodeDetected() throws Exception {
        doReturn(789L).when(service).generateUniqueOrderCode();
        doReturn(Optional.of(IdempotencyKey.builder().keyHash("789").build()))
            .when(idempotencyKeyRepository).findByKeyHash("789");

        assertThatThrownBy(() ->
            service.createTopUpPaymentLink(5, BigDecimal.TEN, "duplicate", "return", "cancel"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Error creating top-up payment link for user");

        verify(idempotencyKeyRepository).findByKeyHash("789");
        verifyNoMoreInteractions(idempotencyKeyRepository);
        verifyNoInteractions(payOS, transactionService);
    }

    @Test
    void should_throwRuntimeException_when_payOsCallFails() throws Exception {
        doReturn(8080L).when(service).generateUniqueOrderCode();
        doReturn(Optional.empty()).when(idempotencyKeyRepository).findByKeyHash("8080");
        doThrow(new IllegalStateException("PayOS failure"))
            .when(payOS).createPaymentLink(any(PaymentData.class));

        assertThatThrownBy(() ->
            service.createTopUpPaymentLink(10, BigDecimal.valueOf(500_000), "desc", "return", "cancel"))
            .isInstanceOf(RuntimeException.class)
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Error creating top-up payment link for user");

        verify(idempotencyKeyRepository).findByKeyHash("8080");
        verify(payOS).createPaymentLink(any(PaymentData.class));
        verifyNoMoreInteractions(payOS, idempotencyKeyRepository);
        verifyNoInteractions(transactionService);
    }

    @Test
    void should_handleSuccessWebhook_when_statusPaid() throws Exception {
        JsonNode payloadNode = new ObjectMapper().readTree("""
            {
              "data": {
                "orderCode": "12345",
                "status": "PAID",
                "description": "Wallet topup"
              }
            }
            """);
        lenient().when(objectMapper.readTree(any(String.class))).thenReturn(payloadNode);

        service.handleWebhook("{\"payload\":\"value\"}");

        verify(objectMapper).readTree("{\"payload\":\"value\"}");
        verify(transactionService).handleTopupSuccess("12345");
        verifyNoMoreInteractions(transactionService);
    }

    @ParameterizedTest
    @MethodSource("failedStatusProvider")
    void should_handleFailureWebhook_when_statusCancelledOrExpired(String status) throws Exception {
        JsonNode payloadNode = new ObjectMapper().readTree("""
            {
              "data": {
                "orderCode": "999",
                "status": "%s"
              }
            }
            """.formatted(status));
        lenient().when(objectMapper.readTree(any(String.class))).thenReturn(payloadNode);

        service.handleWebhook("{}");

        verify(transactionService).handleTopupFailed("999", "Payment " + status.toLowerCase());
        verifyNoMoreInteractions(transactionService);
    }

    static Stream<String> failedStatusProvider() {
        return Stream.of("CANCELLED", "EXPIRED");
    }

    @Test
    void should_notInteract_when_statusUnknown() throws Exception {
        JsonNode payloadNode = new ObjectMapper().readTree("""
            {
              "data": {
                "orderCode": "444",
                "status": "UNKNOWN"
              }
            }
            """);
        lenient().when(objectMapper.readTree(any(String.class))).thenReturn(payloadNode);

        service.handleWebhook("{}");

        verify(transactionService, never()).handleTopupSuccess(any());
        verify(transactionService, never()).handleTopupFailed(any(), any());
        verify(objectMapper).readTree("{}");
    }

    @Test
    void should_throwRuntimeException_when_payloadParsingFails() throws Exception {
        doThrow(new IllegalArgumentException("invalid json"))
            .when(objectMapper).readTree(any(String.class));

        assertThatThrownBy(() -> service.handleWebhook("invalid"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Error processing webhook")
            .hasRootCauseInstanceOf(IllegalArgumentException.class);

        verify(objectMapper).readTree("invalid");
        verifyNoInteractions(transactionService);
    }
}

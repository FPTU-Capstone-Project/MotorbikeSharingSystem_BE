package com.mssus.app.controller;

import com.mssus.app.dto.request.refund.CreateRefundRequestDto;
import com.mssus.app.dto.response.refund.RefundRequestResponseDto;
import com.mssus.app.service.RefundService;
import com.mssus.app.common.enums.RefundStatus;
import com.mssus.app.common.enums.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RefundController refundController;

    @Test
    void createRefundRequest_ShouldReturnCreatedResponse() {
        // Arrange
        CreateRefundRequestDto request = CreateRefundRequestDto.builder()
                .bookingId(456)
                .transactionId(789)
                .refundType(TransactionType.CAPTURE_FARE)
                .amount(new BigDecimal("50000"))
                .reason("Service quality issue")
                .build();

        RefundRequestResponseDto expectedResponse = RefundRequestResponseDto.builder()
                .refundRequestId(123)
                .refundRequestUuid(UUID.randomUUID())
                .userId(456)
                .bookingId(456)
                .transactionId(789)
                .refundType(TransactionType.CAPTURE_FARE)
                .amount(new BigDecimal("50000"))
                .currency("VND")
                .status(RefundStatus.PENDING)
                .reason("Service quality issue")
                .requestedByUserId(456)
                .createdAt(LocalDateTime.now())
                .build();

        when(authentication.getName()).thenReturn("user@example.com");
        when(refundService.createRefundRequest(any(CreateRefundRequestDto.class), any(Authentication.class)))
                .thenReturn(expectedResponse);

        // Act
        ResponseEntity<RefundRequestResponseDto> response = refundController.createRefundRequest(request, authentication);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(123, response.getBody().getRefundRequestId());
        assertEquals(RefundStatus.PENDING, response.getBody().getStatus());
        assertEquals(new BigDecimal("50000"), response.getBody().getAmount());

        verify(refundService).createRefundRequest(eq(request), eq(authentication));
    }

    @Test
    void getRefundRequestById_ShouldReturnOkResponse() {
        // Arrange
        Integer refundRequestId = 123;
        RefundRequestResponseDto expectedResponse = RefundRequestResponseDto.builder()
                .refundRequestId(refundRequestId)
                .status(RefundStatus.PENDING)
                .amount(new BigDecimal("50000"))
                .build();

        when(authentication.getName()).thenReturn("user@example.com");
        when(refundService.getRefundRequestById(refundRequestId))
                .thenReturn(expectedResponse);

        // Act
        ResponseEntity<RefundRequestResponseDto> response = refundController.getRefundRequestById(refundRequestId, authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(refundRequestId, response.getBody().getRefundRequestId());

        verify(refundService).getRefundRequestById(refundRequestId);
    }

    @Test
    void cancelRefundRequest_ShouldReturnOkResponse() {
        // Arrange
        Integer refundRequestId = 123;
        RefundRequestResponseDto expectedResponse = RefundRequestResponseDto.builder()
                .refundRequestId(refundRequestId)
                .status(RefundStatus.CANCELLED)
                .build();

        when(authentication.getName()).thenReturn("user@example.com");
        when(refundService.cancelRefundRequest(refundRequestId, authentication))
                .thenReturn(expectedResponse);

        // Act
        ResponseEntity<RefundRequestResponseDto> response = refundController.cancelRefundRequest(refundRequestId, authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(refundRequestId, response.getBody().getRefundRequestId());
        assertEquals(RefundStatus.CANCELLED, response.getBody().getStatus());

        verify(refundService).cancelRefundRequest(refundRequestId, authentication);
    }

    @Test
    void getPendingRefundCount_ShouldReturnOkResponse() {
        // Arrange
        long expectedCount = 25L;
        when(authentication.getName()).thenReturn("admin@example.com");
        when(refundService.getPendingRefundCount()).thenReturn(expectedCount);

        // Act
        ResponseEntity<Long> response = refundController.getPendingRefundCount(authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedCount, response.getBody());

        verify(refundService).getPendingRefundCount();
    }
}






package com.mssus.app.mapper;

import com.mssus.app.dto.response.refund.RefundRequestResponseDto;
import com.mssus.app.entity.RefundRequest;
import org.springframework.stereotype.Component;

@Component
public class RefundRequestMapper {

    public RefundRequestResponseDto mapToRefundRequestResponseDto(RefundRequest refundRequest) {
        if (refundRequest == null) {
            return null;
        }

        return RefundRequestResponseDto.builder()
                .refundRequestId(refundRequest.getRefundRequestId())
                .refundRequestUuid(refundRequest.getRefundRequestUuid())
                .userId(refundRequest.getUser() != null ? refundRequest.getUser().getUserId() : null)
                .bookingId(refundRequest.getBookingId())
                .transactionId(refundRequest.getTransactionId())
                .refundType(refundRequest.getRefundType())
                .amount(refundRequest.getAmount())
                .currency(refundRequest.getCurrency())
                .status(refundRequest.getStatus())
                .reason(refundRequest.getReason())
                .requestedByUserId(refundRequest.getRequestedByUser() != null ? 
                    refundRequest.getRequestedByUser().getUserId() : null)
                .reviewedByUserId(refundRequest.getReviewedByUser() != null ? 
                    refundRequest.getReviewedByUser().getUserId() : null)
                .reviewNotes(refundRequest.getReviewNotes())
                .pspRef(refundRequest.getPspRef())
                .createdAt(refundRequest.getCreatedAt())
                .updatedAt(refundRequest.getUpdatedAt())
                .reviewedAt(refundRequest.getReviewedAt())
                .completedAt(refundRequest.getCompletedAt())
                .build();
    }
}






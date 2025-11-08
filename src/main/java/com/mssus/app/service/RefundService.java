package com.mssus.app.service;

import com.mssus.app.dto.request.refund.ApproveRefundRequestDto;
import com.mssus.app.dto.request.refund.CreateRefundRequestDto;
import com.mssus.app.dto.request.refund.RejectRefundRequestDto;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.refund.RefundRequestResponseDto;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface RefundService {
    
    /**
     * Create a new refund request for admin review
     */
    RefundRequestResponseDto createRefundRequest(CreateRefundRequestDto request, Authentication authentication);
    
    /**
     * Get all pending refund requests (for admin dashboard)
     */
    PageResponse<RefundRequestResponseDto> getPendingRefundRequests(Pageable pageable);
    
    /**
     * Get all approved refund requests (for processing)
     */
    PageResponse<RefundRequestResponseDto> getApprovedRefundRequests(Pageable pageable);
    
    /**
     * Get refund requests by status
     */
    PageResponse<RefundRequestResponseDto> getRefundRequestsByStatus(String status, Pageable pageable);
    
    /**
     * Get refund requests for a specific user
     */
    PageResponse<RefundRequestResponseDto> getUserRefundRequests(Integer userId, Pageable pageable);
    
    /**
     * Get a specific refund request by ID
     */
    RefundRequestResponseDto getRefundRequestById(Integer refundRequestId);
    
    /**
     * Approve a refund request by staff/admin
     */
    RefundRequestResponseDto approveRefundRequest(ApproveRefundRequestDto request, Authentication authentication);
    
    /**
     * Reject a refund request by staff/admin
     */
    RefundRequestResponseDto rejectRefundRequest(RejectRefundRequestDto request, Authentication authentication);
    
    /**
     * Mark a refund as completed after successful processing
     */
    RefundRequestResponseDto markRefundAsCompleted(Integer refundRequestId);
    
    /**
     * Mark a refund as failed
     */
    RefundRequestResponseDto markRefundAsFailed(Integer refundRequestId, String failureReason);
    
    /**
     * Get count of pending refunds
     */
    long getPendingRefundCount();
    
    /**
     * Cancel a refund request (user cancellation)
     */
    RefundRequestResponseDto cancelRefundRequest(Integer refundRequestId, Authentication authentication);
}






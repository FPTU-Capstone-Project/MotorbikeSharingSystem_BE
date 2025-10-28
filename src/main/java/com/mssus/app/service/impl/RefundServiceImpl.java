package com.mssus.app.service.impl;

import com.mssus.app.common.enums.RefundStatus;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.refund.ApproveRefundRequestDto;
import com.mssus.app.dto.request.refund.CreateRefundRequestDto;
import com.mssus.app.dto.request.refund.RejectRefundRequestDto;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.refund.RefundRequestResponseDto;
import com.mssus.app.entity.RefundRequest;
import com.mssus.app.entity.User;
import com.mssus.app.mapper.RefundRequestMapper;
import com.mssus.app.repository.RefundRequestRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.RefundService;
import com.mssus.app.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final RefundRequestMapper refundRequestMapper;

    @Override
    @Transactional
    public RefundRequestResponseDto createRefundRequest(CreateRefundRequestDto request, Authentication authentication) {
        validateCreateRefundRequest(request);

        User requestedByUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found: " + authentication.getName()));

        User refundUser = userRepository.findById(request.getBookingId())
                .orElseThrow(() -> new NotFoundException("User not found for booking: " + request.getBookingId()));

        // Check if a refund request already exists for this booking
        if (refundRequestRepository.findByBookingId(request.getBookingId()).isPresent()) {
            throw new ValidationException("A refund request already exists for this booking");
        }

        RefundRequest refundRequest = RefundRequest.builder()
                .refundRequestUuid(UUID.randomUUID())
                .user(refundUser)
                .bookingId(request.getBookingId())
                .transactionId(request.getTransactionId())
                .refundType(request.getRefundType())
                .amount(request.getAmount())
                .status(RefundStatus.PENDING)
                .reason(request.getReason())
                .requestedByUser(requestedByUser)
                .pspRef(request.getPspRef())
                .build();

        RefundRequest savedRefundRequest = refundRequestRepository.save(refundRequest);

        log.info("Created refund request - ID: {}, BookingID: {}, Amount: {}, Status: PENDING",
                savedRefundRequest.getRefundRequestId(), savedRefundRequest.getBookingId(), savedRefundRequest.getAmount());

        return refundRequestMapper.mapToRefundRequestResponseDto(savedRefundRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponseDto> getPendingRefundRequests(Pageable pageable) {
        Page<RefundRequest> page = refundRequestRepository.findByStatus(RefundStatus.PENDING, pageable);
        return buildPageResponse(page, page.getContent().stream()
                .map(refundRequestMapper::mapToRefundRequestResponseDto)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponseDto> getApprovedRefundRequests(Pageable pageable) {
        Page<RefundRequest> page = refundRequestRepository.findByStatus(RefundStatus.APPROVED, pageable);
        return buildPageResponse(page, page.getContent().stream()
                .map(refundRequestMapper::mapToRefundRequestResponseDto)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponseDto> getRefundRequestsByStatus(String status, Pageable pageable) {
        validateStatusEnum(status);
        RefundStatus refundStatus = RefundStatus.valueOf(status.toUpperCase());

        Page<RefundRequest> page = refundRequestRepository.findByStatus(refundStatus, pageable);
        return buildPageResponse(page, page.getContent().stream()
                .map(refundRequestMapper::mapToRefundRequestResponseDto)
                .collect(Collectors.toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponseDto> getUserRefundRequests(Integer userId, Pageable pageable) {
        return  null;
    }

    @Override
    @Transactional(readOnly = true)
    public RefundRequestResponseDto getRefundRequestById(Integer refundRequestId) {
        if (refundRequestId == null) {
            throw new ValidationException("Refund request ID cannot be null");
        }

        RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new NotFoundException("Refund request not found: " + refundRequestId));

        return refundRequestMapper.mapToRefundRequestResponseDto(refundRequest);
    }

    @Override
    @Transactional
    public RefundRequestResponseDto approveRefundRequest(ApproveRefundRequestDto request, Authentication authentication) {
        validateApproveRequest(request);

        User reviewedByUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found: " + authentication.getName()));

        RefundRequest refundRequest = refundRequestRepository.findById(request.getRefundRequestId())
                .orElseThrow(() -> new NotFoundException("Refund request not found: " + request.getRefundRequestId()));

        // Validate current status
        if (refundRequest.getStatus() != RefundStatus.PENDING) {
            throw new ValidationException("Cannot approve refund request with status: " + refundRequest.getStatus());
        }

        // Update refund request status to APPROVED
        refundRequest.setStatus(RefundStatus.APPROVED);
        refundRequest.setReviewedByUser(reviewedByUser);
        refundRequest.setReviewNotes(request.getReviewNotes());
        refundRequest.setReviewedAt(LocalDateTime.now());
        if (request.getPspRef() != null) {
            refundRequest.setPspRef(request.getPspRef());
        }

        RefundRequest savedRefundRequest = refundRequestRepository.save(refundRequest);

        log.info("Approved refund request - ID: {}, BookingID: {}, Amount: {}, ReviewedBy: {}",
                savedRefundRequest.getRefundRequestId(), savedRefundRequest.getBookingId(),
                savedRefundRequest.getAmount(), reviewedByUser.getUserId());

        return refundRequestMapper.mapToRefundRequestResponseDto(savedRefundRequest);
    }

    @Override
    @Transactional
    public RefundRequestResponseDto rejectRefundRequest(RejectRefundRequestDto request, Authentication authentication) {
        validateRejectRequest(request);

        User reviewedByUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found: " + authentication.getName()));

        RefundRequest refundRequest = refundRequestRepository.findById(request.getRefundRequestId())
                .orElseThrow(() -> new NotFoundException("Refund request not found: " + request.getRefundRequestId()));

        // Validate current status
        if (refundRequest.getStatus() != RefundStatus.PENDING) {
            throw new ValidationException("Cannot reject refund request with status: " + refundRequest.getStatus());
        }

        // Update refund request status to REJECTED
        refundRequest.setStatus(RefundStatus.REJECTED);
        refundRequest.setReviewedByUser(reviewedByUser);
        refundRequest.setReviewNotes(request.getRejectionReason());
        refundRequest.setReviewedAt(LocalDateTime.now());

        RefundRequest savedRefundRequest = refundRequestRepository.save(refundRequest);

        log.info("Rejected refund request - ID: {}, BookingID: {}, Amount: {}, ReviewedBy: {}, Reason: {}",
                savedRefundRequest.getRefundRequestId(), savedRefundRequest.getBookingId(),
                savedRefundRequest.getAmount(), reviewedByUser.getUserId(), request.getRejectionReason());

        return refundRequestMapper.mapToRefundRequestResponseDto(savedRefundRequest);
    }

    @Override
    @Transactional
    public RefundRequestResponseDto markRefundAsCompleted(Integer refundRequestId) {
        if (refundRequestId == null) {
            throw new ValidationException("Refund request ID cannot be null");
        }

        RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new NotFoundException("Refund request not found: " + refundRequestId));

        if (refundRequest.getStatus() != RefundStatus.APPROVED) {
            throw new ValidationException("Only approved refunds can be marked as completed");
        }

        refundRequest.setStatus(RefundStatus.COMPLETED);
        refundRequest.setCompletedAt(LocalDateTime.now());

        RefundRequest savedRefundRequest = refundRequestRepository.save(refundRequest);

        log.info("Marked refund as completed - ID: {}, BookingID: {}, Amount: {}",
                savedRefundRequest.getRefundRequestId(), savedRefundRequest.getBookingId(), savedRefundRequest.getAmount());

        return refundRequestMapper.mapToRefundRequestResponseDto(savedRefundRequest);
    }

    @Override
    @Transactional
    public RefundRequestResponseDto markRefundAsFailed(Integer refundRequestId, String failureReason) {
        if (refundRequestId == null) {
            throw new ValidationException("Refund request ID cannot be null");
        }
        if (failureReason == null || failureReason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
        }

        RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new NotFoundException("Refund request not found: " + refundRequestId));

        if (refundRequest.getStatus() != RefundStatus.APPROVED) {
            throw new ValidationException("Only approved refunds can be marked as failed");
        }

        refundRequest.setStatus(RefundStatus.FAILED);
        refundRequest.setReviewNotes(refundRequest.getReviewNotes() + " | Failed: " + failureReason);

        RefundRequest savedRefundRequest = refundRequestRepository.save(refundRequest);

        log.info("Marked refund as failed - ID: {}, BookingID: {}, Amount: {}, Reason: {}",
                savedRefundRequest.getRefundRequestId(), savedRefundRequest.getBookingId(),
                savedRefundRequest.getAmount(), failureReason);

        return refundRequestMapper.mapToRefundRequestResponseDto(savedRefundRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public long getPendingRefundCount() {
        return refundRequestRepository.countPendingRefunds();
    }

    @Override
    @Transactional
    public RefundRequestResponseDto cancelRefundRequest(Integer refundRequestId, Authentication authentication) {
        if (refundRequestId == null) {
            throw new ValidationException("Refund request ID cannot be null");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found: " + authentication.getName()));

        RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new NotFoundException("Refund request not found: " + refundRequestId));

        // Only the user who created the request or admin can cancel
        if (!refundRequest.getRequestedByUser().getUserId().equals(user.getUserId())) {
            throw new ValidationException("Only the request creator can cancel this refund");
        }

        // Can only cancel PENDING or APPROVED requests
        if (refundRequest.getStatus() != RefundStatus.PENDING && refundRequest.getStatus() != RefundStatus.APPROVED) {
            throw new ValidationException("Cannot cancel refund request with status: " + refundRequest.getStatus());
        }

        refundRequest.setStatus(RefundStatus.CANCELLED);
        RefundRequest savedRefundRequest = refundRequestRepository.save(refundRequest);

        log.info("Cancelled refund request - ID: {}, BookingID: {}, CancelledBy: {}",
                savedRefundRequest.getRefundRequestId(), savedRefundRequest.getBookingId(), user.getUserId());

        return refundRequestMapper.mapToRefundRequestResponseDto(savedRefundRequest);
    }

    // Private validation methods
    private void validateCreateRefundRequest(CreateRefundRequestDto request) {
        if (request.getBookingId() == null) {
            throw new ValidationException("Booking ID is required");
        }
        if (request.getTransactionId() == null) {
            throw new ValidationException("Transaction ID is required");
        }
        if (request.getRefundType() == null) {
            throw new ValidationException("Refund type is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new ValidationException("Refund reason is required");
        }
    }

    private void validateApproveRequest(ApproveRefundRequestDto request) {
        if (request.getRefundRequestId() == null) {
            throw new ValidationException("Refund request ID is required");
        }
    }

    private void validateRejectRequest(RejectRefundRequestDto request) {
        if (request.getRefundRequestId() == null) {
            throw new ValidationException("Refund request ID is required");
        }
        if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
            throw new ValidationException("Rejection reason is required");
        }
    }

    /**
     * Validates if the provided status string is a valid RefundStatus enum value
     * @param status The status string to validate
     * @throws ValidationException if the status is null, empty, or not a valid RefundStatus
     */
    private void validateStatusEnum(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new ValidationException("Status cannot be null or empty");
        }

        try {
            RefundStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    String.format("Invalid status: '%s'. Valid statuses are: %s", 
                            status, 
                            String.join(", ", getValidRefundStatuses()))
            );
        }
    }

    /**
     * Helper method to get all valid RefundStatus enum values as strings
     * @return Array of valid status names
     */
    private String[] getValidRefundStatuses() {
        return java.util.Arrays.stream(RefundStatus.values())
                .map(Enum::name)
                .toArray(String[]::new);
    }

    private <T> PageResponse<T> buildPageResponse(Page<?> page, List<T> content) {
        return PageResponse.<T>builder()
                .data(content)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(page.getNumber() + 1)
                        .pageSize(page.getSize())
                        .totalPages(page.getTotalPages())
                        .totalRecords(page.getTotalElements())
                        .build())
                .build();
    }
}

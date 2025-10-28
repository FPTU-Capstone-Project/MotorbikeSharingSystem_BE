package com.mssus.app.controller;

import com.mssus.app.dto.request.refund.ApproveRefundRequestDto;
import com.mssus.app.dto.request.refund.CreateRefundRequestDto;
import com.mssus.app.dto.request.refund.RejectRefundRequestDto;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.refund.RefundRequestResponseDto;
import com.mssus.app.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
@Tag(name = "Refund Management", description = "Refund request management and processing operations")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {

    private final RefundService refundService;

    // ========== USER ENDPOINTS ==========

    @Operation(
            summary = "Create refund request",
            description = "Create a new refund request for admin review. Users can request refunds for various transaction types."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Refund request created successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Refund request already exists for this booking")
    })
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RefundRequestResponseDto> createRefundRequest(
            @Valid @RequestBody CreateRefundRequestDto request,
            Authentication authentication) {
        
        log.info("Create refund request - User: {}, BookingID: {}, Amount: {}, Type: {}", 
                authentication.getName(), request.getBookingId(), request.getAmount(), request.getRefundType());
        
        RefundRequestResponseDto response = refundService.createRefundRequest(request, authentication);
        
        log.info("Refund request created successfully - ID: {}, Status: {}", 
                response.getRefundRequestId(), response.getStatus());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get user refund requests",
            description = "Retrieve refund requests for the authenticated user with pagination"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/requests/my")
    public ResponseEntity<PageResponse<RefundRequestResponseDto>> getMyRefundRequests(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        log.info("Get my refund requests - User: {}, Page: {}, Size: {}", 
                authentication.getName(), pageable.getPageNumber(), pageable.getPageSize());
        
        // Extract user ID from authentication (assuming email is stored in authentication.getName())
        // This would need to be implemented based on your authentication mechanism
        Integer userId = extractUserIdFromAuth(authentication);
        PageResponse<RefundRequestResponseDto> response = refundService.getUserRefundRequests(userId, pageable);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get refund request details",
            description = "Get detailed information about a specific refund request"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund request details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Refund request not found")
    })
    @GetMapping("/requests/{refundRequestId}")
    public ResponseEntity<RefundRequestResponseDto> getRefundRequestById(
            @Parameter(description = "Refund request ID") @PathVariable Integer refundRequestId,
            Authentication authentication) {
        
        log.info("Get refund request details - ID: {}, User: {}", refundRequestId, authentication.getName());
        
        RefundRequestResponseDto response = refundService.getRefundRequestById(refundRequestId);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Cancel refund request",
            description = "Cancel a pending or approved refund request. Only the request creator can cancel."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund request cancelled successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Cannot cancel refund request in current status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only request creator can cancel"),
            @ApiResponse(responseCode = "404", description = "Refund request not found")
    })
    @PostMapping("/requests/{refundRequestId}/cancel")
    public ResponseEntity<RefundRequestResponseDto> cancelRefundRequest(
            @Parameter(description = "Refund request ID") @PathVariable Integer refundRequestId,
            Authentication authentication) {
        
        log.info("Cancel refund request - ID: {}, User: {}", refundRequestId, authentication.getName());
        
        RefundRequestResponseDto response = refundService.cancelRefundRequest(refundRequestId, authentication);
        
        log.info("Refund request cancelled successfully - ID: {}, Status: {}", 
                response.getRefundRequestId(), response.getStatus());
        
        return ResponseEntity.ok(response);
    }

    // ========== ADMIN/STAFF ENDPOINTS ==========

    @Operation(
            summary = "Get pending refund requests",
            description = "Retrieve all pending refund requests for admin review. Requires ADMIN or STAFF role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending refund requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Staff role required")
    })
    @GetMapping("/requests/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<PageResponse<RefundRequestResponseDto>> getPendingRefundRequests(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        
        log.info("Get pending refund requests - User: {}, Page: {}, Size: {}", 
                authentication.getName(), pageable.getPageNumber(), pageable.getPageSize());
        
        PageResponse<RefundRequestResponseDto> response = refundService.getPendingRefundRequests(pageable);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get approved refund requests",
            description = "Retrieve all approved refund requests ready for processing. Requires ADMIN or STAFF role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Approved refund requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Staff role required")
    })
    @GetMapping("/requests/approved")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<PageResponse<RefundRequestResponseDto>> getApprovedRefundRequests(
            @PageableDefault(size = 20, sort = "reviewedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        log.info("Get approved refund requests - User: {}, Page: {}, Size: {}", 
                authentication.getName(), pageable.getPageNumber(), pageable.getPageSize());
        
        PageResponse<RefundRequestResponseDto> response = refundService.getApprovedRefundRequests(pageable);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get refund requests by status",
            description = "Retrieve refund requests filtered by status. Requires ADMIN or STAFF role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Staff role required")
    })
    @GetMapping("/requests/status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<PageResponse<RefundRequestResponseDto>> getRefundRequestsByStatus(
            @Parameter(description = "Refund status (PENDING, APPROVED, REJECTED, COMPLETED, FAILED, CANCELLED)") 
            @PathVariable String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        log.info("Get refund requests by status - Status: {}, User: {}, Page: {}", 
                status, authentication.getName(), pageable.getPageNumber());
        
        PageResponse<RefundRequestResponseDto> response = refundService.getRefundRequestsByStatus(status, pageable);
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Approve refund request",
            description = "Approve a pending refund request. This moves the request to APPROVED status for processing. Requires ADMIN or STAFF role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund request approved successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Cannot approve refund request in current status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Staff role required"),
            @ApiResponse(responseCode = "404", description = "Refund request not found")
    })
    @PostMapping("/requests/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<RefundRequestResponseDto> approveRefundRequest(
            @Valid @RequestBody ApproveRefundRequestDto request,
            Authentication authentication) {
        
        log.info("Approve refund request - ID: {}, User: {}, Notes: {}", 
                request.getRefundRequestId(), authentication.getName(), request.getReviewNotes());
        
        RefundRequestResponseDto response = refundService.approveRefundRequest(request, authentication);
        
        log.info("Refund request approved successfully - ID: {}, Status: {}, ReviewedBy: {}", 
                response.getRefundRequestId(), response.getStatus(), response.getReviewedByUserId());
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Reject refund request",
            description = "Reject a pending refund request with a reason. This moves the request to REJECTED status. Requires ADMIN or STAFF role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund request rejected successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Cannot reject refund request in current status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Staff role required"),
            @ApiResponse(responseCode = "404", description = "Refund request not found")
    })
    @PostMapping("/requests/reject")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<RefundRequestResponseDto> rejectRefundRequest(
            @Valid @RequestBody RejectRefundRequestDto request,
            Authentication authentication) {
        
        log.info("Reject refund request - ID: {}, User: {}, Reason: {}", 
                request.getRefundRequestId(), authentication.getName(), request.getRejectionReason());
        
        RefundRequestResponseDto response = refundService.rejectRefundRequest(request, authentication);
        
        log.info("Refund request rejected successfully - ID: {}, Status: {}, ReviewedBy: {}", 
                response.getRefundRequestId(), response.getStatus(), response.getReviewedByUserId());
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Mark refund as completed",
            description = "Mark an approved refund request as completed after successful processing. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund marked as completed successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Only approved refunds can be marked as completed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
            @ApiResponse(responseCode = "404", description = "Refund request not found")
    })
    @PostMapping("/requests/{refundRequestId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RefundRequestResponseDto> markRefundAsCompleted(
            @Parameter(description = "Refund request ID") @PathVariable Integer refundRequestId,
            Authentication authentication) {
        
        log.info("Mark refund as completed - ID: {}, User: {}", refundRequestId, authentication.getName());
        
        RefundRequestResponseDto response = refundService.markRefundAsCompleted(refundRequestId);
        
        log.info("Refund marked as completed successfully - ID: {}, Status: {}", 
                response.getRefundRequestId(), response.getStatus());
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Mark refund as failed",
            description = "Mark an approved refund request as failed with a reason. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund marked as failed successfully",
                    content = @Content(schema = @Schema(implementation = RefundRequestResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Only approved refunds can be marked as failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
            @ApiResponse(responseCode = "404", description = "Refund request not found")
    })
    @PostMapping("/requests/{refundRequestId}/failed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RefundRequestResponseDto> markRefundAsFailed(
            @Parameter(description = "Refund request ID") @PathVariable Integer refundRequestId,
            @Parameter(description = "Failure reason") @RequestParam String failureReason,
            Authentication authentication) {
        
        log.info("Mark refund as failed - ID: {}, User: {}, Reason: {}", 
                refundRequestId, authentication.getName(), failureReason);
        
        RefundRequestResponseDto response = refundService.markRefundAsFailed(refundRequestId, failureReason);
        
        log.info("Refund marked as failed successfully - ID: {}, Status: {}", 
                response.getRefundRequestId(), response.getStatus());
        
        return ResponseEntity.ok(response);
    }

    // ========== DASHBOARD/ANALYTICS ENDPOINTS ==========

    @Operation(
            summary = "Get pending refund count",
            description = "Get the count of pending refund requests for dashboard display. Requires ADMIN or STAFF role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending refund count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin/Staff role required")
    })
    @GetMapping("/requests/count/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<Long> getPendingRefundCount(Authentication authentication) {
        
        log.info("Get pending refund count - User: {}", authentication.getName());
        
        long count = refundService.getPendingRefundCount();
        
        log.info("Pending refund count: {}", count);
        
        return ResponseEntity.ok(count);
    }

    @Operation(
            summary = "Get user refund requests (Admin)",
            description = "Get refund requests for a specific user. Requires ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User refund requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/requests/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<RefundRequestResponseDto>> getUserRefundRequests(
            @Parameter(description = "User ID") @PathVariable Integer userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        log.info("Get user refund requests - UserID: {}, Admin: {}, Page: {}", 
                userId, authentication.getName(), pageable.getPageNumber());
        
        PageResponse<RefundRequestResponseDto> response = refundService.getUserRefundRequests(userId, pageable);
        
        return ResponseEntity.ok(response);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Extract user ID from authentication object
     * This method needs to be implemented based on your authentication mechanism
     */
    private Integer extractUserIdFromAuth(Authentication authentication) {
        // This is a placeholder implementation
        // You'll need to implement this based on how user information is stored in authentication
        // For example, if authentication.getName() returns email, you'd need to look up the user
        
        // Option 1: If authentication contains user ID directly
        // return (Integer) authentication.getPrincipal();
        
        // Option 2: If authentication.getName() is email, look up user
        // User user = userRepository.findByEmail(authentication.getName())
        //     .orElseThrow(() -> new NotFoundException("User not found"));
        // return user.getUserId();
        
        // For now, return a placeholder - this needs to be implemented properly
        throw new UnsupportedOperationException("extractUserIdFromAuth needs to be implemented based on your authentication mechanism");
    }
}






package com.mssus.app.controller;

import com.mssus.app.dto.request.VerificationQueueFilter;
import com.mssus.app.dto.request.VerificationReviewDecisionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VerificationAssignmentResponse;
import com.mssus.app.dto.response.VerificationDecisionResponse;
import com.mssus.app.dto.response.VerificationQueueItemResponse;
import com.mssus.app.service.VerificationReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/verification/review")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Verification Review", description = "Verification review workflow management for administrators")
@RequiredArgsConstructor
public class VerificationReviewController {

    private final VerificationReviewService verificationReviewService;

    @GetMapping("/queue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get verification review queue",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Queue retrieved successfully",
                            content = @Content(schema = @Schema(implementation = PageResponse.class)))
            }
    )
    public ResponseEntity<PageResponse<VerificationQueueItemResponse>> getQueue(
            Authentication authentication,
            @Parameter(description = "Queue status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Profile type filter (RIDER/DRIVER)") @RequestParam(required = false) String profileType,
            @Parameter(description = "Only include high-risk submissions") @RequestParam(required = false) Boolean highRiskOnly,
            @Parameter(description = "Only include assignments claimed by the reviewer") @RequestParam(required = false) Boolean onlyMine,
            @Parameter(description = "Search by name or email") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        VerificationQueueFilter filter = VerificationQueueFilter.builder()
                .status(status)
                .profileType(profileType)
                .highRiskOnly(highRiskOnly)
                .onlyMine(onlyMine)
                .search(search)
                .build();
        PageResponse<VerificationQueueItemResponse> response = verificationReviewService.getQueue(authentication.getName(), filter, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/queue/{verificationId}/claim")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Claim a verification case for review",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Verification claimed successfully",
                            content = @Content(schema = @Schema(implementation = VerificationAssignmentResponse.class)))
            }
    )
    public ResponseEntity<VerificationAssignmentResponse> claim(
            Authentication authentication,
            @PathVariable Integer verificationId
    ) {
        VerificationAssignmentResponse response = verificationReviewService.claim(authentication.getName(), verificationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/queue/{verificationId}/release")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Release a claimed verification back to the queue",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Verification released successfully",
                            content = @Content(schema = @Schema(implementation = VerificationAssignmentResponse.class)))
            }
    )
    public ResponseEntity<VerificationAssignmentResponse> release(
            Authentication authentication,
            @PathVariable Integer verificationId
    ) {
        VerificationAssignmentResponse response = verificationReviewService.release(authentication.getName(), verificationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/decision")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Submit a verification review decision",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Decision recorded successfully",
                            content = @Content(schema = @Schema(implementation = VerificationDecisionResponse.class)))
            }
    )
    public ResponseEntity<VerificationDecisionResponse> decide(
            Authentication authentication,
            @Valid @RequestBody VerificationReviewDecisionRequest request
    ) {
        VerificationDecisionResponse response = verificationReviewService.decide(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/audit/export", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Export verification audit logs within a date range",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Audit export generated successfully",
                            content = @Content(mediaType = "text/csv"))
            }
    )
    public ResponseEntity<Resource> exportAudit(
            @Parameter(description = "Start date inclusive", required = true)
            @RequestParam LocalDate from,
            @Parameter(description = "End date inclusive", required = true)
            @RequestParam LocalDate to
    ) {
        Resource resource = verificationReviewService.exportAudit(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + resource.getFilename())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }
}

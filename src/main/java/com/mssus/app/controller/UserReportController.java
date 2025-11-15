package com.mssus.app.controller;

import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.dto.request.report.DriverReportResponseRequest;
import com.mssus.app.dto.request.report.UpdateRideReportRequest;
import com.mssus.app.dto.request.report.UserReportCreateRequest;
import com.mssus.app.dto.request.report.UserReportResolveRequest;
import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.report.ReportAnalyticsResponse;
import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import com.mssus.app.service.UserReportService;
import com.mssus.app.dto.request.report.StartReportChatRequest;
import com.mssus.app.dto.response.chat.MessageResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/user-reports")
@RequiredArgsConstructor
@Tag(name = "User Reports", description = "Submit and manage user issue reports")
@SecurityRequirement(name = "bearerAuth")
public class UserReportController {

    private final UserReportService userReportService;

    @PostMapping
    @Operation(summary = "Submit a new user report")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Report submitted successfully",
            content = @Content(schema = @Schema(implementation = UserReportResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UserReportResponse> submitReport(
        @Valid @RequestBody UserReportCreateRequest request,
        Authentication authentication
    ) {
        log.info("User {} submitting report of type {}", authentication.getName(), request.getReportType());
        UserReportResponse response = userReportService.submitReport(authentication, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List user reports for review")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reports retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<PageResponse<UserReportSummaryResponse>> getReports(
        @Parameter(description = "Filter by report status") @RequestParam(required = false) ReportStatus status,
        @Parameter(description = "Filter by report type") @RequestParam(required = false) ReportType reportType,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        log.info("Admin listing reports - status: {}, type: {}, page: {}, size: {}", status, reportType, page, size);
        PageResponse<UserReportSummaryResponse> response = userReportService.getReports(status, reportType, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get details of a specific user report")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Report retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserReportResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ResponseEntity<UserReportResponse> getReportDetails(
        @Parameter(description = "Identifier of the report") @PathVariable Integer reportId
    ) {
        log.info("Fetching report details for {}", reportId);
        UserReportResponse response = userReportService.getReportDetails(reportId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reportId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resolve a user report with a response")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Report resolved successfully",
            content = @Content(schema = @Schema(implementation = UserReportResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ResponseEntity<UserReportResponse> resolveReport(
        @Parameter(description = "Identifier of the report") @PathVariable Integer reportId,
        @Valid @RequestBody UserReportResolveRequest request,
        Authentication authentication
    ) {
        log.info("Admin {} resolving report {}", authentication.getName(), reportId);
        UserReportResponse response = userReportService.resolveReport(reportId, request, authentication);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update ride report status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Report status updated successfully",
            content = @Content(schema = @Schema(implementation = UserReportResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload or invalid status transition",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
        @ApiResponse(responseCode = "404", description = "Report not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserReportResponse> updateRideReportStatus(
        @Parameter(description = "Identifier of the report") @PathVariable Integer reportId,
        @Valid @RequestBody UpdateRideReportRequest request,
        Authentication authentication
    ) {
        log.info("Admin {} updating ride report {} status to {}", authentication.getName(), reportId, request.getStatus());
        UserReportResponse response = userReportService.updateRideReportStatus(reportId, request, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-reports")
    @Operation(summary = "Get user's own reports")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User reports retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PageResponse<UserReportSummaryResponse>> getMyReports(
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
        Authentication authentication
    ) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        log.info("User {} fetching their reports - page: {}, size: {}", authentication.getName(), page, size);
        PageResponse<UserReportSummaryResponse> response = userReportService.getMyReports(authentication, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reportId}/driver-response")
    @Operation(summary = "Submit driver response to a report")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Driver response submitted successfully",
            content = @Content(schema = @Schema(implementation = UserReportResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request or already responded"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Driver can only respond to reports about them"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ResponseEntity<UserReportResponse> submitDriverResponse(
        @Parameter(description = "Identifier of the report") @PathVariable Integer reportId,
        @Valid @RequestBody DriverReportResponseRequest request,
        Authentication authentication
    ) {
        log.info("Driver {} submitting response to report {}", authentication.getName(), reportId);
        UserReportResponse response = userReportService.submitDriverResponse(reportId, request, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get report analytics and statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully",
            content = @Content(schema = @Schema(implementation = ReportAnalyticsResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<ReportAnalyticsResponse> getReportAnalytics() {
        log.info("Fetching report analytics");
        ReportAnalyticsResponse response = userReportService.getReportAnalytics();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reportId}/start-chat")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin starts a chat related to a report with reporter or reported user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Chat started and initial message sent",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ResponseEntity<MessageResponse> startReportChat(
        @Parameter(description = "Identifier of the report") @PathVariable Integer reportId,
        @Valid @RequestBody StartReportChatRequest request,
        Authentication authentication
    ) {
        MessageResponse response = userReportService.startReportChat(reportId, request, authentication);
        return ResponseEntity.ok(response);
    }
}

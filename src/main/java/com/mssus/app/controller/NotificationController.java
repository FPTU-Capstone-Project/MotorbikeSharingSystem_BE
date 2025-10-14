package com.mssus.app.controller;

import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.notification.NotificationResponse;
import com.mssus.app.dto.response.notification.NotificationSummaryResponse;
import com.mssus.app.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
        summary = "Get user notifications",
        description = "Retrieve paginated notifications for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<NotificationSummaryResponse>> getUserNotifications(
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
        Authentication authentication) {

        log.info("Fetching notifications for user: {}", authentication.getName());
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        var pageData = notificationService.getNotificationsForUser(authentication, pageable);
        PageResponse<NotificationSummaryResponse> response = PageResponse.<NotificationSummaryResponse>builder()
            .data(pageData.getContent())
            .pagination(PageResponse.PaginationInfo.builder()
                .page(pageData.getNumber() + 1)
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalRecords(pageData.getTotalElements())
                .build())
            .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{notifId}")
    @Operation(
        summary = "Get notification details",
        description = "Retrieve detailed information about a specific notification"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification retrieved successfully",
            content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
        @ApiResponse(responseCode = "404", description = "Notification not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> getNotificationById(
        @Parameter(description = "Notification ID") @PathVariable Integer notifId) {
        log.info("Fetching notification: {}", notifId);
        NotificationResponse response = notificationService.getNotificationById(notifId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{notifId}/read")
    @Operation(
        summary = "Mark notification as read",
        description = "Mark a specific notification as read for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification marked as read successfully"),
        @ApiResponse(responseCode = "404", description = "Notification not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> markAsRead(
        @Parameter(description = "Notification ID") @PathVariable Integer notifId) {
        log.info("Marking notification {} as read", notifId);
        notificationService.markAsRead(notifId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Operation(
        summary = "Mark all notifications as read",
        description = "Mark all notifications as read for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All notifications marked as read successfully"),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        log.info("Marking all notifications as read for user: {}", authentication.getName());
        notificationService.markAllAsReadForUser(authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{notifId}")
    @Operation(
        summary = "Delete notification",
        description = "Delete a specific notification"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Notification not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteNotification(
        @Parameter(description = "Notification ID") @PathVariable Integer notifId) {
        log.info("Deleting notification: {}", notifId);
        notificationService.deleteNotification(notifId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @Operation(
        summary = "Delete all user notifications",
        description = "Delete all notifications for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All notifications deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteAllNotifications(Authentication authentication) {
        log.info("Deleting all notifications for user: {}", authentication.getName());
        notificationService.deleteAllForUser(authentication);
        return ResponseEntity.ok().build();
    }
}

package com.mssus.app.controller;

import com.mssus.app.dto.request.rating.RateDriverRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.rating.RatingResponse;
import com.mssus.app.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/ratings")
@Tag(name = "Ratings", description = "Driver ratings submitted by riders")
@SecurityRequirement(name = "bearerAuth")
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    @Operation(summary = "Submit rating for a driver", description = "Rider submits a rating after completing a shared ride request")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rating submitted successfully",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "User is not a rider")
    })
    public ResponseEntity<MessageResponse> rateDriver(
        @Valid @RequestBody RateDriverRequest request,
        Authentication authentication
    ) {
        log.info("Submitting driver rating for request {}", request.sharedRideRequestId());
        ratingService.rateDriver(authentication, request.sharedRideRequestId(), request.score(), request.comment());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(MessageResponse.of("Rating submitted successfully"));
    }

    @GetMapping("/driver/history")
    @Operation(summary = "Get ratings received by the authenticated driver")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ratings retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "User is not a driver")
    })
    public ResponseEntity<PageResponse<RatingResponse>> getDriverRatings(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<RatingResponse> response = ratingService.getDriverRatingsHistory(authentication, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rider/history")
    @Operation(summary = "Get ratings submitted by the authenticated rider")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ratings retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "User is not a rider")
    })
    public ResponseEntity<PageResponse<RatingResponse>> getRiderRatings(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<RatingResponse> response = ratingService.getRiderRatingsHistory(authentication, pageable);
        return ResponseEntity.ok(response);
    }
}

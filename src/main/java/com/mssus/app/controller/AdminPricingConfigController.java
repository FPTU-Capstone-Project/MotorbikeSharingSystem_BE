package com.mssus.app.controller;

import com.mssus.app.common.enums.PricingConfigStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.pricing.CreatePricingConfigRequest;
import com.mssus.app.dto.request.pricing.FareTierConfigRequest;
import com.mssus.app.dto.request.pricing.ReplaceFareTiersRequest;
import com.mssus.app.dto.request.pricing.UpdatePricingConfigRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.pricing.PricingConfigResponse;
import com.mssus.app.service.PricingConfigAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pricing-configs")
@RequiredArgsConstructor
@Tag(name = "Admin Pricing", description = "Manage fare pricing configurations and tiers")
@SecurityRequirement(name = "bearerAuth")
public class AdminPricingConfigController {

    private final PricingConfigAdminService pricingConfigAdminService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "List pricing configs",
        description = "Paginated list of pricing configs filtered by status",
        responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PageResponse.class)))
    )
    public ResponseEntity<PageResponse<PricingConfigResponse>> list(
        @RequestParam(value = "status", required = false) String status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        PricingConfigStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = PricingConfigStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw BaseDomainException.validation("Unsupported status value: " + status);
            }
        }
        return ResponseEntity.ok(pricingConfigAdminService.list(filter, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Pricing config detail",
        description = "Fetch a single pricing configuration with fare tiers",
        responses = @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PricingConfigResponse.class)))
    )
    public ResponseEntity<PricingConfigResponse> get(@PathVariable Integer id) {
        return ResponseEntity.ok(pricingConfigAdminService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create draft pricing config", responses = {
        @ApiResponse(responseCode = "201", description = "Draft created"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<PricingConfigResponse> create(
        @Valid @RequestBody CreatePricingConfigRequest request,
        Authentication authentication
    ) {
        PricingConfigResponse response = pricingConfigAdminService.createDraft(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update draft/scheduled metadata")
    public ResponseEntity<PricingConfigResponse> updateMetadata(
        @PathVariable Integer id,
        @Valid @RequestBody UpdatePricingConfigRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(pricingConfigAdminService.updateMetadata(id, request, authentication));
    }

    @PutMapping("/{id}/tiers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace fare tiers for a draft/scheduled config")
    public ResponseEntity<PricingConfigResponse> replaceTiers(
        @PathVariable Integer id,
        @Valid @RequestBody ReplaceFareTiersRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(pricingConfigAdminService.replaceTiers(id, request, authentication));
    }

    @PostMapping("/{id}/tiers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a fare tier to a draft/scheduled config")
    public ResponseEntity<PricingConfigResponse> addTier(
        @PathVariable Integer id,
        @Valid @RequestBody FareTierConfigRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingConfigAdminService.addTier(id, request, authentication));
    }

    @PutMapping("/{id}/tiers/{tierId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a fare tier in a draft/scheduled config")
    public ResponseEntity<PricingConfigResponse> updateTier(
        @PathVariable Integer id,
        @PathVariable Integer tierId,
        @Valid @RequestBody FareTierConfigRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(pricingConfigAdminService.updateTier(id, tierId, request, authentication));
    }

    @DeleteMapping("/{id}/tiers/{tierId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a fare tier from a draft/scheduled config")
    public ResponseEntity<PricingConfigResponse> deleteTier(
        @PathVariable Integer id,
        @PathVariable Integer tierId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(pricingConfigAdminService.deleteTier(id, tierId, authentication));
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Schedule go-live at 03:00 (UTC+7) with 24h notice")
    public ResponseEntity<PricingConfigResponse> schedule(
        @PathVariable Integer id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(pricingConfigAdminService.schedule(id, authentication));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive a non-active pricing config")
    public ResponseEntity<PricingConfigResponse> archive(@PathVariable Integer id) {
        return ResponseEntity.ok(pricingConfigAdminService.archive(id));
    }
}

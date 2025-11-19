package com.mssus.app.controller;

import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/transaction")
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<TransactionResponse>> getAllTransactions(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by transaction type") @RequestParam(required = false) String type,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by direction (IN/OUT/INTERNAL)") @RequestParam(required = false) String direction,
            @Parameter(description = "Filter by actor kind (USER/SYSTEM/PSP)") @RequestParam(required = false) String actorKind,
            @Parameter(description = "Filter by start date (yyyy-MM-dd)") @RequestParam(required = false) String dateFrom,
            @Parameter(description = "Filter by end date (yyyy-MM-dd)") @RequestParam(required = false) String dateTo
    ) {
        Sort.Direction sort = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort, sortBy));
        PageResponse<TransactionResponse> response = transactionService.getAllTransactions(
                pageable, type, status, direction, actorKind, dateFrom, dateTo);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get transaction history", description = "Retrieve paginated transaction history")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/history/user-transactions")
    public ResponseEntity<PageResponse<TransactionResponse>> getTransactions(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Filter by transaction type") @RequestParam(required = false) String type,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status) {
        PageResponse<TransactionResponse> response = transactionService.getUserHistoryTransactions(
                authentication, pageable, type, status);
        return ResponseEntity.ok(response);
    }
}

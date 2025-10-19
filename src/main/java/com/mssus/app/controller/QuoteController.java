package com.mssus.app.controller;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.QuoteRequest;
import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.service.pricing.model.Quote;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteService quoteService;
    private final UserRepository userRepository; //TODO: Use service instead of repository directly, fix later

    @PostMapping
    @Operation(summary = "Get ride's quote", description = "Retrieve the quote for a ride based on pickup and dropoff locations",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote retrieved successfully",
            content = @Content(schema = @Schema(implementation = Quote.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Quote> getQuote(@Valid @RequestBody QuoteRequest request, Authentication authentication) {
        var userId = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email"))
            .getRiderProfile()
            .getRiderId();

        var quote = quoteService.generateQuote(request, userId);

        return ResponseEntity.ok(quote);
    }
}

package com.mssus.app.dto.request.rating;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for submitting a driver rating after a completed ride request")
public record RateDriverRequest(

    @NotNull(message = "Shared ride request id is required")
    @Positive(message = "Shared ride request id must be positive")
    @Schema(description = "Identifier of the completed shared ride request", example = "123")
    Integer sharedRideRequestId,

    @NotNull(message = "Rating score is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    @Schema(description = "Rating score from rider to driver", example = "5", minimum = "1", maximum = "5")
    Integer score,

    @Size(max = 500, message = "Comment cannot exceed 500 characters")
    @Schema(description = "Optional rider comment for the driver", example = "Very safe ride, thanks!")
    String comment
) {
}

package com.mssus.app.dto.request.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for resolving a user report")
public class UserReportResolveRequest {

    @NotBlank(message = "Resolution message is required")
    @Size(max = 2000, message = "Resolution message cannot exceed 2000 characters")
    @Schema(description = "Response provided to the reporting user", example = "We have addressed the issue and taken action with the driver.")
    private String resolutionMessage;
}

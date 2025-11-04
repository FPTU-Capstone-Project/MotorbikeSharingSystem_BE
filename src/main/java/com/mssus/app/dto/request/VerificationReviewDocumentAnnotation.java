package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Highlight metadata captured by reviewers on uploaded documents.")
public class VerificationReviewDocumentAnnotation {

    @NotNull
    @Schema(description = "Document page index starting from 1")
    private Integer page;

    @NotNull
    @Schema(description = "Top-left X coordinate as a percentage 0-100")
    private Double x;

    @NotNull
    @Schema(description = "Top-left Y coordinate as a percentage 0-100")
    private Double y;

    @NotNull
    @Schema(description = "Annotation width as a percentage 0-100")
    private Double width;

    @NotNull
    @Schema(description = "Annotation height as a percentage 0-100")
    private Double height;

    @Schema(description = "Optional comment describing the annotation")
    private String comment;
}

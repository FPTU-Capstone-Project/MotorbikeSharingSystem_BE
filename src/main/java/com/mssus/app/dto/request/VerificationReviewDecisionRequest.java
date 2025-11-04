package com.mssus.app.dto.request;

import com.mssus.app.common.enums.VerificationDecisionOutcome;
import com.mssus.app.common.enums.VerificationDecisionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Decision payload recorded when a reviewer resolves a verification case.")
public class VerificationReviewDecisionRequest {

    @NotNull
    @Schema(description = "Target verification identifier")
    private Integer verificationId;

    @NotNull
    @Schema(description = "Outcome recorded by the reviewer")
    private VerificationDecisionOutcome outcome;

    @NotNull
    @Schema(description = "Standardised reason code accompanying the decision")
    private VerificationDecisionReason decisionReason;

    @Size(max = 2000)
    @Schema(description = "Freeform notes captured by the reviewer")
    private String decisionNotes;

    @Schema(description = "Supporting evidence references such as document IDs or external links")
    private List<@Size(max = 255) String> evidenceReferences;

    @Schema(description = "Document annotations captured during review")
    private List<VerificationReviewDocumentAnnotation> annotations;

    @Schema(description = "Set to true by a lead reviewer to override the secondary review requirement")
    private boolean overrideSecondaryRequirement;

    @Size(max = 1000)
    @Schema(description = "Justification recorded when overriding secondary review")
    private String overrideJustification;
}

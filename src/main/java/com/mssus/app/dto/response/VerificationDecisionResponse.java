package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mssus.app.common.enums.VerificationDecisionOutcome;
import com.mssus.app.common.enums.VerificationReviewStage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Acknowledgement payload after recording a verification decision.")
public class VerificationDecisionResponse {

    @JsonProperty("verification_id")
    private Integer verificationId;

    private String status;

    @JsonProperty("current_stage")
    private VerificationReviewStage stage;

    @JsonProperty("secondary_review_required")
    private boolean secondaryReviewRequired;

    @JsonProperty("decision_outcome")
    private VerificationDecisionOutcome outcome;

    @JsonProperty("decided_at")
    private LocalDateTime decidedAt;

    private String message;
}

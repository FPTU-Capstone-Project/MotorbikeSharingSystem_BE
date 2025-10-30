package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "Row returned from the verification review queue API.")
public class VerificationQueueItemResponse {

    @JsonProperty("verification_id")
    private Integer verificationId;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("profile_type")
    private String profileType;

    private String type;

    private String status;

    @JsonProperty("risk_score")
    private Integer riskScore;

    @JsonProperty("high_risk")
    private boolean highRisk;

    @JsonProperty("submitted_at")
    private LocalDateTime submittedAt;

    @JsonProperty("assignment_claimed_at")
    private LocalDateTime assignmentClaimedAt;

    @JsonProperty("assigned_reviewer_id")
    private Integer assignedReviewerId;

    @JsonProperty("assigned_reviewer_name")
    private String assignedReviewerName;

    @JsonProperty("claimable")
    private boolean claimable;

    @JsonProperty("current_stage")
    private VerificationReviewStage currentStage;

    @JsonProperty("secondary_review_required")
    private boolean secondaryReviewRequired;
}

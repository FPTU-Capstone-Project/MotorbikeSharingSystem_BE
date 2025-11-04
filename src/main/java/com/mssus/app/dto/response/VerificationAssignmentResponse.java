package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "Details returned when a reviewer claims or releases a verification assignment.")
public class VerificationAssignmentResponse {

    @JsonProperty("verification_id")
    private Integer verificationId;

    @JsonProperty("reviewer_id")
    private Integer reviewerId;

    @JsonProperty("reviewer_name")
    private String reviewerName;

    @JsonProperty("assigned_at")
    private LocalDateTime assignedAt;

    private String status;

    private String message;
}

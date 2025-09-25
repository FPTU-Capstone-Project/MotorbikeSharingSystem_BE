package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk operation response")
public class BulkOperationResponse {

    @JsonProperty("total_requested")
    @Schema(description = "Total number of items requested", example = "10")
    private Integer totalRequested;

    @JsonProperty("successful_count")
    @Schema(description = "Number of successful operations", example = "8")
    private Integer successfulCount;

    @JsonProperty("failed_count")
    @Schema(description = "Number of failed operations", example = "2")
    private Integer failedCount;

    @JsonProperty("successful_ids")
    @Schema(description = "List of successfully processed IDs", example = "[1, 2, 3, 4, 5, 6, 7, 8]")
    private List<Integer> successfulIds;

    @JsonProperty("failed_items")
    @Schema(description = "List of failed items with reasons")
    private List<FailedItem> failedItems;

    @Schema(description = "Overall operation message", example = "Bulk approval completed")
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Failed item information")
    public static class FailedItem {
        @Schema(description = "Item ID that failed", example = "9")
        private Integer id;

        @Schema(description = "Failure reason", example = "Verification not found")
        private String reason;
    }
}

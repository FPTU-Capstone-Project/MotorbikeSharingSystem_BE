package com.mssus.app.dto.request.report;

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
@Schema(description = "Request payload for an admin to start a chat conversation related to a user report")
public class StartReportChatRequest {

    @NotNull(message = "Target user ID is required")
    @Schema(description = "The user ID of the person the admin wants to chat with (reporter or reported person)", example = "15")
    private Integer targetUserId;

    @Schema(description = "An optional initial message from the admin to start the conversation", example = "Xin chào, tôi muốn trao đổi về báo cáo này.")
    private String initialMessage;
}



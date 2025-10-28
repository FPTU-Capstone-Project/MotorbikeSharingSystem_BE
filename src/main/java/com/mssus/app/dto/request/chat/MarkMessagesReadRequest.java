package com.mssus.app.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkMessagesReadRequest {
    
    @NotBlank(message = "Conversation ID is required")
    private String conversationId;
}






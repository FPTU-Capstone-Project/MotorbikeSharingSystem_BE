package com.mssus.app.dto.request.chat;

import com.mssus.app.common.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    
    @NotNull(message = "Receiver ID is required")
    private Integer receiverId;
    
    // For RIDE_REQUEST conversation; null when sending REPORT conversation message
    private Integer rideRequestId;
    
    // For REPORT conversation; null when sending RIDE_REQUEST conversation message
    private Integer reportId;
    
    @NotNull(message = "Message type is required")
    private MessageType messageType;
    
    @NotBlank(message = "Message content cannot be empty")
    private String content;
    
    /**
     * Optional metadata for special message types (e.g., location coordinates, image URL)
     */
    private String metadata;
}






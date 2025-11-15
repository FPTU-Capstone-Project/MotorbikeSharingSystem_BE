package com.mssus.app.dto.response.chat;

import com.mssus.app.common.enums.MessageType;
import com.mssus.app.common.enums.ConversationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    
    private Integer messageId;
    private Integer senderId;
    private String senderName;
    private String senderPhotoUrl;
    private Integer receiverId;
    private String receiverName;
    private String receiverPhotoUrl;
    private String conversationId;
    private ConversationType conversationType;
    private Integer rideRequestId;
    private Integer reportId;
    private MessageType messageType;
    private String content;
    private String metadata;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime sentAt;
}






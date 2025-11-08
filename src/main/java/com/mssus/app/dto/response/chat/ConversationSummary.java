package com.mssus.app.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {
    
    private String conversationId;
    private Integer otherUserId;
    private String otherUserName;
    private String otherUserPhotoUrl;
    private String otherUserType; // "DRIVER" or "RIDER"
    private Integer rideRequestId;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Long unreadCount;
    private String pickupAddress;
    private String dropoffAddress;
}






package com.mssus.app.dto.response.chat;

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
public class ConversationSummary {
    
    private String conversationId;
    private Integer otherUserId;
    private String otherUserName;
    private String otherUserPhotoUrl;
    private String otherUserType; // "DRIVER" or "RIDER"
    private ConversationType conversationType;
    private Integer rideRequestId;
    private Integer reportId;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Long unreadCount;
    private String pickupAddress;
    private String dropoffAddress;
}






package com.mssus.app.service;

import com.mssus.app.dto.request.chat.SendMessageRequest;
import com.mssus.app.dto.response.chat.ConversationSummary;
import com.mssus.app.dto.response.chat.MessageResponse;

import java.util.List;

public interface MessageService {
    
    /**
     * Send a message from one user to another
     * @param senderEmail Email of the sender (from JWT token)
     * @param request Message request details
     * @return MessageResponse with userId in response
     */
    MessageResponse sendMessage(String senderEmail, SendMessageRequest request);
    
    /**
     * Get all messages in a conversation (ride request)
     * @param userEmail Email of the requesting user (from JWT token)
     * @param rideRequestId The ride request ID
     * @return List of messages with userId in responses
     */
    List<MessageResponse> getConversationMessages(String userEmail, Integer rideRequestId);
    
    /**
     * Get all conversations for a user
     * @param userEmail Email of the user (from JWT token)
     * @return List of conversations with userId in responses
     */
    List<ConversationSummary> getUserConversations(String userEmail);
    
    /**
     * Mark all messages in a conversation as read
     * @param userEmail Email of the user (from JWT token)
     * @param conversationId The conversation ID
     */
    void markMessagesAsRead(String userEmail, String conversationId);
    
    /**
     * Get unread message count for a user
     * @param userEmail Email of the user (from JWT token)
     * @return Count of unread messages
     */
    Long getUnreadMessageCount(String userEmail);
    
    /**
     * Generate conversation ID from ride request and participants
     */
    String generateConversationId(Integer rideRequestId, Integer userId1, Integer userId2);
}






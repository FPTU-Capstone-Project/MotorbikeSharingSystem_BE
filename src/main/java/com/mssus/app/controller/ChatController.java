package com.mssus.app.controller;

import com.mssus.app.dto.request.chat.MarkMessagesReadRequest;
import com.mssus.app.dto.request.chat.SendMessageRequest;
import com.mssus.app.dto.response.chat.ConversationSummary;
import com.mssus.app.dto.response.chat.MessageResponse;
import com.mssus.app.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

/**
 * Chat Controller for handling both REST and WebSocket chat operations
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final MessageService messageService;

    // ========== REST Endpoints ==========

    /**
     * REST: Send a message via HTTP
     */
    @PostMapping("/api/v1/chat/messages")
    @ResponseBody
    public ResponseEntity<MessageResponse> sendMessageHttp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SendMessageRequest request
    ) {
        log.info("REST: Sending message from user {}", userDetails.getUsername());
        MessageResponse response = messageService.sendMessage(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * REST: Get all conversations for the current user
     */
    @GetMapping("/api/v1/chat/conversations")
    @ResponseBody
    public ResponseEntity<List<ConversationSummary>> getUserConversations(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("REST: Getting conversations for user {}", userDetails.getUsername());
        String userId = userDetails.getUsername();
        log.info("REST: Getting conversations for user {}", userId);
        List<ConversationSummary> conversations = messageService.getUserConversations(userId);
        return ResponseEntity.ok(conversations);
    }

    /**
     * REST: Get all messages in a conversation
     */
    @GetMapping("/api/v1/chat/conversations/{rideRequestId}/messages")
    @ResponseBody
    public ResponseEntity<List<MessageResponse>> getConversationMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer rideRequestId
    ) {
        log.info("REST: Getting messages for ride request {}", rideRequestId);
        String userId = userDetails.getUsername();
        List<MessageResponse> messages = messageService.getConversationMessages(userId, rideRequestId);
        return ResponseEntity.ok(messages);
    }

    /**
     * REST: Mark messages as read
     */
    @PostMapping("/api/v1/chat/conversations/read")
    @ResponseBody
    public ResponseEntity<Void> markMessagesAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MarkMessagesReadRequest request
    ) {
        log.info("REST: Marking messages as read for conversation {}", request.getConversationId());
        String userId = userDetails.getUsername();
        messageService.markMessagesAsRead(userId, request.getConversationId());
        return ResponseEntity.ok().build();
    }

    /**
     * REST: Get unread message count
     */
    @GetMapping("/api/v1/chat/unread-count")
    @ResponseBody
    public ResponseEntity<Long> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("REST: Getting unread count for user {}", userDetails.getUsername());
        String userId = userDetails.getUsername();
        Long count = messageService.getUnreadMessageCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * REST: Upload image for chat
     */
    @PostMapping("/api/v1/chat/upload-image")
    @ResponseBody
    public ResponseEntity<MessageResponse> uploadChatImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("receiverId") Integer receiverId,
            @RequestParam("rideRequestId") Integer rideRequestId
    ) {
        try {
            log.info("REST: Uploading chat image from user {}", userDetails.getUsername());
            MessageResponse response = messageService.uploadChatImage(
                    userDetails.getUsername(),
                    file,
                    receiverId,
                    rideRequestId
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("REST: Error uploading chat image", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========== WebSocket Endpoints ==========

    /**
     * WebSocket: Send a message via WebSocket
     * Client sends to: /app/chat.send
     */
    @MessageMapping("/chat.send")
    public void sendMessageWebSocket(
            @Payload @Valid SendMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            Principal principal = headerAccessor.getUser();
            if (principal == null) {
                log.error("No principal found in WebSocket message");
                return;
            }

            log.info("WebSocket: Received message from {}", principal.getName());
            String senderId = principal.getName();
            MessageResponse response = messageService.sendMessage(senderId, request);
            log.info("WebSocket: Message sent successfully with ID {}", response.getMessageId());
            
        } catch (Exception e) {
            log.error("WebSocket: Error sending message", e);
        }
    }

    /**
     * WebSocket: Mark messages as read
     * Client sends to: /app/chat.read
     */
    @MessageMapping("/chat.read")
    public void markAsReadWebSocket(
            @Payload @Valid MarkMessagesReadRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            Principal principal = headerAccessor.getUser();
            if (principal == null) {
                log.error("No principal found in WebSocket message");
                return;
            }

            log.info("WebSocket: Marking messages as read for conversation {}", request.getConversationId());
            String userId = principal.getName();
            
            messageService.markMessagesAsRead(userId, request.getConversationId());
            log.info("WebSocket: Messages marked as read successfully");
            
        } catch (Exception e) {
            log.error("WebSocket: Error marking messages as read", e);
        }
    }
}






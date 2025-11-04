package com.mssus.app.service.impl;

import com.mssus.app.common.enums.MessageType;
import com.mssus.app.dto.request.chat.SendMessageRequest;
import com.mssus.app.dto.response.chat.ConversationSummary;
import com.mssus.app.dto.response.chat.MessageResponse;
import com.mssus.app.entity.*;
import com.mssus.app.repository.*;
import com.mssus.app.service.FileUploadService;
import com.mssus.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SharedRideRequestRepository sharedRideRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileUploadService fileUploadService;

    private static final String CHAT_QUEUE = "/queue/chat";

    @Override
    @Transactional
    public MessageResponse sendMessage(String senderEmail, SendMessageRequest request) {
        log.info("Sending message from user email: {} to user ID: {}", senderEmail, request.getReceiverId());

        // Look up sender by email (from JWT token)
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Sender not found with email: " + senderEmail));
        
        // Look up receiver by ID
        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found with ID: " + request.getReceiverId()));

        // Validate ride request exists
        SharedRideRequest rideRequest = sharedRideRequestRepository.findById(request.getRideRequestId())
                .orElseThrow(() -> new RuntimeException("Ride request not found"));

        // Generate conversation ID
        String conversationId = generateConversationId(
                request.getRideRequestId(),
                sender.getUserId(),
                request.getReceiverId()
        );

        // Create message
        Message message = Message.builder()
                .senderId(sender.getUserId())
                .receiverId(request.getReceiverId())
                .sharedRideRequest(rideRequest)
                .conversationId(conversationId)
                .messageType(request.getMessageType())
                .content(request.getContent())
                .metadata(request.getMetadata())
                .isRead(false)
                .build();

        message = messageRepository.save(message);
        log.info("Message saved with ID: {}", message.getMessageId());

        // Convert to response DTO
        MessageResponse response = convertToResponse(message, sender, receiver);

        // Send real-time notification via WebSocket to receiver
        try {
            messagingTemplate.convertAndSendToUser(
                    request.getReceiverId().toString(),
                    CHAT_QUEUE,
                    response
            );
            log.info("WebSocket message sent to user {}", request.getReceiverId());
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user {}", request.getReceiverId(), e);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getConversationMessages(String userEmail, Integer rideRequestId) {
        log.info("Getting conversation messages for user email: {} and ride request {}", userEmail, rideRequestId);

        // Look up user by email to verify they exist
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        
        log.debug("Found user ID: {} for email: {}", user.getUserId(), userEmail);

        List<Message> messages = messageRepository.findBySharedRideRequest_SharedRideRequestIdOrderBySentAtAsc(rideRequestId);

        return messages.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummary> getUserConversations(String userEmail) {
        log.info("Getting all conversations for user email: {}", userEmail);

        // Look up user by email to get userId
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        
        Integer userId = user.getUserId();
        log.debug("Found user ID: {} for email: {}", userId, userEmail);

        List<Message> latestMessages = messageRepository.findLatestMessagesForUser(userId);
        
        Map<String, ConversationSummary> conversationMap = new HashMap<>();

        for (Message message : latestMessages) {
            String conversationId = message.getConversationId();
            
            if (conversationMap.containsKey(conversationId)) {
                continue;
            }

            // Determine the other user in the conversation
            Integer otherUserId = message.getSenderId().equals(userId) 
                    ? message.getReceiverId() 
                    : message.getSenderId();

            User otherUser = userRepository.findById(otherUserId).orElse(null);
            if (otherUser == null) {
                continue;
            }

            // Count unread messages in this conversation
            Long unreadCount = messageRepository.findUnreadMessagesByReceiverIdAndConversationId(
                    userId, conversationId
            ).stream().count();

            // Determine user type
            String otherUserType = "RIDER";
            if (otherUser.getDriverProfile() != null) {
                otherUserType = "DRIVER";
            }

            // Get location info from ride request
            SharedRideRequest rideRequest = message.getSharedRideRequest();
            String pickupAddress = rideRequest != null && rideRequest.getPickupLocation() != null 
                    ? rideRequest.getPickupLocation().getName() 
                    : "Unknown";
            String dropoffAddress = rideRequest != null && rideRequest.getDropoffLocation() != null 
                    ? rideRequest.getDropoffLocation().getName() 
                    : "Unknown";

            ConversationSummary summary = ConversationSummary.builder()
                    .conversationId(conversationId)
                    .otherUserId(otherUserId)
                    .otherUserName(otherUser.getFullName())
                    .otherUserPhotoUrl(otherUser.getProfilePhotoUrl())
                    .otherUserType(otherUserType)
                    .rideRequestId(rideRequest != null ? rideRequest.getSharedRideRequestId() : null)
                    .lastMessage(message.getContent())
                    .lastMessageTime(message.getSentAt())
                    .unreadCount(unreadCount)
                    .pickupAddress(pickupAddress)
                    .dropoffAddress(dropoffAddress)
                    .build();

            conversationMap.put(conversationId, summary);
        }

        return new ArrayList<>(conversationMap.values());
    }

    @Override
    @Transactional
    public void markMessagesAsRead(String userEmail, String conversationId) {
        log.info("Marking messages as read for user email: {} in conversation {}", userEmail, conversationId);

        // Look up user by email to get userId
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        
        Integer userId = user.getUserId();
        log.debug("Found user ID: {} for email: {}", userId, userEmail);

        List<Message> unreadMessages = messageRepository.findUnreadMessagesByReceiverIdAndConversationId(
                userId, conversationId
        );

        LocalDateTime now = LocalDateTime.now();
        for (Message message : unreadMessages) {
            message.setIsRead(true);
            message.setReadAt(now);
        }

        messageRepository.saveAll(unreadMessages);
        log.info("Marked {} messages as read", unreadMessages.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Long getUnreadMessageCount(String userEmail) {
        log.info("Getting unread message count for user email: {}", userEmail);

        // Look up user by email to get userId
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        
        Integer userId = user.getUserId();
        log.debug("Found user ID: {} for email: {}", userId, userEmail);

        return messageRepository.countUnreadMessagesByReceiverId(userId);
    }

    @Override
    @Transactional
    public MessageResponse uploadChatImage(String senderEmail, MultipartFile file, Integer receiverId, Integer rideRequestId) {
        log.info("Uploading chat image from user email: {} to user ID: {}", senderEmail, receiverId);

        try {
            // Upload file to Cloudinary
            CompletableFuture<String> uploadFuture = fileUploadService.uploadFile(file);
            String imageUrl = uploadFuture.get(); // Wait for upload to complete
            log.info("Image uploaded successfully: {}", imageUrl);

            // Create and send IMAGE type message
            SendMessageRequest messageRequest = SendMessageRequest.builder()
                    .receiverId(receiverId)
                    .rideRequestId(rideRequestId)
                    .messageType(MessageType.IMAGE)
                    .content(imageUrl)
                    .metadata("{\"originalFilename\":\"" + file.getOriginalFilename() + "\"}")
                    .build();

            return sendMessage(senderEmail, messageRequest);
            
        } catch (Exception e) {
            log.error("Error uploading chat image", e);
            throw new RuntimeException("Failed to upload chat image", e);
        }
    }

    @Override
    public String generateConversationId(Integer rideRequestId, Integer userId1, Integer userId2) {
        // Ensure consistent conversation ID regardless of who sends first
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);
        return String.format("ride_%d_users_%d_%d", rideRequestId, smaller, larger);
    }

    private MessageResponse convertToResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);
        User receiver = userRepository.findById(message.getReceiverId()).orElse(null);
        
        return convertToResponse(message, sender, receiver);
    }

    private MessageResponse convertToResponse(Message message, User sender, User receiver) {
        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSenderId())
                .senderName(sender != null ? sender.getFullName() : "Unknown")
                .senderPhotoUrl(sender != null ? sender.getProfilePhotoUrl() : null)
                .receiverId(message.getReceiverId())
                .receiverName(receiver != null ? receiver.getFullName() : "Unknown")
                .receiverPhotoUrl(receiver != null ? receiver.getProfilePhotoUrl() : null)
                .conversationId(message.getConversationId())
                .rideRequestId(message.getSharedRideRequest() != null ? 
                        message.getSharedRideRequest().getSharedRideRequestId() : null)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .isRead(message.getIsRead())
                .readAt(message.getReadAt())
                .sentAt(message.getSentAt())
                .build();
    }
}


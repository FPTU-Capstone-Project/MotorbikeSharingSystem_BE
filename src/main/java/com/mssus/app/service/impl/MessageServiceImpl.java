package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ConversationType;
import com.mssus.app.common.enums.MessageType;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.exception.ForbiddenException;
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
    private final UserReportRepository userReportRepository;
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

        SharedRideRequest rideRequest = null;
        UserReport report = null;
        ConversationType conversationType;
        String conversationId;

        if (request.getReportId() != null) {
            // REPORT conversation
            report = userReportRepository.findById(request.getReportId())
                    .orElseThrow(() -> new RuntimeException("Report not found"));
            
            // Validate report chat permissions
            validateReportChatPermission(report, sender, receiver);
            
            conversationType = ConversationType.REPORT;
            conversationId = generateReportConversationId(
                    report.getReportId(),
                    sender.getUserId(),
                    request.getReceiverId()
            );
        } else {
            // RIDE_REQUEST conversation
            if (request.getRideRequestId() == null) {
                throw new RuntimeException("Ride request ID is required for ride conversation");
            }
            rideRequest = sharedRideRequestRepository.findById(request.getRideRequestId())
                    .orElseThrow(() -> new RuntimeException("Ride request not found"));
            conversationType = ConversationType.RIDE_REQUEST;
            conversationId = generateConversationId(
                    request.getRideRequestId(),
                    sender.getUserId(),
                    request.getReceiverId()
            );
        }

        // Create message
        Message message = Message.builder()
                .senderId(sender.getUserId())
                .receiverId(request.getReceiverId())
                .sharedRideRequest(rideRequest)
                .report(report)
                .conversationId(conversationId)
                .conversationType(conversationType)
                .messageType(MessageType.valueOf(request.getMessageType().name()))
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

        if (conversationType == ConversationType.REPORT && report != null) {
            updateReportConversationTracking(report, sender, receiver);
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
    public List<MessageResponse> getMessagesByConversationId(String userEmail, String conversationId) {
        log.info("Getting messages for user email: {} and conversationId: {}", userEmail, conversationId);

        // Look up user by email to verify they exist and get userId
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        
        log.debug("Found user ID: {} for email: {}", user.getUserId(), userEmail);

        // Get messages by conversationId
        List<Message> messages = messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);

        // Verify user is part of this conversation (either sender or receiver)
        boolean isParticipant = messages.stream()
                .anyMatch(m -> m.getSenderId().equals(user.getUserId()) || m.getReceiverId().equals(user.getUserId()));
        
        if (!isParticipant && !messages.isEmpty()) {
            throw new RuntimeException("User is not a participant in this conversation");
        }

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

            // Context based on conversation type
            SharedRideRequest rideRequest = message.getSharedRideRequest();
            String pickupAddress = null;
            String dropoffAddress = null;
            if (rideRequest != null) {
                pickupAddress = rideRequest.getPickupLocation() != null ? rideRequest.getPickupLocation().getName() : "Unknown";
                dropoffAddress = rideRequest.getDropoffLocation() != null ? rideRequest.getDropoffLocation().getName() : "Unknown";
            }

            ConversationSummary summary = ConversationSummary.builder()
                    .conversationId(conversationId)
                    .otherUserId(otherUserId)
                    .otherUserName(otherUser.getFullName())
                    .otherUserPhotoUrl(otherUser.getProfilePhotoUrl())
                    .otherUserType(otherUserType)
                    .conversationType(message.getConversationType())
                    .rideRequestId(rideRequest != null ? rideRequest.getSharedRideRequestId() : null)
                    .reportId(message.getReport() != null ? message.getReport().getReportId() : null)
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

    @Override
    public String generateReportConversationId(Integer reportId, Integer userId1, Integer userId2) {
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);
        return String.format("report_%d_users_%d_%d", reportId, smaller, larger);
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
                .conversationType(message.getConversationType())
                .rideRequestId(message.getSharedRideRequest() != null ? 
                        message.getSharedRideRequest().getSharedRideRequestId() : null)
                .reportId(message.getReport() != null ? message.getReport().getReportId() : null)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .isRead(message.getIsRead())
                .readAt(message.getReadAt())
                .sentAt(message.getSentAt())
                .build();
    }

    private void updateReportConversationTracking(UserReport report, User sender, User receiver) {
        if (report == null || sender == null || receiver == null) {
            return;
        }

        boolean changed = false;
        LocalDateTime now = LocalDateTime.now();
        Integer senderId = sender.getUserId();
        Integer receiverId = receiver.getUserId();

        Integer reporterId = report.getReporter() != null ? report.getReporter().getUserId() : null;
        Integer reportedUserId = resolveReportedUserId(report);
        
        boolean isSenderAdmin = UserType.ADMIN.equals(sender.getUserType());

        // If admin is sending to reporter/reported user, mark chat as started
        if (isSenderAdmin) {
            if (reporterId != null && reporterId.equals(receiverId) && report.getReporterChatStartedAt() == null) {
                report.setReporterChatStartedAt(now);
                changed = true;
            }
            if (reportedUserId != null && reportedUserId.equals(receiverId) && report.getReportedChatStartedAt() == null) {
                report.setReportedChatStartedAt(now);
                changed = true;
            }
        }

        // Update last reply timestamps when reporter/reported user sends messages
        if (reporterId != null && reporterId.equals(senderId)) {
            report.setReporterLastReplyAt(now);
            changed = true;
        } else if (reportedUserId != null && reportedUserId.equals(senderId)) {
            report.setReportedLastReplyAt(now);
            changed = true;
        }

        if (changed) {
            userReportRepository.save(report);
        }
    }

    private Integer resolveReportedUserId(UserReport report) {
        if (report == null) {
            return null;
        }

        Integer reporterUserId = report.getReporter() != null ? report.getReporter().getUserId() : null;

        if (report.getDriver() != null && report.getDriver().getUser() != null) {
            Integer driverUserId = report.getDriver().getUser().getUserId();
            if (driverUserId != null && !Objects.equals(driverUserId, reporterUserId)) {
                return driverUserId;
            }
        }

        if (report.getSharedRide() != null
            && report.getSharedRide().getSharedRideRequest() != null
            && report.getSharedRide().getSharedRideRequest().getRider() != null
            && report.getSharedRide().getSharedRideRequest().getRider().getUser() != null) {
            Integer riderUserId = report.getSharedRide().getSharedRideRequest().getRider().getUser().getUserId();
            if (riderUserId != null && !Objects.equals(riderUserId, reporterUserId)) {
                return riderUserId;
            }
        }

        return null;
    }

    /**
     * Validate that user has permission to send message in report chat.
     * Rules:
     * - Admin can always send (they initiate the chat)
     * - Reporter/Reported user can only send if admin has started the chat first
     */
    private void validateReportChatPermission(UserReport report, User sender, User receiver) {
        Integer senderId = sender.getUserId();
        Integer receiverId = receiver.getUserId();
        Integer reporterId = report.getReporter() != null ? report.getReporter().getUserId() : null;
        Integer reportedUserId = resolveReportedUserId(report);
        
        // Check if sender is admin
        boolean isSenderAdmin = UserType.ADMIN.equals(sender.getUserType());
        boolean isReceiverAdmin = UserType.ADMIN.equals(receiver.getUserType());
        
        // Admin can always send messages (they initiate the chat)
        if (isSenderAdmin) {
            // Admin sending to reporter or reported user - this is allowed
            if ((reporterId != null && reporterId.equals(receiverId)) || 
                (reportedUserId != null && reportedUserId.equals(receiverId))) {
                return; // Valid: Admin chatting with reporter/reported user
            }
            throw new ForbiddenException("Admin can only chat with reporter or reported user in report conversations");
        }
        
        // Non-admin sender (reporter or reported user)
        // They can only send if admin has started the chat first
        if (isReceiverAdmin) {
            // Sender is reporter
            if (reporterId != null && reporterId.equals(senderId)) {
                if (report.getReporterChatStartedAt() == null) {
                    throw new ForbiddenException("Admin has not started a chat with the reporter yet. Please wait for admin to initiate the conversation.");
                }
                return; // Valid: Reporter replying to admin after admin started chat
            }
            
            // Sender is reported user
            if (reportedUserId != null && reportedUserId.equals(senderId)) {
                if (report.getReportedChatStartedAt() == null) {
                    throw new ForbiddenException("Admin has not started a chat with you yet. Please wait for admin to initiate the conversation.");
                }
                return; // Valid: Reported user replying to admin after admin started chat
            }
        }
        
        // Invalid: Sender is not reporter/reported user, or receiver is not admin
        throw new ForbiddenException("You are not authorized to send messages in this report conversation");
    }
}


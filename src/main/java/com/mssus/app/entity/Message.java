package com.mssus.app.entity;

import com.mssus.app.common.enums.MessageType;
import com.mssus.app.common.enums.ConversationType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Integer messageId;

    @Column(name = "sender_id", nullable = false)
    private Integer senderId;

    @Column(name = "receiver_id", nullable = false)
    private Integer receiverId;

    @ManyToOne
    @JoinColumn(name = "shared_ride_request_id")
    private SharedRideRequest sharedRideRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private UserReport report;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "conversation_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConversationType conversationType = ConversationType.RIDE_REQUEST;

    @Column(name = "message_type")
    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "is_read")
    private Boolean isRead;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;
}

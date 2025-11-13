package com.mssus.app.repository;

import com.mssus.app.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {
    
    /**
     * Find all messages for a specific conversation (ride request)
     */
    List<Message> findBySharedRideRequest_SharedRideRequestIdOrderBySentAtAsc(Integer sharedRideRequestId);
    
    /**
     * Find all messages in a conversation by conversationId
     */
    List<Message> findByConversationIdOrderBySentAtAsc(String conversationId);
    
    /**
     * Find all unread messages for a specific receiver
     */
    @Query("SELECT m FROM Message m WHERE m.receiverId = :receiverId AND m.isRead = false ORDER BY m.sentAt ASC")
    List<Message> findUnreadMessagesByReceiverId(@Param("receiverId") Integer receiverId);
    
    /**
     * Find all unread messages in a specific conversation for a receiver
     */
    @Query("SELECT m FROM Message m WHERE m.receiverId = :receiverId AND m.conversationId = :conversationId AND m.isRead = false ORDER BY m.sentAt ASC")
    List<Message> findUnreadMessagesByReceiverIdAndConversationId(
        @Param("receiverId") Integer receiverId, 
        @Param("conversationId") String conversationId
    );
    
    /**
     * Get unread message count for a user
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :receiverId AND m.isRead = false")
    Long countUnreadMessagesByReceiverId(@Param("receiverId") Integer receiverId);
    
    /**
     * Get all conversations for a user (either as sender or receiver)
     */
    @Query("SELECT DISTINCT m.conversationId FROM Message m WHERE m.senderId = :userId OR m.receiverId = :userId ORDER BY m.sentAt DESC")
    List<String> findConversationIdsByUserId(@Param("userId") Integer userId);
    
    /**
     * Get last message in each conversation for a user
     */
    @Query("SELECT m FROM Message m WHERE m.messageId IN " +
           "(SELECT MAX(m2.messageId) FROM Message m2 " +
           "WHERE (m2.senderId = :userId OR m2.receiverId = :userId) " +
           "GROUP BY m2.conversationId) " +
           "ORDER BY m.sentAt DESC")
    List<Message> findLatestMessagesForUser(@Param("userId") Integer userId);
}






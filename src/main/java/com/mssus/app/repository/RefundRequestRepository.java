package com.mssus.app.repository;

import com.mssus.app.common.enums.RefundStatus;
import com.mssus.app.entity.RefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Integer> {

    Optional<RefundRequest> findByRefundRequestUuid(UUID refundRequestUuid);

    @Query("SELECT r FROM RefundRequest r WHERE r.user.userId = :userId ORDER BY r.createdAt DESC")
    List<RefundRequest> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);

    @Query("SELECT r FROM RefundRequest r WHERE r.user.userId = :userId AND r.status = :status ORDER BY r.createdAt DESC")
    List<RefundRequest> findByUserIdAndStatus(@Param("userId") Integer userId, @Param("status") RefundStatus status);

    @Query("SELECT r FROM RefundRequest r WHERE r.status = :status ORDER BY r.createdAt DESC")
    List<RefundRequest> findByStatus(@Param("status") RefundStatus status);

    @Query("SELECT r FROM RefundRequest r WHERE r.status = :status ORDER BY r.createdAt DESC")
    Page<RefundRequest> findByStatus(@Param("status") RefundStatus status, Pageable pageable);

    @Query("SELECT r FROM RefundRequest r WHERE r.bookingId = :bookingId")
    Optional<RefundRequest> findByBookingId(@Param("bookingId") Integer bookingId);

    @Query("SELECT r FROM RefundRequest r WHERE r.transactionId = :transactionId")
    Optional<RefundRequest> findByTransactionId(@Param("transactionId") Integer transactionId);

    @Query("SELECT r FROM RefundRequest r WHERE r.pspRef = :pspRef")
    Optional<RefundRequest> findByPspRef(@Param("pspRef") String pspRef);

    @Query("SELECT r FROM RefundRequest r WHERE r.originalGroupId = :groupId")
    Optional<RefundRequest> findByOriginalGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT r FROM RefundRequest r WHERE r.status IN ('PENDING', 'APPROVED') ORDER BY r.createdAt ASC")
    List<RefundRequest> findPendingAndApprovedRefunds();

    @Query("SELECT r FROM RefundRequest r WHERE r.status IN ('PENDING', 'APPROVED') ORDER BY r.createdAt ASC")
    Page<RefundRequest> findPendingAndApprovedRefunds(Pageable pageable);

    @Query("SELECT COUNT(r) FROM RefundRequest r WHERE r.status = 'PENDING'")
    long countPendingRefunds();

    @Query("SELECT r FROM RefundRequest r WHERE r.user.userId = :userId AND r.bookingId = :bookingId")
    Optional<RefundRequest> findByUserIdAndBookingId(@Param("userId") Integer userId, @Param("bookingId") Integer bookingId);
}






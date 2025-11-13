package com.mssus.app.repository;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.entity.SharedRideRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SharedRideRequestRepository extends JpaRepository<SharedRideRequest, Integer> {
        Page<SharedRideRequest> findByRiderRiderIdOrderByCreatedAtDesc(Integer riderId, Pageable pageable);

        Page<SharedRideRequest> findByRiderRiderIdAndStatusOrderByCreatedAtDesc(
                        Integer riderId, SharedRideRequestStatus status, Pageable pageable);

        Long countByRiderRiderIdAndStatus(Integer riderId, SharedRideRequestStatus status);

        List<SharedRideRequest> findBySharedRideSharedRideIdAndStatus(
                        Integer sharedRideId, SharedRideRequestStatus status);

        List<SharedRideRequest> findBySharedRideSharedRideId(Integer sharedRideId);

        @Query("SELECT r FROM SharedRideRequest r " +
                        "WHERE r.sharedRide.sharedRideId = :sharedRideId " +
                        "AND r.status IN (:confirmedStatus, :ongoingStatus)")
        List<SharedRideRequest> findActiveRequestsByRide(
                        @Param("sharedRideId") Integer sharedRideId,
                        @Param("confirmedStatus") SharedRideRequestStatus confirmedStatus,
                        @Param("ongoingStatus") SharedRideRequestStatus ongoingStatus);

        List<SharedRideRequest> findByRequestKindAndStatusOrderByPickupTimeAsc(
                        RequestKind requestKind, SharedRideRequestStatus status);

        Optional<SharedRideRequest> findBySharedRideRequestIdAndRiderRiderIdAndStatus(
                        Integer requestId, Integer riderId, SharedRideRequestStatus status);

        @Query("SELECT r FROM SharedRideRequest r " +
                        "WHERE r.status = :status " +
                        "AND r.createdAt < :cutoffTime")
        List<SharedRideRequest> findExpiredRequests(
                        @Param("status") SharedRideRequestStatus status,
                        @Param("cutoffTime") LocalDateTime cutoffTime);

        @Modifying
        @Query("UPDATE SharedRideRequest r SET r.status = :status " +
                        "WHERE r.sharedRideRequestId = :requestId")
        void updateStatus(@Param("requestId") Integer requestId, @Param("status") SharedRideRequestStatus status);

        @Modifying
        @Query("UPDATE SharedRideRequest r SET r.sharedRide.sharedRideId = :sharedRideId, " +
                        "r.status = :status " +
                        "WHERE r.sharedRideRequestId = :requestId")
        void assignRideAndUpdateStatus(
                        @Param("requestId") Integer requestId,
                        @Param("sharedRideId") Integer sharedRideId,
                        @Param("status") SharedRideRequestStatus status);

        @Query("SELECT r FROM SharedRideRequest r " +
                        "WHERE r.status = 'CONFIRMED' " +
                        "AND r.pickupTime IS NOT NULL " +
                        "AND r.pickupTime <= :cutoff")
        List<SharedRideRequest> findConfirmedForAutoStart(@Param("cutoff") LocalDateTime cutoff);

        @Query("SELECT r FROM SharedRideRequest r " +
                        "WHERE r.status = 'ONGOING' " +
                        "AND r.actualPickupTime IS NOT NULL " +
                        "AND r.actualPickupTime <= :cutoff")
        List<SharedRideRequest> findOngoingForAutoCompletion(@Param("cutoff") LocalDateTime cutoff);

        boolean existsBySharedRideSharedRideIdAndStatusIn(Integer sharedRideId,
                        List<SharedRideRequestStatus> statuses);

        List<SharedRideRequest> findByStatus(SharedRideRequestStatus status);

        Optional<SharedRideRequest> findFirstByRiderRiderIdAndStatusOrderByCreatedAtDesc(Integer riderId,
                        SharedRideRequestStatus status);
}

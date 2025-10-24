package com.mssus.app.repository;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.entity.SharedRide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SharedRideRepository extends JpaRepository<SharedRide, Integer> {
    Page<SharedRide> findByDriverDriverIdOrderByScheduledTimeDesc(Integer driverId, Pageable pageable);

    Page<SharedRide> findByDriverDriverIdAndStatusOrderByScheduledTimeDesc(
        Integer driverId, SharedRideStatus status, Pageable pageable);

    Long countByDriverDriverIdAndStatus(Integer driverId, SharedRideStatus status);

    @Query("SELECT r FROM SharedRide r " +
        "WHERE r.status = 'SCHEDULED' OR r.status = 'ONGOING' " +
        "AND r.currentPassengers < r.maxPassengers " +
        "AND r.scheduledTime BETWEEN :startTime AND :endTime " +
        "AND r.sharedRideId IS NULL " +
        "ORDER BY r.scheduledTime ASC")
    Page<SharedRide> findAvailableRides(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SharedRide r WHERE r.sharedRideId = :sharedRideId")
    Optional<SharedRide> findByIdForUpdate(@Param("sharedRideId") Integer sharedRideId);

    @Modifying
    @Query("UPDATE SharedRide r SET r.currentPassengers = r.currentPassengers + 1 " +
        "WHERE r.sharedRideId = :sharedRideId")
    void incrementPassengerCount(@Param("sharedRideId") Integer sharedRideId);

    @Modifying
    @Query("UPDATE SharedRide r SET r.currentPassengers = r.currentPassengers - 1 " +
        "WHERE r.sharedRideId = :sharedRideId AND r.currentPassengers > 0")
    void decrementPassengerCount(@Param("sharedRideId") Integer sharedRideId);

    @Modifying
    @Query("UPDATE SharedRide r SET r.status = :status WHERE r.sharedRideId = :sharedRideId")
    void updateStatus(@Param("sharedRideId") Integer sharedRideId, @Param("status") SharedRideStatus status);

    @Modifying
    @Query("UPDATE SharedRide r SET r.actualDistance = :actualDistance, " +
        "r.actualDuration = :actualDuration WHERE r.sharedRideId = :sharedRideId")
    void updateActualMetrics(
        @Param("sharedRideId") Integer sharedRideId,
        @Param("actualDistance") Float actualDistance,
        @Param("actualDuration") Integer actualDuration);

    boolean existsByDriverDriverIdAndStatus(Integer driverId, SharedRideStatus status);

    @Query("SELECT r FROM SharedRide r " +
        "WHERE r.status = 'SCHEDULED' OR r.status = 'ONGOING'" +
        "AND r.currentPassengers < r.maxPassengers " +
        "AND r.scheduledTime BETWEEN :startTime AND :endTime")
    List<SharedRide> findCandidateRidesForMatching(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    @Query("SELECT r FROM SharedRide r WHERE r.status = 'SCHEDULED' " +
        "AND r.scheduledTime <= :cutoff")
    List<SharedRide> findScheduledAndOverdue(LocalDateTime cutoff);

    @Query("SELECT r FROM SharedRide r " +
        "WHERE r.status = 'SCHEDULED' AND r.scheduledTime <= :cutoff")
    List<SharedRide> findScheduledForAutoStart(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT r FROM SharedRide r " +
        "WHERE r.status = 'ONGOING' AND r.startedAt IS NOT NULL AND r.startedAt <= :cutoff")
    List<SharedRide> findOngoingForAutoCompletion(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT r FROM SharedRide r " +
        "WHERE r.driver.driverId = :driverId " +
        "ORDER BY r.scheduledTime DESC LIMIT 1")
    Optional<SharedRide> findLatestScheduledRideByDriverId(@Param("driverId") Integer driverId);


}


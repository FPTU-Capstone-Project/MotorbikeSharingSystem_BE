package com.mssus.app.repository;

import com.mssus.app.entity.AiMatchingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AI matching log operations.
 * Used to track and audit all AI-based matching decisions.
 *
 * @since 1.0.0 (AI Integration)
 */
@Repository
public interface AiMatchingLogRepository extends JpaRepository<AiMatchingLog, Integer> {

    /**
     * Find all logs for a specific ride request.
     */
    List<AiMatchingLog> findBySharedRideRequest_SharedRideRequestId(Integer requestId);

    /**
     * Find successful AI matching logs within a time range.
     */
    @Query("SELECT a FROM AiMatchingLog a WHERE a.success = true AND a.createdAt >= :startDate")
    List<AiMatchingLog> findSuccessfulLogsAfter(@Param("startDate") LocalDateTime startDate);

    /**
     * Count successful AI matches in the last N days.
     */
    @Query("SELECT COUNT(a) FROM AiMatchingLog a WHERE a.success = true AND a.createdAt >= :cutoffDate")
    long countSuccessfulMatchesSince(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count failed AI matches in the last N days.
     */
    @Query("SELECT COUNT(a) FROM AiMatchingLog a WHERE a.success = false AND a.createdAt >= :cutoffDate")
    long countFailedMatchesSince(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Get average processing time for AI matches.
     */
    @Query("SELECT AVG(a.processingTimeMs) FROM AiMatchingLog a WHERE a.success = true AND a.createdAt >= :cutoffDate")
    Double getAverageProcessingTime(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find logs by algorithm version (for A/B testing).
     */
    List<AiMatchingLog> findByAlgorithmVersion(String version);
}



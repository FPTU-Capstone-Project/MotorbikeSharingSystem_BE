package com.mssus.app.repository;

import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.entity.SosAlert;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SosAlertRepository extends JpaRepository<SosAlert, Integer> {

    List<SosAlert> findByTriggeredBy_UserIdAndStatusInOrderByCreatedAtDesc(Integer userId, Collection<SosAlertStatus> statuses);

    List<SosAlert> findByStatusInOrderByCreatedAtDesc(Collection<SosAlertStatus> statuses);

    Page<SosAlert> findByStatusIn(List<SosAlertStatus> statuses, Pageable pageable);

    @Query("""
        select alert from SosAlert alert
        where alert.status in :statuses
          and alert.nextEscalationAt is not null
          and alert.nextEscalationAt <= :cutoff
    """)
    List<SosAlert> findEscalationCandidates(@Param("statuses") Collection<SosAlertStatus> statuses,
                                            @Param("cutoff") LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select alert from SosAlert alert where alert.sosId = :sosId")
    Optional<SosAlert> findByIdForUpdate(@Param("sosId") Integer sosId);
}

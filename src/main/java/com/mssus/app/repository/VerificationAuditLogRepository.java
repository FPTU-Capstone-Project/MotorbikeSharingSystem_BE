package com.mssus.app.repository;

import com.mssus.app.entity.VerificationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VerificationAuditLogRepository extends JpaRepository<VerificationAuditLog, Long> {

    List<VerificationAuditLog> findByVerification_VerificationIdOrderByCreatedAtAsc(Integer verificationId);

    List<VerificationAuditLog> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime from, LocalDateTime to);
}

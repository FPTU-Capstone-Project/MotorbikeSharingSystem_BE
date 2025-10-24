package com.mssus.app.repository;

import com.mssus.app.entity.SosAlert;
import com.mssus.app.entity.SosAlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SosAlertEventRepository extends JpaRepository<SosAlertEvent, Long> {

    List<SosAlertEvent> findBySosAlertOrderByCreatedAtAsc(SosAlert sosAlert);
}

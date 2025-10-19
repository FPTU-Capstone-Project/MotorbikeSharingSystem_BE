package com.mssus.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mssus.app.entity.ReconciliationRun;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Integer> {
}




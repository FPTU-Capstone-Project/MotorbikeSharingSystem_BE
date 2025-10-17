package com.mssus.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mssus.app.entity.ReconciliationResult;

@Repository
public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Integer> {
}




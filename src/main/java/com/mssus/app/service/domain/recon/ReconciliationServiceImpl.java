package com.mssus.app.service.domain.recon;

import com.mssus.app.entity.ReconciliationResult;
import com.mssus.app.entity.ReconciliationRun;
import com.mssus.app.repository.ReconciliationResultRepository;
import com.mssus.app.repository.ReconciliationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationResultRepository resultRepository;

    @Override
    @Transactional
    public void runReconciliation() {
        ReconciliationRun run = ReconciliationRun.builder()
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .build();
        run = runRepository.save(run);

        try {
            ReconciliationResult res = ReconciliationResult.builder()
                    .run(run)
                    .ref("SAMPLE")
                    .kind("MISSING_IN_LEDGER")
                    .detail("Sample placeholder")
                    .createdAt(LocalDateTime.now())
                    .build();
            resultRepository.save(res);

            run.setStatus("SUCCESS");
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
        } catch (Exception e) {
            log.error("Reconciliation failed", e);
            run.setStatus("FAILED");
            run.setFinishedAt(LocalDateTime.now());
            run.setNotes(e.getMessage());
            runRepository.save(run);
        }
    }
}

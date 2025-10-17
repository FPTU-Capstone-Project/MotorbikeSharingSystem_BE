package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Integer resultId;

    @ManyToOne
    @JoinColumn(name = "run_id")
    private ReconciliationRun run;

    @Column(name = "ref")
    private String ref;

    @Column(name = "kind")
    private String kind;

    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

## Verification Review Workflow

Identity verification reviewers now have a dedicated workflow that supports assignment, dual review, and auditable outcomes.

### Queue Operations

- Endpoint: `GET /api/v1/verification/review/queue`
- Filters: `status`, `profileType`, `highRiskOnly`, `onlyMine`, `search`
- Sorting is risk-first (`highRisk`, `riskScore`, `createdAt`)
- Only unclaimed or self-assigned cases are returned unless `onlyMine=true`

### Assignment Actions

| Action | Endpoint | Notes |
|--------|----------|-------|
| Claim case | `POST /api/v1/verification/review/queue/{id}/claim` | Sets status to `IN_REVIEW` and records assignment audit log. |
| Release case | `POST /api/v1/verification/review/queue/{id}/release` | Clears assignment and returns case to queue. |

Each assignment triggers an in-app notification (`VERIFICATION_REVIEW_ASSIGNED`) to the reviewer.

### Decision Submission

`POST /api/v1/verification/review/decision`

Required payload:
- `verificationId`
- `outcome` (`APPROVED`, `REJECTED`, `OVERRIDE_APPROVED`)
- `decisionReason` (`DOCUMENT_MATCH`, `DOCUMENT_MISMATCH`, `FRAUD_SUSPECTED`, `INFORMATION_INCOMPLETE`, `EXPIRED_DOCUMENT`, `MANUAL_OVERRIDE`, `OTHER`)
- Optional lists: `evidenceReferences`, `annotations`
- `overrideSecondaryRequirement` + `overrideJustification` (lead reviewer bypass of dual review)

Behavior:
- High-risk approvals escalate to `AWAITING_SECONDARY_REVIEW`, notify admins (`VERIFICATION_ESCALATED`), and require a secondary reviewer.
- Secondary approvals or overrides write immutable audit entries and complete the workflow.
- Rejections capture reasons, notes, and document hashes for compliance.

### Audit Export

- Endpoint: `GET /api/v1/verification/review/audit/export?from=YYYY-MM-DD&to=YYYY-MM-DD`
- Returns CSV with verified decisions for the range.

Sample extract:

```csv
verification_id,event_type,previous_status,new_status,reviewer_id,created_at,document_hash,payload
145,DECISION_RECORDED,IN_REVIEW,AWAITING_SECONDARY_REVIEW,301,2025-01-14T05:12:43,lcS6...=,"{\"stage\":\"PRIMARY\",\"outcome\":\"APPROVED\",\"reason\":\"DOCUMENT_MATCH\"}"
145,SECONDARY_REVIEW_TRIGGERED,IN_REVIEW,AWAITING_SECONDARY_REVIEW,301,2025-01-14T05:12:43,lcS6...=,"{\"reason\":\"DOCUMENT_MATCH\"}"
145,ESCALATION_NOTIFIED,AWAITING_SECONDARY_REVIEW,AWAITING_SECONDARY_REVIEW,,2025-01-14T05:12:44,lcS6...=,"{\"recipientCount\":3}"
```

### Analytics Hooks

- `trackVerificationDecision` fired for every decision with stage, outcome, and risk context.
- `trackVerificationEscalation` fired when primary approvals escalate to secondary review.

### Reviewer UX Notes

- Cases display risk score, high-risk flag, current stage, assigned reviewer, and claimable indicator.
- Document annotations are stored alongside decisions and retrievable for future review tooling.
- Override actions require explicit justification and are tagged in the audit trail as overrides.

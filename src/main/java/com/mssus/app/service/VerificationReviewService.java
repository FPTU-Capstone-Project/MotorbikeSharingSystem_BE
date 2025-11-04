package com.mssus.app.service;

import com.mssus.app.dto.request.VerificationQueueFilter;
import com.mssus.app.dto.request.VerificationReviewDecisionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VerificationAssignmentResponse;
import com.mssus.app.dto.response.VerificationDecisionResponse;
import com.mssus.app.dto.response.VerificationQueueItemResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface VerificationReviewService {

    PageResponse<VerificationQueueItemResponse> getQueue(String reviewerEmail,
                                                         VerificationQueueFilter filter,
                                                         Pageable pageable);

    VerificationAssignmentResponse claim(String reviewerEmail, Integer verificationId);

    VerificationAssignmentResponse release(String reviewerEmail, Integer verificationId);

    VerificationDecisionResponse decide(String reviewerEmail, VerificationReviewDecisionRequest request);

    Resource exportAudit(LocalDate from, LocalDate to);
}

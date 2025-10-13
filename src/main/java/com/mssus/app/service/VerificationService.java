package com.mssus.app.service;

import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface VerificationService {

    StudentVerificationResponse getStudentVerificationById(Integer userId);
    DriverKycResponse getDriverKycById(Integer driverId);
    DriverStatsResponse getDriverVerificationStats();
    PageResponse<VerificationResponse> getAllVerifications(Pageable pageable);
    BulkOperationResponse bulkApproveVerifications(String admin, BulkApprovalRequest request);
    MessageResponse approveVerification(String admin, VerificationDecisionRequest request);
    MessageResponse rejectVerification(String admin, VerificationDecisionRequest request);
    MessageResponse updateBackgroundCheck(String admin, Integer driverId, BackgroundCheckRequest request);
}
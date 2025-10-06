package com.mssus.app.service;

import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface VerificationService {

    StudentVerificationResponse getStudentVerificationById(Integer userId);
    BulkOperationResponse bulkApproveVerifications(String admin, BulkApprovalRequest request);

    MessageResponse approveVerification(String admin, VerificationDecisionRequest request);
    DriverKycResponse getDriverKycById(Integer driverId);
    MessageResponse approveDriverVehicle(String admin, VerificationDecisionRequest request);
    MessageResponse rejectVerification(String admin, VerificationDecisionRequest request);
    MessageResponse updateBackgroundCheck(String admin, Integer driverId, BackgroundCheckRequest request);
    DriverStatsResponse getDriverVerificationStats();
    PageResponse<VerificationResponse> getAllVerifications(Pageable pageable);

}
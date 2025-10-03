package com.mssus.app.service;

import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface VerificationService {

    // Student verification methods
    PageResponse<StudentVerificationResponse> getPendingStudentVerifications(Pageable pageable);
    StudentVerificationResponse getStudentVerificationById(Integer userId);
    MessageResponse approveStudentVerification(String admin, Integer userId, VerificationDecisionRequest request);
    MessageResponse rejectStudentVerification(Integer userId, VerificationDecisionRequest request);
    PageResponse<StudentVerificationResponse> getStudentVerificationHistory(Pageable pageable);
    BulkOperationResponse bulkApproveStudentVerifications(BulkApprovalRequest request);

    // Driver verification methods
    PageResponse<DriverKycResponse> getPendingDriverVerifications(Pageable pageable);
    DriverKycResponse getDriverKycById(Integer driverId);
    MessageResponse approveDriverDocuments(Integer driverId, VerificationDecisionRequest request);
    MessageResponse approveDriverLicense(Integer driverId, VerificationDecisionRequest request);
    MessageResponse approveDriverVehicle(Integer driverId, VerificationDecisionRequest request);
    MessageResponse rejectDriverVerification(Integer driverId, VerificationDecisionRequest request);
    MessageResponse updateBackgroundCheck(Integer driverId, BackgroundCheckRequest request);
    DriverStatsResponse getDriverVerificationStats();

    // Common verification methods
    VerificationResponse getVerificationById(Integer verificationId);
    PageResponse<VerificationResponse> getAllVerifications(Pageable pageable);
    PageResponse<VerificationResponse> getAllPendingVerifications(Pageable pageable);
    VerificationResponse approveVerification(Integer verificationId);

}

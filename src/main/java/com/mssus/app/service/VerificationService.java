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
    MessageResponse rejectStudentVerification(String admin,Integer userId, VerificationDecisionRequest request);
    PageResponse<StudentVerificationResponse> getStudentVerificationHistory(Pageable pageable);
    BulkOperationResponse bulkApproveStudentVerifications(String admin, BulkApprovalRequest request);

    // Driver verification methods
    PageResponse<DriverKycResponse> getPendingDriverVerifications(Pageable pageable);
    DriverKycResponse getDriverKycById(Integer driverId);
    MessageResponse approveDriverDocuments(String admin, Integer driverId, VerificationDecisionRequest request);
    MessageResponse approveDriverLicense(String admin, Integer driverId, VerificationDecisionRequest request);
    MessageResponse approveDriverVehicle(String admin, Integer driverId, VerificationDecisionRequest request);
    MessageResponse rejectDriverVerification(String admin, Integer driverId, VerificationDecisionRequest request);
    MessageResponse updateBackgroundCheck(String admin, Integer driverId, BackgroundCheckRequest request);
    DriverStatsResponse getDriverVerificationStats();

    // Common verification methods
    VerificationResponse getVerificationById(Integer verificationId);
    PageResponse<VerificationResponse> getAllVerifications(Pageable pageable);
    PageResponse<VerificationResponse> getAllPendingVerifications(Pageable pageable);
    VerificationResponse approveVerification(String admin, Integer verificationId);

}
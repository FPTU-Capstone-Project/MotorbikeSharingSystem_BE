package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mssus.app.dto.sos.EmergencyContactResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User response with profile details")
public class UserResponse {

    @JsonProperty("user_id")
    @Schema(description = "User ID", example = "1")
    private Integer userId;

    @Schema(description = "Email address", example = "user@example.com")
    private String email;

    @Schema(description = "Phone number", example = "+84912345678")
    private String phone;

    @JsonProperty("full_name")
    @Schema(description = "Full name", example = "John Doe")
    private String fullName;

    @JsonProperty("student_id")
    @Schema(description = "Student ID", example = "SE123456")
    private String studentId;

    @JsonProperty("user_type")
    @Schema(description = "User type", example = "USER")
    private String userType;

    @JsonProperty("profile_photo_url")
    @Schema(description = "Profile photo URL")
    private String profilePhotoUrl;

    @JsonProperty("email_verified")
    @Schema(description = "Email verification status", example = "true")
    private Boolean emailVerified;

    @JsonProperty("phone_verified")
    @Schema(description = "Phone verification status", example = "true")
    private Boolean phoneVerified;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @JsonProperty("created_at")
    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @JsonProperty("rider_profile")
    @Schema(description = "Rider profile (if exists)")
    private RiderProfileInfo riderProfile;

    @JsonProperty("driver_profile")
    @Schema(description = "Driver profile (if exists)")
    private DriverProfileInfo driverProfile;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Rider profile information")
    public static class RiderProfileInfo {
        @JsonProperty("rider_id")
        @Schema(description = "Rider ID", example = "1")
        private Integer riderId;

        @JsonProperty("emergency_contacts")
        @Schema(description = "Emergency contacts configured by the rider")
        private List<EmergencyContactResponse> emergencyContacts;

        @JsonProperty("total_rides")
        @Schema(description = "Total completed rides", example = "25")
        private Integer totalRides;

        @JsonProperty("total_spent")
        @Schema(description = "Total amount spent", example = "500000")
        private BigDecimal totalSpent;

        @Schema(description = "Profile status", example = "ACTIVE")
        private String status;

        @JsonProperty("preferred_payment_method")
        @Schema(description = "Preferred payment method", example = "WALLET")
        private String preferredPaymentMethod;

        @JsonProperty("created_at")
        @Schema(description = "Profile creation timestamp")
        private LocalDateTime createdAt;

        @JsonProperty("suspended_at")
        @Schema(description = "Suspension timestamp (if applicable)")
        private LocalDateTime suspendedAt;

        @JsonProperty("activated_at")
        @Schema(description = "Activation timestamp (if applicable)")
        private LocalDateTime activatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Driver profile information")
    public static class DriverProfileInfo {
        @JsonProperty("driver_id")
        @Schema(description = "Driver ID", example = "2")
        private Integer driverId;

        @JsonProperty("license_number")
        @Schema(description = "License number", example = "LIC123456")
        private String licenseNumber;

        @JsonProperty("license_verified_at")
        @Schema(description = "License verification timestamp")
        private LocalDateTime licenseVerifiedAt;

        @Schema(description = "Driver status", example = "ACTIVE")
        private String status;

        @JsonProperty("rating_avg")
        @Schema(description = "Average rating", example = "4.8")
        private Float ratingAvg;

        @JsonProperty("total_shared_rides")
        @Schema(description = "Total shared rides", example = "50")
        private Integer totalSharedRides;

        @JsonProperty("total_earned")
        @Schema(description = "Total earnings", example = "1500000")
        private BigDecimal totalEarned;

        @JsonProperty("commission_rate")
        @Schema(description = "Commission rate", example = "0.15")
        private BigDecimal commissionRate;

        @JsonProperty("is_available")
        @Schema(description = "Availability status", example = "true")
        private Boolean isAvailable;

        @JsonProperty("max_passengers")
        @Schema(description = "Maximum passengers", example = "1")
        private Integer maxPassengers;

        @JsonProperty("created_at")
        @Schema(description = "Profile creation timestamp")
        private LocalDateTime createdAt;

        @JsonProperty("suspended_at")
        @Schema(description = "Suspension timestamp (if applicable)")
        private LocalDateTime suspendedAt;

        @JsonProperty("activated_at")
        @Schema(description = "Activation timestamp (if applicable)")
        private LocalDateTime activatedAt;
    }
}

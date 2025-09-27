package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User profile response")
public class UserProfileResponse {

    @Schema(description = "User information")
    private UserInfo user;

    @JsonProperty("rider_profile")
    @Schema(description = "Rider profile (if exists)")
    private RiderProfile riderProfile;

    @JsonProperty("driver_profile")
    @Schema(description = "Driver profile (if exists)")
    private DriverProfile driverProfile;

    @JsonProperty("available_profiles")
    @Schema(description = "List of available profiles for the user")
    private List<String> availableProfiles;

    @JsonProperty("active_profile")
    @Schema(description = "Currently active profile")
    private String activeProfile;


    @Schema(description = "Wallet information")
    private WalletInfo wallet;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "User basic information")
    public static class UserInfo {
        @JsonProperty("user_id")
        private Integer userId;

        @JsonProperty("user_type")
        private String userType;

        private String email;
        private String phone;

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("student_id")
        private String studentId;

        @JsonProperty("profile_photo_url")
        private String profilePhotoUrl;

        @JsonProperty("status")
        private String status;

        @JsonProperty("email_verified")
        private Boolean emailVerified;

        @JsonProperty("phone_verified")
        private Boolean phoneVerified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Rider profile information")
    public static class RiderProfile {
        @JsonProperty("emergency_contact")
        private String emergencyContact;

        @JsonProperty("rating_avg")
        private Float ratingAvg;

        @JsonProperty("total_rides")
        private Integer totalRides;

        @JsonProperty("total_spent")
        private BigDecimal totalSpent;

        @JsonProperty("preferred_payment_method")
        private String preferredPaymentMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Driver profile information")
    public static class DriverProfile {
        @JsonProperty("license_number")
        private String licenseNumber;

        private String status;

        @JsonProperty("rating_avg")
        private Float ratingAvg;

        @JsonProperty("total_shared_rides")
        private Integer totalSharedRides;

        @JsonProperty("total_earned")
        private BigDecimal totalEarned;

        @JsonProperty("commission_rate")
        private BigDecimal commissionRate;

        @JsonProperty("is_available")
        private Boolean isAvailable;

        @JsonProperty("max_passengers")
        private Integer maxPassengers;
    }

//    @Data
//    @Builder
//    @NoArgsConstructor
//    @AllArgsConstructor
//    @Schema(description = "Admin profile information")
//    public static class AdminProfile {
//        private String department;
//        private String permissions;
//    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Wallet information")
    public static class WalletInfo {
        @JsonProperty("wallet_id")
        private Integer walletId;

        @JsonProperty("cached_balance")
        private BigDecimal cachedBalance;

        @JsonProperty("pending_balance")
        private BigDecimal pendingBalance;

        @JsonProperty("is_active")
        private Boolean isActive;
    }
}

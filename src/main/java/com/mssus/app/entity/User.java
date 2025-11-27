package com.mssus.app.entity;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.enums.UserType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_phone", columnList = "phone")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "phone", unique = true)
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "student_id", unique = true)
    private String studentId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String gender;

    @Column(name = "user_type", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserType userType = UserType.USER;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "phone_verified")
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 1;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // One-to-One relationships
    @OneToOne(mappedBy = "user")
    private RiderProfile riderProfile;

    @OneToOne(mappedBy = "user")
    private DriverProfile driverProfile;

    @OneToOne(mappedBy = "user")
    private Wallet wallet;
    

    // Helper method to check if user has a specific role
    public boolean hasProfile(String profile) {
        return switch (profile.toLowerCase()) {
            case "rider" -> riderProfile != null;
            case "driver" -> driverProfile != null;
            default -> false;
        };
    }

    public boolean isProfileActive(String profile) {
        return switch (profile.toLowerCase()) {
            case "rider" -> riderProfile != null && RiderProfileStatus.ACTIVE.equals(riderProfile.getStatus());
            case "driver" -> driverProfile != null && DriverProfileStatus.ACTIVE.equals(driverProfile.getStatus());
            default -> false;
        };
    }

    // Helper method to get the primary role
//    public String getPrimaryRole() {
//        if (adminProfile != null) {
//            return "ADMIN";
//        }
//        if (driverProfile != null && DriverProfileStatus.ACTIVE.equals(driverProfile.getStatus())) {
//            return "DRIVER";
//        }
//        if (riderProfile != null && RiderProfileStatus.ACTIVE.equals(riderProfile.getStatus())) {
//            return "RIDER";
//        }
//        return "USER";
//    }

    public void incrementTokenVersion() {
        this.tokenVersion = this.tokenVersion + 1;
    }
}

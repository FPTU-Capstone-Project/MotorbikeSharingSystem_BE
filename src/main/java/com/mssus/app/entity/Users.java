package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "phone", unique = true, nullable = false)
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "student_id", unique = true)
    private String studentId;

    @Column(name = "user_type", length = 20)
    @Builder.Default
    private String userType = "student";

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "phone_verified")
    @Builder.Default
    private Boolean phoneVerified = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // One-to-One relationships
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RiderProfile riderProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private DriverProfile driverProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AdminProfile adminProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Wallet wallet;
    @Version
    private Long version;

    // Helper method to check if user has a specific role
    public boolean hasRole(String role) {
        switch (role.toLowerCase()) {
            case "rider":
                return riderProfile != null;
            case "driver":
                return driverProfile != null && driverProfile.getStatus() != null && driverProfile.getStatus().equals("active");
            case "admin":
                return adminProfile != null;
            default:
                return false;
        }
    }

    // Helper method to get the primary role
    public String getPrimaryRole() {
        if (adminProfile != null) {
            return "ADMIN";
        }
        if (driverProfile != null && "active".equals(driverProfile.getStatus())) {
            return "DRIVER";
        }
        if (riderProfile != null) {
            return "RIDER";
        }
        return "USER";
    }
}

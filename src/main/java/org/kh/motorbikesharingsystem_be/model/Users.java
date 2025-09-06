package org.kh.motorbikesharingsystem_be.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String email;
    private String passwordHash;
    private String phone;
    private String fullName;
    private LocalDate createdAt;
    private LocalDate updatedAt;
    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role {
        USER, ADMIN, STAFF
    }
}

package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_id")
    private Integer vehicleId;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverProfile driver;

    @Column(name = "plate_number")
    private String plateNumber;

    @Column(name = "model")
    private String model;

    @Column(name = "color")
    private String color;

    @Column(name = "year")
    private Integer year;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "insurance_expiry")
    private LocalDateTime insuranceExpiry;

    @Column(name = "last_maintenance")
    private LocalDateTime lastMaintenance;

    @Column(name = "fuel_type")
    private String fuelType;

    @Column(name = "status")
    private String status;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

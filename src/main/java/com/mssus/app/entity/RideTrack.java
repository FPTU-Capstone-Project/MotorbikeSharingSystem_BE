package com.mssus.app.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.mssus.app.dto.ride.LocationPoint;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ride_track")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer rideTrackId;

    @OneToOne
    @JoinColumn(name = "shared_ride_id", nullable = false)
    private SharedRide sharedRide;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gps_points", nullable = false, columnDefinition = "jsonb")
    private JsonNode gpsPoints;  // Array: [{"lat": 10.84, "lng": 106.81, "timestamp": "2025-10-13T13:00:00"}]

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();


    public void addGpsPoints(List<LocationPoint> points) {
    }
}

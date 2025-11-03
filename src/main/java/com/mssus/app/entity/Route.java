package com.mssus.app.entity;

import com.mssus.app.common.enums.RouteType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "routes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Integer routeId;

    @Column(name = "name", length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", nullable = false, length = 20)
    private RouteType routeType = RouteType.CUSTOM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @Column(name = "default_price", precision = 19, scale = 2)
    private BigDecimal defaultPrice;

    @Column(name = "polyline", columnDefinition = "TEXT")
    private String polyline;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "distance_meters")
    private Long distanceMeters;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

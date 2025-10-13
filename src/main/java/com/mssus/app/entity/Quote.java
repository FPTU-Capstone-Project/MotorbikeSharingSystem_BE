//package com.mssus.app.entity;
//
//import jakarta.persistence.*;
//import lombok.*;
//
//import java.time.OffsetDateTime;
//import java.util.UUID;
//
//@Entity
//@Table(name = "quotes")
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//public class Quote {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    @Column(name = "quote_id", nullable = false)
//    private UUID quoteId;
//
//    @Column(name = "rider_id", nullable = false)
//    private Long riderId;
//
//    @Column(name = "pickup_lat", nullable = false)
//    private Double pickupLat;
//
//    @Column(name = "pickup_lng", nullable = false)
//    private Double pickupLng;
//
//    @Column(name = "dropoff_lat", nullable = false)
//    private Double dropoffLat;
//
//    @Column(name = "dropoff_lng", nullable = false)
//    private Double dropoffLng;
//
//    @Column(name = "distance_m", nullable = false)
//    private Integer distanceM;
//
//    @Column(name = "duration_s", nullable = false)
//    private Integer durationS;
//
//    @Column(name = "polyline", nullable = false, columnDefinition = "TEXT")
//    private String polyline;
//
//    @ManyToOne
//    @JoinColumn(name = "pricing_version", referencedColumnName = "version", nullable = false)
//    private PricingConfig pricingConfig;
//
//    @Column(name = "base_flag_vnd", nullable = false)
//    private Long baseFlagVnd;
//
//    @Column(name = "per_km_component_vnd", nullable = false)
//    private Long perKmComponentVnd;
//
//    @Column(name = "per_min_component_vnd", nullable = false)
//    private Long perMinComponentVnd;
//
//    @Column(name = "surcharge_vnd", nullable = false)
//    private Long surchargeVnd;
//
//    @Column(name = "discount_vnd", nullable = false)
//    private Long discountVnd;
//
//    @Column(name = "subtotal_vnd", nullable = false)
//    private Long subtotalVnd;
//
//    @Column(name = "total_vnd", nullable = false)
//    private Long totalVnd;
//
//    @Column(name = "created_at", nullable = false)
//    private OffsetDateTime createdAt;
//
//    @Column(name = "expires_at", nullable = false)
//    private OffsetDateTime expiresAt;
//}

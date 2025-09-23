package com.mssus.app.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
@Entity
@Table(name = "ratings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rating_id")
    private Integer ratingId;

    @ManyToOne
    @JoinColumn(name = "shared_ride_request_id", nullable = false)
    private SharedRideRequest sharedRideRequest;

    @ManyToOne
    @JoinColumn(name = "rater_id", nullable = false)
    private RiderProfile rater;

    @ManyToOne
    @JoinColumn(name = "target_id", nullable = false)
    private DriverProfile target;

    @Column(name = "rating_type")
    private String ratingType;

    @Column(name = "score")
    private Integer score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "safety_score")
    private Integer safetyScore;

    @Column(name = "punctuality_score")
    private Integer punctualityScore;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

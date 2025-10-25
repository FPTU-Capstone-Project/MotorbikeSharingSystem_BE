package com.mssus.app.repository;

import com.mssus.app.entity.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, Integer> {

    boolean existsBySharedRideRequestSharedRideRequestIdAndRaterRiderId(Integer sharedRideRequestId, Integer riderId);

    Page<Rating> findByTargetDriverIdOrderByCreatedAtDesc(Integer driverId, Pageable pageable);

    Page<Rating> findByRaterRiderIdOrderByCreatedAtDesc(Integer riderId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM Rating r WHERE r.target.driverId = :driverId")
    Double calculateAverageScoreForDriver(@Param("driverId") Integer driverId);
}

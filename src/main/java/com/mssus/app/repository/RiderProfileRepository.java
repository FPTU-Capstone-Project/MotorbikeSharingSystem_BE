package com.mssus.app.repository;

import com.mssus.app.entity.RiderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface RiderProfileRepository extends JpaRepository<RiderProfile, Integer> {

    Optional<RiderProfile> findByUserUserId(Integer userId);

    @Modifying
    @Query("UPDATE RiderProfile r SET r.totalRides = r.totalRides + 1, " +
           "r.totalSpent = r.totalSpent + :amount WHERE r.riderId = :riderId")
    void updateRideStats(@Param("riderId") Integer riderId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE RiderProfile r SET r.ratingAvg = :rating WHERE r.riderId = :riderId")
    void updateRating(@Param("riderId") Integer riderId, @Param("rating") Float rating);
}

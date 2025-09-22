package com.mssus.app.repository;

import com.mssus.app.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, Integer> {

    Optional<DriverProfile> findByUserUserId(Integer userId);

    Optional<DriverProfile> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    List<DriverProfile> findByStatus(String status);

    List<DriverProfile> findByIsAvailable(Boolean isAvailable);

    @Modifying
    @Query("UPDATE DriverProfile d SET d.totalSharedRides = d.totalSharedRides + 1, " +
           "d.totalEarned = d.totalEarned + :amount WHERE d.driverId = :driverId")
    void updateRideStats(@Param("driverId") Integer driverId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE DriverProfile d SET d.ratingAvg = :rating WHERE d.driverId = :driverId")
    void updateRating(@Param("driverId") Integer driverId, @Param("rating") Float rating);

    @Modifying
    @Query("UPDATE DriverProfile d SET d.status = :status WHERE d.driverId = :driverId")
    void updateStatus(@Param("driverId") Integer driverId, @Param("status") String status);

    @Modifying
    @Query("UPDATE DriverProfile d SET d.isAvailable = :available WHERE d.driverId = :driverId")
    void updateAvailability(@Param("driverId") Integer driverId, @Param("available") Boolean available);
}

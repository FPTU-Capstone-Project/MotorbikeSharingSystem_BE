package com.mssus.app.repository;

import com.mssus.app.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Integer> {

    boolean existsByPlateNumber(String plateNumber);

    Optional<Vehicle> findByDriver_DriverId(Integer driverProfileId);
    Page<Vehicle> findByStatus(String status, Pageable pageable);

    Page<Vehicle> findByDriverDriverId(Integer driverId, Pageable pageable);

    @Query("SELECT v FROM Vehicle v JOIN FETCH v.driver WHERE v.vehicleId = :vehicleId")
    Optional<Vehicle> findByIdWithDriver(@Param("vehicleId") Integer vehicleId);
}
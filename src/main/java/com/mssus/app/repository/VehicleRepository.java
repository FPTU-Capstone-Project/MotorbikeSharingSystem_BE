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

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    boolean existsByPlateNumber(String plateNumber);

    List<Vehicle> findByDriverDriverId(Integer driverId);

    Page<Vehicle> findByStatus(String status, Pageable pageable);

    Page<Vehicle> findByDriverDriverId(Integer driverId, Pageable pageable);

    @Query("SELECT v FROM Vehicle v JOIN FETCH v.driver WHERE v.vehicleId = :vehicleId")
    Optional<Vehicle> findByIdWithDriver(@Param("vehicleId") Integer vehicleId);

    @Query("SELECT v FROM Vehicle v JOIN FETCH v.driver WHERE v.status = :status")
    List<Vehicle> findByStatusWithDriver(@Param("status") String status);

    @Query("SELECT COUNT(v) FROM Vehicle v WHERE v.driver.driverId = :driverId AND v.status = :status")
    long countByDriverIdAndStatus(@Param("driverId") Integer driverId, @Param("status") String status);
}
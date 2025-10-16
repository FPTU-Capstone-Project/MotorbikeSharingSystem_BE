package com.mssus.app.repository;

import com.mssus.app.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Integer> {

    List<Location> findByNameContainingIgnoreCase(String name);

    Optional<Location> findByLatAndLng(Double lat, Double lng);

    @Query("SELECT l FROM Location l " +
           "WHERE l.lat BETWEEN :minLat AND :maxLat " +
           "AND l.lng BETWEEN :minLng AND :maxLng")
    List<Location> findLocationsInBoundingBox(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    boolean existsById(Integer locationId);
}


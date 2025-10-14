package com.mssus.app.repository;

import com.mssus.app.entity.RideTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RideTrackRepository extends JpaRepository<RideTrack, Integer> {
    Optional<RideTrack> findBySharedRideSharedRideId(Integer sharedRideId);
}

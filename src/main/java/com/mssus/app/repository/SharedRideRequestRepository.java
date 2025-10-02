package com.mssus.app.repository;

import com.mssus.app.entity.SharedRideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedRideRequestRepository extends JpaRepository<SharedRideRequest, Integer> {

}

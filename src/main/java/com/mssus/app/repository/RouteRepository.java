package com.mssus.app.repository;

import com.mssus.app.common.enums.RouteType;
import com.mssus.app.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Integer> {
    List<Route> findByRouteType(RouteType routeType);

    Page<Route> findByRouteType(RouteType routeType, Pageable pageable);
}

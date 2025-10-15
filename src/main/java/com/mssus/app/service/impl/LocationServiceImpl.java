package com.mssus.app.service.impl;

import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.entity.Location;
import com.mssus.app.mapper.LocationMapper;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class LocationServiceImpl implements LocationService {
    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

    @Override
    public List<LocationResponse> getAppPOIs() {
        List<Location> locations = locationRepository.findAll();

        return locations.stream()
            .map(locationMapper::toResponse)
            .toList();
    }
}

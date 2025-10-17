package com.mssus.app.service;

import com.mssus.app.dto.request.CreateVehicleRequest;
import com.mssus.app.dto.request.UpdateVehicleRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VehicleResponse;
import org.springframework.data.domain.Pageable;

public interface VehicleService {

    VehicleResponse createVehicle(CreateVehicleRequest request);

    VehicleResponse getVehicleById(Integer vehicleId);

    VehicleResponse updateVehicle(Integer vehicleId, UpdateVehicleRequest request);

    MessageResponse deleteVehicle(Integer vehicleId);

    PageResponse<VehicleResponse> getAllVehicles(Pageable pageable);

    PageResponse<VehicleResponse> getVehiclesByDriverId(String driver, Pageable pageable);

    PageResponse<VehicleResponse> getVehiclesByStatus(String status, Pageable pageable);
}

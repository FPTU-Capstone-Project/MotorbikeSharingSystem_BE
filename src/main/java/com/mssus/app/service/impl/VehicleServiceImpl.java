package com.mssus.app.service.impl;

import com.mssus.app.common.enums.FuelType;
import com.mssus.app.common.enums.VehicleStatus;
import com.mssus.app.dto.request.CreateVehicleRequest;
import com.mssus.app.dto.request.UpdateVehicleRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VehicleResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Vehicle;
import com.mssus.app.common.exception.ConflictException;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.VehicleRepository;
import com.mssus.app.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;

    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        if (vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw ConflictException.of("Vehicle with plate number " + request.getPlateNumber() + " already exists");
        }

        DriverProfile driver = driverProfileRepository.findById(request.getDriverId())
                .orElseThrow(() -> NotFoundException.resourceNotFound("Driver", "ID " + request.getDriverId()));

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .plateNumber(request.getPlateNumber())
                .model(request.getModel())
                .color(request.getColor())
                .year(request.getYear())
                .capacity(request.getCapacity())
                .insuranceExpiry(request.getInsuranceExpiry())
                .lastMaintenance(request.getLastMaintenance())
                .fuelType(FuelType.valueOf(request.getFuelType()))
                .status(VehicleStatus.valueOf(request.getStatus()))
                .build();

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return mapToVehicleResponse(savedVehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Integer vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdWithDriver(vehicleId)
                .orElseThrow(() -> NotFoundException.resourceNotFound("Vehicle", "ID " + vehicleId));
        return mapToVehicleResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Integer vehicleId, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> NotFoundException.resourceNotFound("Vehicle", "ID " + vehicleId));

        if (request.getPlateNumber() != null &&
            !request.getPlateNumber().equals(vehicle.getPlateNumber()) &&
            vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw ConflictException.of("Vehicle with plate number " + request.getPlateNumber() + " already exists");
        }

        updateVehicleFields(vehicle, request);
        Vehicle updatedVehicle = vehicleRepository.save(vehicle);
        return mapToVehicleResponse(updatedVehicle);
    }

    @Override
    @Transactional
    public MessageResponse deleteVehicle(Integer vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> NotFoundException.resourceNotFound("Vehicle", "ID " + vehicleId));

        vehicleRepository.delete(vehicle);
        return MessageResponse.builder()
                .message("Vehicle deleted successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getAllVehicles(Pageable pageable) {
        Page<Vehicle> vehiclePage = vehicleRepository.findAll(pageable);
        List<VehicleResponse> vehicles = vehiclePage.getContent().stream()
                .map(this::mapToVehicleResponse)
                .toList();

        return PageResponse.<VehicleResponse>builder()
                .data(vehicles)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(vehiclePage.getNumber() +1)
                        .pageSize(vehiclePage.getSize())
                        .totalPages(vehiclePage.getTotalPages())
                        .totalRecords(vehiclePage.getTotalElements())
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getVehiclesByDriverId(Integer driverId, Pageable pageable) {
        Page<Vehicle> vehiclePage = vehicleRepository.findByDriverDriverId(driverId, pageable);
        List<VehicleResponse> vehicles = vehiclePage.getContent().stream()
                .map(this::mapToVehicleResponse)
                .toList();

        return PageResponse.<VehicleResponse>builder()
                .data(vehicles)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(vehiclePage.getNumber() +1)
                        .pageSize(vehiclePage.getSize())
                        .totalPages(vehiclePage.getTotalPages())
                        .totalRecords(vehiclePage.getTotalElements())
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getVehiclesByStatus(String status, Pageable pageable) {
        Page<Vehicle> vehiclePage = vehicleRepository.findByStatus(status, pageable);
        List<VehicleResponse> vehicles = vehiclePage.getContent().stream()
                .map(this::mapToVehicleResponse)
                .toList();

        return PageResponse.<VehicleResponse>builder()
                .data(vehicles)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(vehiclePage.getNumber() +1)
                        .pageSize(vehiclePage.getSize())
                        .totalPages(vehiclePage.getTotalPages())
                        .totalRecords(vehiclePage.getTotalElements())
                        .build())
                .build();
    }

    private void updateVehicleFields(Vehicle vehicle, UpdateVehicleRequest request) {
        if (request.getPlateNumber() != null) {
            vehicle.setPlateNumber(request.getPlateNumber());
        }
        if (request.getModel() != null) {
            vehicle.setModel(request.getModel());
        }
        if (request.getColor() != null) {
            vehicle.setColor(request.getColor());
        }
        if (request.getYear() != null) {
            vehicle.setYear(request.getYear());
        }
        if (request.getCapacity() != null) {
            vehicle.setCapacity(request.getCapacity());
        }
        if (request.getInsuranceExpiry() != null) {
            vehicle.setInsuranceExpiry(request.getInsuranceExpiry());
        }
        if (request.getLastMaintenance() != null) {
            vehicle.setLastMaintenance(request.getLastMaintenance());
        }
        if (request.getFuelType() != null) {
            vehicle.setFuelType(FuelType.valueOf(request.getFuelType()));
        }
        if (request.getStatus() != null) {
            vehicle.setStatus(VehicleStatus.valueOf(request.getStatus()));
        }
    }

    private VehicleResponse mapToVehicleResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .vehicleId(vehicle.getVehicleId())
                .driverId(vehicle.getDriver().getDriverId())
                .plateNumber(vehicle.getPlateNumber())
                .model(vehicle.getModel())
                .color(vehicle.getColor())
                .year(vehicle.getYear())
                .capacity(vehicle.getCapacity())
                .insuranceExpiry(vehicle.getInsuranceExpiry())
                .lastMaintenance(vehicle.getLastMaintenance())
                .fuelType(vehicle.getFuelType().name())
                .status(vehicle.getStatus().name())
                .verifiedAt(vehicle.getVerifiedAt())
                .createdAt(vehicle.getCreatedAt())
                .build();
    }
}

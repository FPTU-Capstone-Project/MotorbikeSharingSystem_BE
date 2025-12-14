package com.mssus.app.service.impl;

import com.mssus.app.common.enums.FuelType;
import com.mssus.app.common.enums.VehicleStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.CreateVehicleRequest;
import com.mssus.app.dto.request.UpdateVehicleRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VehicleResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Vehicle;
import com.mssus.app.common.exception.ConflictException;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.mapper.VehicleMapper;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VehicleRepository;
import com.mssus.app.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleMapper vehicleMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request) {
        if (vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw new ConflictException("Phương tiện với biển số " + request.getPlateNumber() + " đã tồn tại");
        }

        DriverProfile driver = driverProfileRepository.findById(request.getDriverId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế với ID: " + request.getDriverId()));

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .plateNumber(request.getPlateNumber().trim())
                .model(request.getModel().trim())
                .color(request.getColor() != null ? request.getColor().trim() : null)
                .year(request.getYear())
                .capacity(request.getCapacity())
                .insuranceExpiry(request.getInsuranceExpiry())
                .lastMaintenance(request.getLastMaintenance())
                .fuelType(resolveFuelType(request.getFuelType()))
                .status(resolveVehicleStatus(request.getStatus()))
                .build();

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return mapVehicleResponse(savedVehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Integer vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdWithDriver(vehicleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phương tiện với ID: " + vehicleId));
        return mapVehicleResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Integer vehicleId, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phương tiện với ID: " + vehicleId));

        if (request.getPlateNumber() != null &&
            !request.getPlateNumber().equals(vehicle.getPlateNumber()) &&
            vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw new ConflictException("Phương tiện với biển số " + request.getPlateNumber() + " đã tồn tại");
        }

        updateVehicleFields(vehicle, request);
        Vehicle updatedVehicle = vehicleRepository.save(vehicle);
        return mapVehicleResponse(updatedVehicle);
    }

    @Override
    @Transactional
    public MessageResponse deleteVehicle(Integer vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phương tiện với ID: " + vehicleId));
        vehicle.setStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(vehicle);
        return MessageResponse.builder()
                .message("Xóa phương tiện thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> getAllVehicles(Pageable pageable) {
        Page<Vehicle> vehiclePage = vehicleRepository.findAll(pageable);
        List<VehicleResponse> vehicles = vehiclePage.getContent().stream()
                .map(this::mapVehicleResponse)
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
    public PageResponse<VehicleResponse> getVehiclesByDriverId(String driver, Pageable pageable) {
        User users = userRepository.findByEmail(driver).orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với email: " + driver));
        Integer driverId = users.getDriverProfile().getDriverId();
        Page<Vehicle> vehiclePage = vehicleRepository.findByDriverDriverId(driverId, pageable);
        List<VehicleResponse> vehicles = vehiclePage.getContent().stream()
                .map(this::mapVehicleResponse)
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
                .map(this::mapVehicleResponse)
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
            vehicle.setPlateNumber(request.getPlateNumber().trim());
        }
        if (request.getModel() != null) {
            vehicle.setModel(request.getModel().trim());
        }
        if (request.getColor() != null) {
            vehicle.setColor(request.getColor().trim());
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
            vehicle.setFuelType(resolveFuelType(request.getFuelType()));
        }
        if (request.getStatus() != null) {
            vehicle.setStatus(resolveVehicleStatus(request.getStatus()));
        }
    }

    private FuelType resolveFuelType(String rawFuelType) {
        if (rawFuelType == null || rawFuelType.trim().isEmpty()) {
            throw BaseDomainException.validation("Loại nhiên liệu là bắt buộc");
        }
        String candidate = rawFuelType.trim().toUpperCase(Locale.ROOT);
        try {
            return FuelType.valueOf(candidate);
        } catch (IllegalArgumentException ex) {
            throw BaseDomainException.validation("Loại nhiên liệu không hợp lệ: " + rawFuelType);
        }
    }

    private VehicleStatus resolveVehicleStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return VehicleStatus.PENDING;
        }

        String candidate = rawStatus.trim().toUpperCase(Locale.ROOT);
        try {
            return VehicleStatus.valueOf(candidate);
        } catch (IllegalArgumentException ex) {
            throw BaseDomainException.validation("Trạng thái phương tiện không hợp lệ: " + rawStatus);
        }
    }

    private VehicleResponse mapVehicleResponse(Vehicle vehicle) {
        VehicleResponse response = vehicleMapper.mapToVehicleResponse(vehicle);
        if (response == null) {
            return null;
        }

        response.setVehicleId(vehicle.getVehicleId());
        response.setDriverId(vehicle.getDriver() != null ? vehicle.getDriver().getDriverId() : null);
        response.setPlateNumber(vehicle.getPlateNumber());
        response.setModel(vehicle.getModel());
        response.setColor(vehicle.getColor());
        response.setYear(vehicle.getYear());
        response.setCapacity(vehicle.getCapacity());
        response.setInsuranceExpiry(vehicle.getInsuranceExpiry());
        response.setLastMaintenance(vehicle.getLastMaintenance());
        response.setFuelType(vehicle.getFuelType() != null ? vehicle.getFuelType().name() : null);
        response.setVehicleStatus(vehicle.getStatus() != null ? vehicle.getStatus().name().toLowerCase(Locale.ROOT) : null);
        response.setCreatedAt(vehicle.getCreatedAt());

        if (vehicle.getDriver() != null) {
            DriverProfile driver = vehicle.getDriver();
            User user = driver.getUser();
            if (user != null) {
                response.setDriverName(user.getFullName());
                response.setDriverPhone(user.getPhone());
                response.setDriverEmail(user.getEmail());
                response.setUserStatus(user.getStatus() != null ? user.getStatus().name() : null);
            }
        }

        return response;
    }

}

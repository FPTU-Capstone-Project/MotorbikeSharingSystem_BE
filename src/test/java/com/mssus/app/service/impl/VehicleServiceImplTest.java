package com.mssus.app.service.impl;

import com.mssus.app.common.enums.FuelType;
import com.mssus.app.common.enums.VehicleStatus;
import com.mssus.app.common.exception.ConflictException;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.dto.request.CreateVehicleRequest;
import com.mssus.app.dto.request.UpdateVehicleRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VehicleResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Vehicle;
import com.mssus.app.mapper.VehicleMapper;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VehicleServiceImpl Tests")
class VehicleServiceImplTest { // 15 failed tests

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private VehicleMapper vehicleMapper;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    private Vehicle testVehicle;
    private DriverProfile testDriverProfile;
    private User testUser;
    private CreateVehicleRequest testCreateRequest;
    private UpdateVehicleRequest testUpdateRequest;
    private VehicleResponse testVehicleResponse;

    @BeforeEach
    void setUp() {
        setupTestData();
        setupMockBehavior();
    }

    private void setupTestData() {
        testUser = createTestUser();
        testDriverProfile = createTestDriverProfile();
        testVehicle = createTestVehicle();
        testCreateRequest = createTestCreateVehicleRequest();
        testUpdateRequest = createTestUpdateVehicleRequest();
        testVehicleResponse = createTestVehicleResponse();
        
        // Set driver profile after both objects are created
        testUser.setDriverProfile(testDriverProfile);
    }

    private void setupMockBehavior() {
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(testVehicle);
        when(vehicleRepository.findById(anyInt())).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.findByIdWithDriver(anyInt())).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.existsByPlateNumber(anyString())).thenReturn(false);
        when(driverProfileRepository.findById(anyInt())).thenReturn(Optional.of(testDriverProfile));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(vehicleMapper.mapToVehicleResponse(any(Vehicle.class))).thenReturn(testVehicleResponse);
    }

    // ========== CREATE VEHICLE TESTS ==========

    @Test
    @DisplayName("should_createVehicle_when_validRequest")
    void should_createVehicle_when_validRequest() {
        // Arrange
        when(vehicleRepository.existsByPlateNumber(testCreateRequest.getPlateNumber())).thenReturn(false);
        when(driverProfileRepository.findById(testCreateRequest.getDriverId())).thenReturn(Optional.of(testDriverProfile));

        // Act
        VehicleResponse result = vehicleService.createVehicle(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(testVehicle.getVehicleId());
        assertThat(result.getPlateNumber()).isEqualTo(testCreateRequest.getPlateNumber());
        assertThat(result.getModel()).isEqualTo(testCreateRequest.getModel());

        verify(vehicleRepository).existsByPlateNumber(testCreateRequest.getPlateNumber());
        verify(driverProfileRepository).findById(testCreateRequest.getDriverId());
        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
        verifyNoMoreInteractions(vehicleRepository, driverProfileRepository, vehicleMapper);
    }

    @Test
    @DisplayName("should_throwConflictException_when_plateNumberAlreadyExists")
    void should_throwConflictException_when_plateNumberAlreadyExists() {
        // Arrange
        when(vehicleRepository.existsByPlateNumber(testCreateRequest.getPlateNumber())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.createVehicle(testCreateRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Vehicle with plate number " + testCreateRequest.getPlateNumber() + " already exists");

        verify(vehicleRepository).existsByPlateNumber(testCreateRequest.getPlateNumber());
        verify(driverProfileRepository, never()).findById(anyInt());
        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verifyNoMoreInteractions(vehicleRepository, driverProfileRepository, vehicleMapper);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_driverNotFound")
    void should_throwNotFoundException_when_driverNotFound() {
        // Arrange
        when(vehicleRepository.existsByPlateNumber(testCreateRequest.getPlateNumber())).thenReturn(false);
        when(driverProfileRepository.findById(testCreateRequest.getDriverId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.createVehicle(testCreateRequest))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Driver not found with ID: " + testCreateRequest.getDriverId());

        verify(vehicleRepository).existsByPlateNumber(testCreateRequest.getPlateNumber());
        verify(driverProfileRepository).findById(testCreateRequest.getDriverId());
        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verifyNoMoreInteractions(vehicleRepository, driverProfileRepository, vehicleMapper);
    }

    @Test
    @DisplayName("should_createVehicle_when_statusIsNull")
    void should_createVehicle_when_statusIsNull() {
        // Arrange
        testCreateRequest.setStatus(null);
        when(vehicleRepository.existsByPlateNumber(testCreateRequest.getPlateNumber())).thenReturn(false);
        when(driverProfileRepository.findById(testCreateRequest.getDriverId())).thenReturn(Optional.of(testDriverProfile));

        // Act
        VehicleResponse result = vehicleService.createVehicle(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(testVehicle.getVehicleId());

        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    // ========== GET VEHICLE BY ID TESTS ==========

    @Test
    @DisplayName("should_getVehicleById_when_vehicleExists")
    void should_getVehicleById_when_vehicleExists() {
        // Arrange
        Integer vehicleId = 1;

        // Act
        VehicleResponse result = vehicleService.getVehicleById(vehicleId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(vehicleId);
        assertThat(result.getPlateNumber()).isEqualTo(testVehicle.getPlateNumber());
        assertThat(result.getModel()).isEqualTo(testVehicle.getModel());

        verify(vehicleRepository).findByIdWithDriver(vehicleId);
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
        verifyNoMoreInteractions(vehicleRepository, vehicleMapper);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_vehicleNotFound")
    void should_throwNotFoundException_when_vehicleNotFound() {
        // Arrange
        Integer vehicleId = 999;
        when(vehicleRepository.findByIdWithDriver(vehicleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.getVehicleById(vehicleId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Vehicle not found with ID: " + vehicleId);

        verify(vehicleRepository).findByIdWithDriver(vehicleId);
        verify(vehicleMapper, never()).mapToVehicleResponse(any(Vehicle.class));
        verifyNoMoreInteractions(vehicleRepository, vehicleMapper);
    }

    // ========== UPDATE VEHICLE TESTS ==========

    @Test
    @DisplayName("should_updateVehicle_when_validRequest")
    void should_updateVehicle_when_validRequest() {
        // Arrange
        Integer vehicleId = 1;
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(testVehicle));

        // Act
        VehicleResponse result = vehicleService.updateVehicle(vehicleId, testUpdateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(vehicleId);
        assertThat(result.getPlateNumber()).isEqualTo(testUpdateRequest.getPlateNumber());
        assertThat(result.getModel()).isEqualTo(testUpdateRequest.getModel());

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository).save(testVehicle);
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_vehicleNotFoundForUpdate")
    void should_throwNotFoundException_when_vehicleNotFoundForUpdate() {
        // Arrange
        Integer vehicleId = 999;
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, testUpdateRequest))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Vehicle not found with ID: " + vehicleId);

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verify(vehicleMapper, never()).mapToVehicleResponse(any(Vehicle.class));
    }

    @Test
    @DisplayName("should_throwConflictException_when_plateNumberAlreadyExistsInUpdate")
    void should_throwConflictException_when_plateNumberAlreadyExistsInUpdate() {
        // Arrange
        Integer vehicleId = 1;
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.existsByPlateNumber(testUpdateRequest.getPlateNumber())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, testUpdateRequest))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Vehicle with plate number " + testUpdateRequest.getPlateNumber() + " already exists");

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository).existsByPlateNumber(testUpdateRequest.getPlateNumber());
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    @DisplayName("should_updateVehicle_when_plateNumberNotChanged")
    void should_updateVehicle_when_plateNumberNotChanged() {
        // Arrange
        Integer vehicleId = 1;
        testUpdateRequest.setPlateNumber(testVehicle.getPlateNumber()); // Same plate number
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(testVehicle));

        // Act
        VehicleResponse result = vehicleService.updateVehicle(vehicleId, testUpdateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(vehicleId);

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository, never()).existsByPlateNumber(anyString());
        verify(vehicleRepository).save(testVehicle);
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    @Test
    @DisplayName("should_updateVehicle_when_plateNumberIsNull")
    void should_updateVehicle_when_plateNumberIsNull() {
        // Arrange
        Integer vehicleId = 1;
        testUpdateRequest.setPlateNumber(null);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(testVehicle));

        // Act
        VehicleResponse result = vehicleService.updateVehicle(vehicleId, testUpdateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(vehicleId);

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository, never()).existsByPlateNumber(anyString());
        verify(vehicleRepository).save(testVehicle);
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    // ========== DELETE VEHICLE TESTS ==========

    @Test
    @DisplayName("should_deleteVehicle_when_vehicleExists")
    void should_deleteVehicle_when_vehicleExists() {
        // Arrange
        Integer vehicleId = 1;
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(testVehicle));

        // Act
        MessageResponse result = vehicleService.deleteVehicle(vehicleId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("Vehicle deleted successfully");
        assertThat(testVehicle.getStatus()).isEqualTo(VehicleStatus.INACTIVE);

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository).save(testVehicle);
        verifyNoMoreInteractions(vehicleRepository);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_vehicleNotFoundForDelete")
    void should_throwNotFoundException_when_vehicleNotFoundForDelete() {
        // Arrange
        Integer vehicleId = 999;
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Vehicle not found with ID: " + vehicleId);

        verify(vehicleRepository).findById(vehicleId);
        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verifyNoMoreInteractions(vehicleRepository);
    }

    // ========== GET ALL VEHICLES TESTS ==========

    @Test
    @DisplayName("should_getAllVehicles_when_vehiclesExist")
    void should_getAllVehicles_when_vehiclesExist() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Vehicle> vehicles = List.of(testVehicle, createTestVehicle(2, "29B-67890"));
        Page<Vehicle> vehiclePage = new PageImpl<>(vehicles, pageable, 2);
        when(vehicleRepository.findAll(pageable)).thenReturn(vehiclePage);

        // Act
        PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(2);
        assertThat(result.getPagination().getPage()).isEqualTo(1); // +1 from service
        assertThat(result.getPagination().getPageSize()).isEqualTo(10);

        verify(vehicleRepository).findAll(pageable);
        verify(vehicleMapper, times(2)).mapToVehicleResponse(any(Vehicle.class));
        verifyNoMoreInteractions(vehicleRepository, vehicleMapper);
    }

    @Test
    @DisplayName("should_getAllVehicles_when_noVehiclesExist")
    void should_getAllVehicles_when_noVehiclesExist() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Vehicle> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(vehicleRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act
        PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(0);

        verify(vehicleRepository).findAll(pageable);
        verify(vehicleMapper, never()).mapToVehicleResponse(any(Vehicle.class));
        verifyNoMoreInteractions(vehicleRepository, vehicleMapper);
    }

    // ========== GET VEHICLES BY DRIVER ID TESTS ==========

    @Test
    @DisplayName("should_getVehiclesByDriverId_when_vehiclesExist")
    void should_getVehiclesByDriverId_when_vehiclesExist() {
        // Arrange
        String driverEmail = "driver@example.com";
        Pageable pageable = PageRequest.of(0, 10);
        List<Vehicle> vehicles = List.of(testVehicle);
        Page<Vehicle> vehiclePage = new PageImpl<>(vehicles, pageable, 1);
        when(userRepository.findByEmail(driverEmail)).thenReturn(Optional.of(testUser));
        when(vehicleRepository.findByDriverDriverId(testDriverProfile.getDriverId(), pageable)).thenReturn(vehiclePage);

        // Act
        PageResponse<VehicleResponse> result = vehicleService.getVehiclesByDriverId(driverEmail, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(1);

        verify(userRepository).findByEmail(driverEmail);
        verify(vehicleRepository).findByDriverDriverId(testDriverProfile.getDriverId(), pageable);
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_userNotFoundByEmail")
    void should_throwNotFoundException_when_userNotFoundByEmail() {
        // Arrange
        String driverEmail = "notfound@example.com";
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail(driverEmail)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.getVehiclesByDriverId(driverEmail, pageable))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("User not found with email: " + driverEmail);

        verify(userRepository).findByEmail(driverEmail);
        verify(vehicleRepository, never()).findByDriverDriverId(anyInt(), any(Pageable.class));
        verify(vehicleMapper, never()).mapToVehicleResponse(any(Vehicle.class));
    }

    // ========== GET VEHICLES BY STATUS TESTS ==========

    @Test
    @DisplayName("should_getVehiclesByStatus_when_vehiclesExist")
    void should_getVehiclesByStatus_when_vehiclesExist() {
        // Arrange
        String status = "ACTIVE";
        Pageable pageable = PageRequest.of(0, 10);
        List<Vehicle> vehicles = List.of(testVehicle);
        Page<Vehicle> vehiclePage = new PageImpl<>(vehicles, pageable, 1);
        when(vehicleRepository.findByStatus(status, pageable)).thenReturn(vehiclePage);

        // Act
        PageResponse<VehicleResponse> result = vehicleService.getVehiclesByStatus(status, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(1);

        verify(vehicleRepository).findByStatus(status, pageable);
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    @Test
    @DisplayName("should_getVehiclesByStatus_when_noVehiclesExist")
    void should_getVehiclesByStatus_when_noVehiclesExist() {
        // Arrange
        String status = "INACTIVE";
        Pageable pageable = PageRequest.of(0, 10);
        Page<Vehicle> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(vehicleRepository.findByStatus(status, pageable)).thenReturn(emptyPage);

        // Act
        PageResponse<VehicleResponse> result = vehicleService.getVehiclesByStatus(status, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(0);

        verify(vehicleRepository).findByStatus(status, pageable);
        verify(vehicleMapper, never()).mapToVehicleResponse(any(Vehicle.class));
    }

    // ========== PARAMETERIZED TESTS ==========

    @ParameterizedTest
    @MethodSource("fuelTypeProvider")
    @DisplayName("should_createVehicle_when_differentFuelTypes")
    void should_createVehicle_when_differentFuelTypes(String fuelType) {
        // Arrange
        testCreateRequest.setFuelType(fuelType);
        when(vehicleRepository.existsByPlateNumber(testCreateRequest.getPlateNumber())).thenReturn(false);
        when(driverProfileRepository.findById(testCreateRequest.getDriverId())).thenReturn(Optional.of(testDriverProfile));

        // Act
        VehicleResponse result = vehicleService.createVehicle(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(testVehicle.getVehicleId());

        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    @ParameterizedTest
    @MethodSource("vehicleStatusProvider")
    @DisplayName("should_createVehicle_when_differentStatuses")
    void should_createVehicle_when_differentStatuses(String status) {
        // Arrange
        testCreateRequest.setStatus(status);
        when(vehicleRepository.existsByPlateNumber(testCreateRequest.getPlateNumber())).thenReturn(false);
        when(driverProfileRepository.findById(testCreateRequest.getDriverId())).thenReturn(Optional.of(testDriverProfile));

        // Act
        VehicleResponse result = vehicleService.createVehicle(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getVehicleId()).isEqualTo(testVehicle.getVehicleId());

        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleMapper).mapToVehicleResponse(testVehicle);
    }

    // ========== HELPER METHODS ==========

    private User createTestUser() {
        return User.builder()
            .userId(1)
            .email("driver@example.com")
            .phone("0901234567")
            .fullName("Test Driver")
            .build();
    }

    private DriverProfile createTestDriverProfile() {
        return DriverProfile.builder()
            .driverId(1)
            .user(testUser)
            .build();
    }

    private Vehicle createTestVehicle() {
        return createTestVehicle(1, "29A-12345");
    }

    private Vehicle createTestVehicle(Integer vehicleId, String plateNumber) {
        return Vehicle.builder()
            .vehicleId(vehicleId)
            .driver(testDriverProfile)
            .plateNumber(plateNumber)
            .model("Honda Wave Alpha")
            .color("Red")
            .year(2020)
            .capacity(2)
            .insuranceExpiry(LocalDateTime.now().plusYears(1))
            .lastMaintenance(LocalDateTime.now().minusMonths(1))
            .fuelType(FuelType.GASOLINE)
            .status(VehicleStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private CreateVehicleRequest createTestCreateVehicleRequest() {
        return CreateVehicleRequest.builder()
            .driverId(1)
            .plateNumber("29A-12345")
            .model("Honda Wave Alpha")
            .color("Red")
            .year(2020)
            .capacity(2)
            .insuranceExpiry(LocalDateTime.now().plusYears(1))
            .lastMaintenance(LocalDateTime.now().minusMonths(1))
            .fuelType("GASOLINE")
            .status("active")
            .build();
    }

    private UpdateVehicleRequest createTestUpdateVehicleRequest() {
        return UpdateVehicleRequest.builder()
            .plateNumber("29A-54321")
            .model("Honda Wave Beta")
            .color("Blue")
            .year(2021)
            .capacity(2)
            .insuranceExpiry(LocalDateTime.now().plusYears(1))
            .lastMaintenance(LocalDateTime.now().minusMonths(1))
            .fuelType("ELECTRIC")
            .status("maintenance")
            .build();
    }

    private VehicleResponse createTestVehicleResponse() {
        return VehicleResponse.builder()
            .vehicleId(1)
            .driverId(1)
            .driverName("Test Driver")
            .driverPhone("0901234567")
            .driverEmail("driver@example.com")
            .userStatus("ACTIVE")
            .plateNumber("29A-12345")
            .model("Honda Wave Alpha")
            .color("Red")
            .year(2020)
            .capacity(2)
            .insuranceExpiry(LocalDateTime.now().plusYears(1))
            .lastMaintenance(LocalDateTime.now().minusMonths(1))
            .fuelType("GASOLINE")
            .vehicleStatus("active")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private static Stream<String> fuelTypeProvider() {
        return Stream.of("GASOLINE", "ELECTRIC");
    }

    private static Stream<String> vehicleStatusProvider() {
        return Stream.of("active", "inactive", "maintenance", "pending");
    }
}

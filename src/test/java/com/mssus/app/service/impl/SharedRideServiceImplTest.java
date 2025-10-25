package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.ride.*;
import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.*;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.LocationMapper;
import com.mssus.app.mapper.SharedRideMapper;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharedRideServiceImplTest {

    @Mock
    private SharedRideRepository rideRepository;

    @Mock
    private SharedRideRequestRepository requestRepository;

    @Mock
    private DriverProfileRepository driverRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SharedRideMapper rideMapper;

    @Mock
    private RoutingService routingService;

    @Mock
    private PricingConfigRepository pricingConfigRepository;

    @Mock
    private RideTrackRepository trackRepository;

    @Mock
    private RideTrackingService rideTrackingService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RideFundCoordinatingService rideFundCoordinatingService;

    @Mock
    private Authentication authentication;

    @Mock
    private LocationMapper locationMapper;

    @InjectMocks
    private SharedRideServiceImpl sharedRideService;

    private User user;
    private DriverProfile driver;
    private Vehicle vehicle;
    private Location startLocation;
    private Location endLocation;
    private LocationResponse startLocationResponse;
    private LocationResponse endLocationResponse;
    private PricingConfig pricingConfig;
    private SharedRide ride;
    private SharedRideRequest rideRequest;
    private RiderProfile rider;
    private CreateRideRequest createRideRequest;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(1);
        user.setEmail("driver@test.com");
        user.setFullName("Test Driver");

        driver = new DriverProfile();
        driver.setDriverId(1);
        driver.setUser(user);

        vehicle = new Vehicle();
        vehicle.setVehicleId(1);
        vehicle.setDriver(driver);
        vehicle.setPlateNumber("ABC123");

        startLocation = new Location();
        startLocation.setLocationId(1);
        startLocation.setName("Start Location");
        startLocation.setLat(10.762622);
        startLocation.setLng(106.660172);

        endLocation = new Location();
        endLocation.setLocationId(2);
        endLocation.setName("End Location");
        endLocation.setLat(10.772622);
        endLocation.setLng(106.670172);

        startLocationResponse = new LocationResponse(
            1,
            "Start Location",
            10.762622,
            106.660172,
            null
        );

        endLocationResponse = new LocationResponse(
            2,
            "End Location",
            10.772622,
            106.670172,
            null
        );

        pricingConfig = new PricingConfig();
        pricingConfig.setPricingConfigId(1);
        pricingConfig.setVersion(Instant.now());
        pricingConfig.setSystemCommissionRate(BigDecimal.valueOf(0.20));
        pricingConfig.setValidFrom(Instant.now().minusSeconds(3600));

        ride = new SharedRide();
        ride.setSharedRideId(1);
        ride.setDriver(driver);
        ride.setVehicle(vehicle);
        ride.setStartLocation(startLocation);
        ride.setEndLocation(endLocation);
//        ride.setStartLat(10.762622);
//        ride.setStartLng(106.660172);
//        ride.setEndLat(10.772622);
//        ride.setEndLng(106.670172);
//        ride.setStartLocationId(1);
//        ride.setEndLocationId(2);
        ride.setStatus(SharedRideStatus.SCHEDULED);
        ride.setMaxPassengers(1);
        ride.setCurrentPassengers(0);
        ride.setScheduledTime(LocalDateTime.now().plusHours(1));
        ride.setEstimatedDuration(30);
        ride.setEstimatedDistance(10.5f);
        ride.setPricingConfig(pricingConfig);

        User riderUser = new User();
        riderUser.setUserId(2);
        riderUser.setEmail("rider@test.com");
        riderUser.setFullName("Test Rider");

        rider = new RiderProfile();
        rider.setRiderId(1);
        rider.setUser(riderUser);

        rideRequest = new SharedRideRequest();
        rideRequest.setSharedRideRequestId(1);
        rideRequest.setSharedRide(ride);
        rideRequest.setRider(rider);
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
        rideRequest.setPickupLocation(startLocation);
        rideRequest.setDropoffLocation(endLocation);
//        rideRequest.setPickupLat(10.762622);
//        rideRequest.setPickupLng(106.660172);
//        rideRequest.setDropoffLat(10.772622);
//        rideRequest.setDropoffLng(106.670172);
//        rideRequest.setPickupLocationId(1);
//        rideRequest.setDropoffLocationId(2);
        rideRequest.setTotalFare(BigDecimal.valueOf(50000));
        rideRequest.setSubtotalFare(BigDecimal.valueOf(50000));
        rideRequest.setDiscountAmount(BigDecimal.ZERO);
        rideRequest.setDistanceMeters(10500);

        createRideRequest = new CreateRideRequest(
            1,
            2,
            null,
            null,
            LocalDateTime.now().plusHours(2)
        );
    }

    @Test
    void createRide_WithValidLocationIds_ReturnsSharedRideResponse() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriver_DriverId(driver.getDriverId())).thenReturn(Optional.of(vehicle));
        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(pricingConfigRepository.findActive(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(routingService.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new RouteResponse(10500L, 1800L, "encoded_polyline"));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        SharedRideResponse response = sharedRideService.createRide(createRideRequest, authentication);

        assertNotNull(response);
        verify(rideRepository).save(any(SharedRide.class));
        verify(routingService).getRoute(10.762622, 106.660172, 10.772622, 106.670172);
    }

    @Test
    void createRide_WithAdHocCoordinates_ReturnsSharedRideResponse() {
        CreateRideRequest adHocRequest = new CreateRideRequest(
            null,
            null,
            new LatLng(10.762622, 106.660172),
            new LatLng(10.772622, 106.670172),
            LocalDateTime.now().plusHours(2)
        );

        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriver_DriverId(driver.getDriverId())).thenReturn(Optional.of(vehicle));
        when(pricingConfigRepository.findActive(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(routingService.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new RouteResponse(10500L, 1800L, "encoded_polyline"));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        SharedRideResponse response = sharedRideService.createRide(adHocRequest, authentication);

        assertNotNull(response);
        verify(rideRepository).save(any(SharedRide.class));
    }

    @Test
    void createRide_UserNotFound_ThrowsBaseDomainException() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.empty());

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void createRide_DriverProfileNotFound_ThrowsBaseDomainException() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.empty());

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void createRide_VehicleNotFound_ThrowsBaseDomainException() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void createRide_VehicleNotOwnedByDriver_ThrowsBaseDomainException() {
        DriverProfile anotherDriver = new DriverProfile();
        anotherDriver.setDriverId(999);
        vehicle.setDriver(anotherDriver);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void createRide_StartLocationNotFound_ThrowsBaseDomainException() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void createRide_PricingConfigNotFound_ThrowsBaseDomainException() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void createRide_RouteValidationFails_ThrowsBaseDomainException() {
        when(authentication.getName()).thenReturn("driver@test.com");
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.createRide(createRideRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void getRideById_WithValidId_ReturnsSharedRideResponse() {
        when(rideRepository.findById(1)).thenReturn(Optional.of(ride));
//        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
//        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(ride)).thenReturn(new SharedRideResponse());

        SharedRideResponse response = sharedRideService.getRideById(1);

        assertNotNull(response);
        verify(rideRepository).findById(1);
    }

    @Test
    void getRideById_WithInvalidId_ThrowsBaseDomainException() {
        when(rideRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(BaseDomainException.class, () -> sharedRideService.getRideById(999));
    }

    @Test
    void getRidesByDriver_WithoutStatusFilter_ReturnsPageOfRides() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SharedRide> ridePage = new PageImpl<>(List.of(ride));

        when(rideRepository.findByDriverDriverIdOrderByScheduledTimeDesc(1, pageable)).thenReturn(ridePage);
//        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
//        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        Page<SharedRideResponse> result = sharedRideService.getRidesByDriver(1, null, pageable, authentication);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(rideRepository).findByDriverDriverIdOrderByScheduledTimeDesc(1, pageable);
    }

    @Test
    void getRidesByDriver_WithStatusFilter_ReturnsFilteredPageOfRides() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SharedRide> ridePage = new PageImpl<>(List.of(ride));

        when(rideRepository.findByDriverDriverIdAndStatusOrderByScheduledTimeDesc(
            1, SharedRideStatus.SCHEDULED, pageable)).thenReturn(ridePage);
//        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
//        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        Page<SharedRideResponse> result = sharedRideService.getRidesByDriver(
            1, "SCHEDULED", pageable, authentication);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(rideRepository).findByDriverDriverIdAndStatusOrderByScheduledTimeDesc(
            1, SharedRideStatus.SCHEDULED, pageable);
    }

    @Test
    void browseAvailableRides_WithTimeRange_ReturnsAvailableRides() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(2);
        Page<SharedRide> ridePage = new PageImpl<>(List.of(ride));

        when(rideRepository.findAvailableRides(any(LocalDateTime.class),
            any(LocalDateTime.class), eq(pageable))).thenReturn(ridePage);
//        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
//        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        Page<SharedRideResponse> result = sharedRideService.browseAvailableRides(
            start.toString(), end.toString(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(rideRepository).findAvailableRides(any(LocalDateTime.class),
            any(LocalDateTime.class), eq(pageable));
    }

    @Test
    void browseAvailableRides_WithoutTimeRange_UsesDefaultTimeWindow() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<SharedRide> ridePage = new PageImpl<>(List.of(ride));

        when(rideRepository.findAvailableRides(any(LocalDateTime.class),
            any(LocalDateTime.class), eq(pageable))).thenReturn(ridePage);
        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        Page<SharedRideResponse> result = sharedRideService.browseAvailableRides(null, null, pageable);

        assertNotNull(result);
        verify(rideRepository).findAvailableRides(any(LocalDateTime.class),
            any(LocalDateTime.class), eq(pageable));
    }

    @Test
    void startRide_WithValidRequest_StartsRideSuccessfully() {
        StartRideRequest request = new StartRideRequest(1);
        ride.setStatus(SharedRideStatus.SCHEDULED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
//        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.CONFIRMED))
//                .thenReturn(List.of(rideRequest));
//        when(rideTrackingService.getLatestPosition(1, 3))
//                .thenReturn(Optional.of(new LatLng(10.762622, 106.660172)));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);
//        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
//        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(any(SharedRide.class))).thenReturn(new SharedRideResponse());

        SharedRideResponse response = sharedRideService.startRide(request, authentication);

        assertNotNull(response);
        verify(rideRepository).save(any(SharedRide.class));
        verify(rideTrackingService).startTracking(1);
        verify(notificationService).sendNotification(
            any(User.class), eq(NotificationType.RIDE_STARTED), anyString(),
            anyString(), any(), any(Priority.class), any(DeliveryMethod.class), anyString());
    }

    @Test
    void startRide_RideNotFound_ThrowsBaseDomainException() {
        StartRideRequest request = new StartRideRequest(999);

        when(rideRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRide(request, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void startRide_DriverNotOwner_ThrowsBaseDomainException() {
        StartRideRequest request = new StartRideRequest(1);
        DriverProfile anotherDriver = new DriverProfile();
        anotherDriver.setDriverId(999);
        ride.setDriver(anotherDriver);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRide(request, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void startRide_InvalidStatus_ThrowsBaseDomainException() {
        StartRideRequest request = new StartRideRequest(1);
        ride.setStatus(SharedRideStatus.ONGOING);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRide(request, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

//    @Test
//    void startRide_NoConfirmedPassengers_ThrowsBaseDomainException() {
//        StartRideRequest request = new StartRideRequest(1);
//        ride.setStatus(SharedRideStatus.SCHEDULED);
//
//        when(authentication.getName()).thenReturn("driver@test.com");
//        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
//        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
//        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
//        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.CONFIRMED))
//                .thenReturn(Collections.emptyList());
//
//        assertThrows(BaseDomainException.class,
//                () -> sharedRideService.startRide(request, authentication));
//        verify(rideRepository, never()).save(any(SharedRide.class));
//    }

//    @Test
//    void startRide_DriverTooFarFromPickup_ThrowsBaseDomainException() {
//        StartRideRequest request = new StartRideRequest(1);
//        ride.setStatus(SharedRideStatus.SCHEDULED);
//
//        when(authentication.getName()).thenReturn("driver@test.com");
//        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
//        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
//        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
//        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.CONFIRMED))
//                .thenReturn(List.of(rideRequest));
//        when(rideTrackingService.getLatestPosition(1, 3))
//                .thenReturn(Optional.of(new LatLng(11.0, 107.0)));
//
//        assertThrows(BaseDomainException.class,
//                () -> sharedRideService.startRide(request, authentication));
//        verify(rideRepository, never()).save(any(SharedRide.class));
//    }

    @Test
    void startRideRequestOfRide_WithValidRequest_StartsRequestSuccessfully() {
        StartRideReqRequest request = new StartRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.ONGOING);
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(10.762622, 106.660172)));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        when(locationMapper.toResponse(startLocation)).thenReturn(startLocationResponse);
        when(locationMapper.toResponse(endLocation)).thenReturn(endLocationResponse);

        SharedRideRequestResponse response = sharedRideService.startRideRequestOfRide(request, authentication);

        assertNotNull(response);
        verify(requestRepository).save(any(SharedRideRequest.class));
        verify(notificationService, times(2)).sendNotification(
            any(User.class), any(NotificationType.class), anyString(),
            anyString(), any(), any(Priority.class), any(DeliveryMethod.class), anyString());
    }

    @Test
    void startRideRequestOfRide_RideRequestNotBelongToRide_ThrowsBaseDomainException() {
        StartRideReqRequest request = new StartRideReqRequest(1, 1);
        SharedRide anotherRide = new SharedRide();
        anotherRide.setSharedRideId(999);
        rideRequest.setSharedRide(anotherRide);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRideRequestOfRide(request, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void startRideRequestOfRide_RideNotOngoing_ThrowsBaseDomainException() {
        StartRideReqRequest request = new StartRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.SCHEDULED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRideRequestOfRide(request, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void startRideRequestOfRide_RequestNotConfirmed_ThrowsBaseDomainException() {
        StartRideReqRequest request = new StartRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.ONGOING);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRideRequestOfRide(request, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void startRideRequestOfRide_DriverTooFarFromPickup_ThrowsBaseDomainException() {
        StartRideReqRequest request = new StartRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.ONGOING);
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(11.0, 107.0)));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.startRideRequestOfRide(request, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void completeRideRequestOfRide_WithValidRequest_CompletesRequestSuccessfully() {
        CompleteRideReqRequest request = new CompleteRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now().minusMinutes(30));
        rideRequest.setStatus(SharedRideRequestStatus.ONGOING);

        RideRequestSettledResponse settledResponse = new RideRequestSettledResponse(
            BigDecimal.valueOf(40000), BigDecimal.valueOf(10000));

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(10.772622, 106.670172)));
        when(pricingConfigRepository.findActive(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(rideFundCoordinatingService.settleRideFunds(any(RideCompleteSettlementRequest.class),
            any(FareBreakdown.class))).thenReturn(settledResponse);
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);

        RideRequestCompletionResponse response = sharedRideService.completeRideRequestOfRide(
            request, authentication);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(40000), response.getDriverEarningsOfRequest());
        verify(requestRepository).save(any(SharedRideRequest.class));
        verify(rideFundCoordinatingService).settleRideFunds(
            any(RideCompleteSettlementRequest.class), any(FareBreakdown.class));
    }

    @Test
    void completeRideRequestOfRide_RideNotOngoing_ThrowsBaseDomainException() {
        CompleteRideReqRequest request = new CompleteRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.SCHEDULED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.completeRideRequestOfRide(request, authentication));
    }

    @Test
    void completeRideRequestOfRide_RequestNotOngoing_ThrowsBaseDomainException() {
        CompleteRideReqRequest request = new CompleteRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.ONGOING);
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.completeRideRequestOfRide(request, authentication));
    }

    @Test
    void completeRideRequestOfRide_DriverTooFarFromDropoff_ThrowsBaseDomainException() {
        CompleteRideReqRequest request = new CompleteRideReqRequest(1, 1);
        ride.setStatus(SharedRideStatus.ONGOING);
        rideRequest.setStatus(SharedRideRequestStatus.ONGOING);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(11.0, 107.0)));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.completeRideRequestOfRide(request, authentication));
    }

    @Test
    void completeRide_WithValidRequest_CompletesRideSuccessfully() {
        CompleteRideRequest request = new CompleteRideRequest(1);
        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now().minusMinutes(30));
        rideRequest.setStatus(SharedRideRequestStatus.COMPLETED);

        RideTrack track = new RideTrack();
        track.setSharedRide(ride);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(10.772622, 106.670172)));
        when(requestRepository.findActiveRequestsByRide(1, SharedRideRequestStatus.CONFIRMED,
            SharedRideRequestStatus.ONGOING)).thenReturn(Collections.emptyList());
        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.COMPLETED))
            .thenReturn(List.of(rideRequest));
//        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.ONGOING))
//                .thenReturn(Collections.emptyList());
        when(pricingConfigRepository.findActive(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(trackRepository.findBySharedRideSharedRideId(1)).thenReturn(Optional.of(track));


        RideCompletionResponse response = sharedRideService.completeRide(request, authentication);

        assertNotNull(response);
        verify(rideRepository).save(any(SharedRide.class));
        verify(driverRepository).updateRideStats(eq(1), any(BigDecimal.class));
        verify(rideTrackingService).stopTracking(1);
    }

    @Test
    void completeRide_RideNotOngoing_ThrowsBaseDomainException() {
        CompleteRideRequest request = new CompleteRideRequest(1);
        ride.setStatus(SharedRideStatus.SCHEDULED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.completeRide(request, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void completeRide_HasActiveRequests_ThrowsBaseDomainException() {
        CompleteRideRequest request = new CompleteRideRequest(1);
        ride.setStatus(SharedRideStatus.ONGOING);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findActiveRequestsByRide(1, SharedRideRequestStatus.CONFIRMED,
            SharedRideRequestStatus.ONGOING)).thenReturn(List.of(rideRequest));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.completeRide(request, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void completeRide_HasOngoingRequests_ThrowsBaseDomainException() {
        CompleteRideRequest request = new CompleteRideRequest(1);
        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now().minusMinutes(30));
        rideRequest.setStatus(SharedRideRequestStatus.ONGOING);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(10.772622, 106.670172)));
        when(requestRepository.findActiveRequestsByRide(1, SharedRideRequestStatus.CONFIRMED,
            SharedRideRequestStatus.ONGOING)).thenReturn(Collections.emptyList());
        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.COMPLETED))
            .thenReturn(Collections.emptyList());
//        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.ONGOING))
//                .thenReturn(List.of(rideRequest));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.completeRide(request, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void completeRide_WithFallbackRouteDistance_CompletesSuccessfully() {
        CompleteRideRequest request = new CompleteRideRequest(1);
        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now().minusMinutes(30));
        rideRequest.setStatus(SharedRideRequestStatus.COMPLETED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(rideTrackingService.getLatestPosition(1, 3))
            .thenReturn(Optional.of(new LatLng(10.772622, 106.670172)));
        when(requestRepository.findActiveRequestsByRide(1, SharedRideRequestStatus.CONFIRMED,
            SharedRideRequestStatus.ONGOING)).thenReturn(Collections.emptyList());
        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.COMPLETED))
            .thenReturn(List.of(rideRequest));
//        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.ONGOING))
//                .thenReturn(Collections.emptyList());
        when(pricingConfigRepository.findActive(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(trackRepository.findBySharedRideSharedRideId(1)).thenReturn(Optional.empty());
        when(routingService.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(new RouteResponse(10500L, 1800L, "polyline"));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);

        RideCompletionResponse response = sharedRideService.completeRide(request, authentication);

        assertNotNull(response);
        verify(routingService).getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void cancelRide_WithValidRequest_CancelsRideSuccessfully() {
        ride.setStatus(SharedRideStatus.SCHEDULED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.CONFIRMED))
            .thenReturn(List.of(rideRequest));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);
        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(ride)).thenReturn(new SharedRideResponse());

        SharedRideResponse response = sharedRideService.cancelRide(1, "Driver cancellation", authentication);

        assertNotNull(response);
        verify(rideRepository).save(any(SharedRide.class));
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
        verify(requestRepository).save(any(SharedRideRequest.class));
    }

    @Test
    void cancelRide_RideNotScheduled_ThrowsBaseDomainException() {
        ride.setStatus(SharedRideStatus.ONGOING);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.cancelRide(1, "reason", authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void cancelRide_NotOwner_ThrowsBaseDomainException() {
        DriverProfile anotherDriver = new DriverProfile();
        anotherDriver.setDriverId(999);
        ride.setDriver(anotherDriver);
        ride.setStatus(SharedRideStatus.SCHEDULED);

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(authentication.getAuthorities()).thenReturn(Collections.emptyList());

        assertThrows(BaseDomainException.class,
            () -> sharedRideService.cancelRide(1, "reason", authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    @Test
    void cancelRide_WithMultipleConfirmedRequests_ReleasesAllHolds() {
        ride.setStatus(SharedRideStatus.SCHEDULED);
        SharedRideRequest request2 = new SharedRideRequest();
        request2.setSharedRideRequestId(2);
        request2.setSharedRide(ride);
        request2.setRider(rider);
        request2.setStatus(SharedRideRequestStatus.CONFIRMED);
        request2.setTotalFare(BigDecimal.valueOf(40000));

        when(authentication.getName()).thenReturn("driver@test.com");
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.CONFIRMED))
            .thenReturn(List.of(rideRequest, request2));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);
//        when(locationRepository.findById(1)).thenReturn(Optional.of(startLocation));
//        when(locationRepository.findById(2)).thenReturn(Optional.of(endLocation));
        when(rideMapper.toResponse(ride)).thenReturn(new SharedRideResponse());

        SharedRideResponse response = sharedRideService.cancelRide(1, "reason", authentication);

        assertNotNull(response);
        verify(rideFundCoordinatingService, times(2)).releaseRideFunds(any(RideHoldReleaseRequest.class));
        verify(requestRepository, times(2)).save(any(SharedRideRequest.class));
    }
}


package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.infrastructure.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.dto.response.ride.BroadcastingRideRequestResponse;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.dto.domain.ride.AcceptRequestDto;
import com.mssus.app.dto.domain.ride.BroadcastAcceptRequest;
import com.mssus.app.dto.domain.ride.CreateRideRequestDto;
import com.mssus.app.dto.request.ride.JoinRideRequest;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.SharedRideRequestMapper;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.service.domain.matching.RideMatchingCoordinator;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.service.domain.pricing.model.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedRideRequestServiceImplTest {

    @Mock
    private SharedRideRequestRepository requestRepository;

    @Mock
    private SharedRideRepository rideRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RiderProfileRepository riderRepository;

    @Mock
    private DriverProfileRepository driverRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SharedRideRequestMapper requestMapper;

    @Mock
    private AuthServiceImpl authServiceImpl;

    @Mock
    private QuoteService quoteService;

    @Mock
    private RideMatchingService matchingService;

    @Mock
    private RideConfigurationProperties rideConfig;

    @Mock
    private RideMatchingCoordinator matchingCoordinator;

    @Mock
    private ApplicationEventPublisherService eventPublisherService;

    @Mock
    private PricingConfigRepository pricingConfigRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RideFundCoordinatingService rideFundCoordinatingService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SharedRideRequestServiceImpl sharedRideRequestService;

    // Test data
    private User user;
    private RiderProfile rider;
    private DriverProfile driver;
    private Vehicle vehicle;
    private Location pickupLocation;
    private Location dropoffLocation;
    private PricingConfig pricingConfig;
    private SharedRide ride;
    private SharedRideRequest rideRequest;
    private Quote quote;
    private FareBreakdown fareBreakdown;
    private CreateRideRequestDto createRideRequestDto;
    private JoinRideRequest joinRideRequest;
    private BroadcastAcceptRequest broadcastAcceptRequest;
    private AcceptRequestDto acceptRequestDto;
    private LatLng currentDriverLocation;

    @BeforeEach
    void setUp() {
        // Setup common test data
        user = new User();
        user.setUserId(1);
        user.setEmail("test@example.com");
        user.setFullName("Test User");

        rider = new RiderProfile();
        rider.setRiderId(1);
        rider.setUser(user);

        driver = new DriverProfile();
        driver.setDriverId(1);
        driver.setUser(user);
        driver.setStatus(DriverProfileStatus.ACTIVE);

        vehicle = new Vehicle();
        vehicle.setVehicleId(1);
        vehicle.setDriver(driver);
        vehicle.setCapacity(2);
        vehicle.setPlateNumber("ABC123");

        pickupLocation = new Location();
        pickupLocation.setLocationId(1);
        pickupLocation.setName("Pickup Location");
        pickupLocation.setLat(10.762622);
        pickupLocation.setLng(106.660172);

        dropoffLocation = new Location();
        dropoffLocation.setLocationId(2);
        dropoffLocation.setName("Dropoff Location");
        dropoffLocation.setLat(10.772622);
        dropoffLocation.setLng(106.670172);

        pricingConfig = new PricingConfig();
        pricingConfig.setPricingConfigId(1);
        pricingConfig.setVersion(Instant.now());
        pricingConfig.setSystemCommissionRate(BigDecimal.valueOf(0.20));

        ride = new SharedRide();
        ride.setSharedRideId(1);
        ride.setDriver(driver);
        ride.setVehicle(vehicle);
        ride.setStatus(SharedRideStatus.SCHEDULED);

        rideRequest = new SharedRideRequest();
        rideRequest.setSharedRideRequestId(1);
        rideRequest.setSharedRide(ride);
        rideRequest.setRider(rider);
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        rideRequest.setPickupLocation(pickupLocation);
        rideRequest.setDropoffLocation(dropoffLocation);
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
        rideRequest.setPickupTime(LocalDateTime.now().plusHours(1));

        // Create fare breakdown
        fareBreakdown = new FareBreakdown(
            Instant.now(),
            10500L,
            MoneyVnd.VND(BigDecimal.ZERO),
            MoneyVnd.VND(BigDecimal.valueOf(50000)),
            MoneyVnd.VND(BigDecimal.valueOf(50000)),
            BigDecimal.valueOf(0.20)
        );

        quote = new Quote(
            UUID.randomUUID(),
            1,
            pickupLocation,
            dropoffLocation,
//            1,
//            2,
//            10.762622,
//            106.660172,
//            10.772622,
//            106.670172,
            10500L,
            1800L,
            "encoded_polyline",
            fareBreakdown,
            Instant.now(),
            Instant.now().plusSeconds(300)
        );

        createRideRequestDto = new CreateRideRequestDto(
            quote.quoteId(),
            LocalDateTime.now().plusHours(2),
            "Test notes"
        );

        joinRideRequest = new JoinRideRequest(
            quote.quoteId(),
            LocalDateTime.now().plusHours(1),
            "Join ride notes"
        );

        broadcastAcceptRequest = new BroadcastAcceptRequest(1, new LatLng(null, null));
        currentDriverLocation = new LatLng(10.763622, 106.661172);

        acceptRequestDto = new AcceptRequestDto(1, currentDriverLocation);




        // Setup timezone for testing (use reflection to set private field)
        try {
            java.lang.reflect.Field timezoneField = SharedRideRequestServiceImpl.class.getDeclaredField("appTimezone");
            timezoneField.setAccessible(true);
            timezoneField.set(sharedRideRequestService, "Asia/Ho_Chi_Minh");
        } catch (Exception e) {
            // Ignore if field doesn't exist or can't be set
        }

        // Setup common mocks
        when(authentication.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        // Setup user context for active profile
        Map<String, Object> userClaims = Map.of("active_profile", "rider");
        AuthServiceImpl.userContext.put(user.getUserId().toString(), userClaims);

        // Setup requestMapper to return proper response object
        when(requestMapper.toResponse(any(SharedRideRequest.class))).thenReturn(new SharedRideRequestResponse());

        // Setup locationRepository for buildRequestResponse
        when(locationRepository.findById(1)).thenReturn(Optional.of(pickupLocation));
        when(locationRepository.findById(2)).thenReturn(Optional.of(dropoffLocation));
    }

    // Helper method to create SharedRideRequestResponse
    private SharedRideRequestResponse createRequestResponse(SharedRideRequest request) {
        RiderProfile rider = request.getRider();
        String riderName = rider != null && rider.getUser() != null ? rider.getUser().getFullName() : "Unknown Rider";

        LocationResponse pickupLocation = new LocationResponse(
            request.getPickupLocation().getLocationId(),
            request.getPickupLocation().getName(),
            request.getPickupLocation().getLat(),
            request.getPickupLocation().getLng(),
            request.getPickupLocation().getAddress()
        );
        LocationResponse dropoffLocation = new LocationResponse(
            request.getDropoffLocation().getLocationId(),
            request.getDropoffLocation().getName(),
            request.getDropoffLocation().getLat(),
            request.getDropoffLocation().getLng(),
            request.getDropoffLocation().getAddress()
        );

        return SharedRideRequestResponse.builder()
            .sharedRideRequestId(request.getSharedRideRequestId())
            .requestKind(request.getRequestKind().name())
            .sharedRideId(request.getSharedRide() != null ? request.getSharedRide().getSharedRideId() : null)
            .riderId(rider != null ? rider.getRiderId() : null)
            .riderName(riderName)
            .status(request.getStatus().name())
            .pickupLocation(pickupLocation)
            .dropoffLocation(dropoffLocation)
//            .pickupLocationName(pickupLocation.getName())
//            .dropoffLocationName(dropoffLocation.getName())
//            .pickupLat(request.getPickupLat())
//            .pickupLng(request.getPickupLng())
//            .dropoffLat(request.getDropoffLat())
//            .dropoffLng(request.getDropoffLng())
            .fareAmount(request.getTotalFare())
            .originalFare(request.getSubtotalFare())
            .discountAmount(request.getDiscountAmount())
            .pickupTime(request.getPickupTime())
            .specialRequests(request.getSpecialRequests())
            .build();
    }

    // Tests for createAIBookingRequest method
    @Test
    void createAIBookingRequest_Success_WithValidData_ShouldCreateRequest() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        // holdRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication);

        // Assert
        assertNotNull(response);
        verify(riderRepository).findByUserUserId(1);
        verify(quoteService).getQuote(any(UUID.class));
        verify(requestRepository).save(any(SharedRideRequest.class));
        verify(rideFundCoordinatingService).holdRideFunds(any(RideConfirmHoldRequest.class));
        verify(eventPublisherService).publishRideRequestCreatedEvent(1);
        verify(notificationService).sendNotification(eq(user), eq(NotificationType.BOOKING_REQUEST_CREATED),
            eq("Booking Request Created"), eq("Your booking request has been created successfully."),
            eq(null), eq(Priority.MEDIUM), eq(DeliveryMethod.IN_APP), eq(null));
    }

    @Test
    void createAIBookingRequest_UserNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication));
        verify(riderRepository, never()).findByUserUserId(anyInt());
    }

    @Test
    void createAIBookingRequest_RiderProfileNotFound_ShouldThrowException() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication));
        verify(quoteService, never()).getQuote(any(UUID.class));
    }

    @Test
    void createAIBookingRequest_InvalidProfile_ShouldThrowException() {
        // Arrange
        Map<String, Object> claims = Map.of("active_profile", "driver");
        AuthServiceImpl.userContext.put("1", claims); // Override the global setup
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication));
    }

    @Test
    void createAIBookingRequest_QuoteBelongsToDifferentRider_ShouldThrowException() {
        // Arrange
        Quote differentRiderQuote = new Quote(
            UUID.randomUUID(),
            999, // Different rider ID
            pickupLocation,
            dropoffLocation,
//            1,
//            2,
//            10.762622,
//            106.660172,
//            10.772622,
//            106.670172,
            10500L,
            1800L,
            "encoded_polyline",
            fareBreakdown,
            Instant.now(),
            Instant.now().plusSeconds(300)
        );
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(differentRiderQuote);

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication));
    }

    @Test
    void createAIBookingRequest_PricingConfigNotFound_ShouldThrowException() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication));
    }

    @Test
    void createAIBookingRequest_WalletHoldFails_ShouldDeleteRequestAndThrowException() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        doThrow(new RuntimeException("Wallet hold failed")).when(rideFundCoordinatingService)
            .holdRideFunds(any(RideConfirmHoldRequest.class));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.createAIBookingRequest(createRideRequestDto, authentication));
        verify(requestRepository).delete(rideRequest);
    }

    @Test
    void createAIBookingRequest_NullPickupTime_ShouldUseCurrentTime() {
        // Arrange
        CreateRideRequestDto requestWithNullTime = new CreateRideRequestDto(
            quote.quoteId(),
            null, // null pickup time
            "Test notes"
        );
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        // holdRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.createAIBookingRequest(requestWithNullTime, authentication);

        // Assert
        assertNotNull(response);
        ArgumentCaptor<SharedRideRequest> requestCaptor = ArgumentCaptor.forClass(SharedRideRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        SharedRideRequest savedRequest = requestCaptor.getValue();
        assertNotNull(savedRequest.getPickupTime());
    }

    // Tests for getBroadcastingRideRequests method
    @Test
    void getBroadcastingRideRequests_Success_WithValidDriver_ShouldReturnRequests() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        List<SharedRideRequest> broadcastingRequests = List.of(rideRequest);
        when(requestRepository.findByStatus(SharedRideRequestStatus.BROADCASTING)).thenReturn(broadcastingRequests);

        // Act
        List<BroadcastingRideRequestResponse> response = sharedRideRequestService.getBroadcastingRideRequests(authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        verify(driverRepository).findByUserUserId(1);
        verify(requestRepository).findByStatus(SharedRideRequestStatus.BROADCASTING);
    }

    @Test
    void getBroadcastingRideRequests_UserNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getBroadcastingRideRequests(authentication));
        verify(driverRepository, never()).findByUserUserId(anyInt());
    }

    @Test
    void getBroadcastingRideRequests_DriverProfileNotFound_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getBroadcastingRideRequests(authentication));
    }

    @Test
    void getBroadcastingRideRequests_EmptyList_ShouldReturnEmptyList() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findByStatus(SharedRideRequestStatus.BROADCASTING)).thenReturn(Collections.emptyList());

        // Act
        List<BroadcastingRideRequestResponse> response = sharedRideRequestService.getBroadcastingRideRequests(authentication);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    // Tests for requestToJoinRide method
    @Test
    void requestToJoinRide_Success_WithValidData_ShouldCreateRequest() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        // holdRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.requestToJoinRide(1, joinRideRequest, authentication);

        // Assert
        assertNotNull(response);
        verify(riderRepository).findByUserUserId(1);
        verify(quoteService).getQuote(any(UUID.class));
        verify(rideRepository).findByIdForUpdate(1);
        verify(requestRepository).save(any(SharedRideRequest.class));
        verify(rideFundCoordinatingService).holdRideFunds(any(RideConfirmHoldRequest.class));
        verify(matchingCoordinator).initiateRideJoining(1);
        verify(notificationService).sendNotification(eq(user), eq(NotificationType.JOIN_RIDE_REQUEST_CREATED),
            eq("Join Ride Request Created"), eq("Your request to join the ride has been created successfully."),
            eq(null), eq(Priority.MEDIUM), eq(DeliveryMethod.IN_APP), eq(null));
    }

    @Test
    void requestToJoinRide_RideNotFound_ShouldThrowException() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.requestToJoinRide(1, joinRideRequest, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void requestToJoinRide_InvalidRideStatus_ShouldThrowException() {
        // Arrange
        ride.setStatus(SharedRideStatus.ONGOING);
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.requestToJoinRide(1, joinRideRequest, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void requestToJoinRide_RideFull_ShouldThrowException() {
        // Arrange
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.requestToJoinRide(1, joinRideRequest, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void requestToJoinRide_QuoteBelongsToDifferentRider_ShouldThrowException() {
        // Arrange
        Quote differentRiderQuote = new Quote(
            UUID.randomUUID(),
            999,
            pickupLocation,
            dropoffLocation,
//            1,
//            2,
//            10.762622,
//            106.660172,
//            10.772622,
//            106.670172,
            10500L,
            1800L,
            "encoded_polyline",
            fareBreakdown,
            Instant.now(),
            Instant.now().plusSeconds(300)
        );
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(differentRiderQuote);

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.requestToJoinRide(1, joinRideRequest, authentication));
    }

    // Tests for getRequestById method
    @Test
    void getRequestById_Success_WithValidId_ShouldReturnRequest() {
        // Arrange
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.getRequestById(1);

        // Assert
        assertNotNull(response);
        verify(requestRepository).findById(1);
    }

    @Test
    void getRequestById_RequestNotFound_ShouldThrowException() {
        // Arrange
        when(requestRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getRequestById(999));
    }

    // Tests for acceptBroadcast method
    @Test
    void acceptBroadcast_Success_WithValidData_ShouldAcceptRequest() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.BROADCASTING);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));
        when(rideRepository.existsByDriverDriverIdAndStatus(1, SharedRideStatus.ONGOING)).thenReturn(false);
        when(matchingCoordinator.beginBroadcastAcceptance(anyInt(), anyInt())).thenReturn(true);
        when(locationRepository.save(any(Location.class))).thenReturn(pickupLocation, dropoffLocation);
        when(locationRepository.findById(1)).thenReturn(Optional.of(pickupLocation));
        when(rideRepository.save(any(SharedRide.class))).thenReturn(ride);
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication);

        // Assert
        assertNotNull(response);
        verify(driverRepository).findByUserUserId(1);
        verify(requestRepository).findById(1);
        verify(vehicleRepository).findById(1);
        verify(matchingCoordinator).beginBroadcastAcceptance(1, 1);
        verify(rideRepository).save(any(SharedRide.class));
        verify(requestRepository).save(any(SharedRideRequest.class));
        verify(matchingCoordinator).completeBroadcastAcceptance(eq(1), any(RideMatchProposalResponse.class));
    }

    @Test
    void acceptBroadcast_InvalidRequestKind_ShouldThrowException() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.JOIN_RIDE);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
        verify(matchingCoordinator, never()).beginBroadcastAcceptance(anyInt(), anyInt());
    }

    @Test
    void acceptBroadcast_InvalidStatus_ShouldThrowException() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
        verify(matchingCoordinator, never()).beginBroadcastAcceptance(anyInt(), anyInt());
    }

    @Test
    void acceptBroadcast_RequestNotFound_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(999, broadcastAcceptRequest, authentication));
    }

    @Test
    void acceptBroadcast_VehicleNotFound_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(vehicleRepository.findById(1)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
    }

    @Test
    void acceptBroadcast_VehicleNotOwnedByDriver_ShouldThrowException() {
        // Arrange
        Vehicle differentVehicle = new Vehicle();
        differentVehicle.setVehicleId(1);
        DriverProfile anotherDriver = new DriverProfile();
        anotherDriver.setDriverId(999);
        differentVehicle.setDriver(anotherDriver);

        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
    }

    @Test
    void acceptBroadcast_DriverHasOngoingRide_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));
        when(rideRepository.existsByDriverDriverIdAndStatus(1, SharedRideStatus.ONGOING)).thenReturn(true);

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
    }

    @Test
    void acceptBroadcast_BroadcastLockFails_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(matchingCoordinator.beginBroadcastAcceptance(1, 1)).thenReturn(false);

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
        verify(rideRepository, never()).save(any(SharedRide.class));
    }

    // Tests for getMatchProposals method
    @Test
    void getMatchProposals_Success_WithValidRequest_ShouldReturnProposals() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        List<RideMatchProposalResponse> proposals = List.of(
            RideMatchProposalResponse.builder()
                .sharedRideId(1)
                .driverId(1)
                .driverName("Test Driver")
                .build()
        );
        when(matchingService.findMatches(rideRequest)).thenReturn(proposals);

        // Act
        List<RideMatchProposalResponse> response = sharedRideRequestService.getMatchProposals(1, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        verify(requestRepository).findById(1);
        verify(matchingService).findMatches(rideRequest);
    }

    @Test
    void getMatchProposals_InvalidRequestKind_ShouldThrowException() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.JOIN_RIDE);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getMatchProposals(1, authentication));
        verify(matchingService, never()).findMatches(any());
    }

    @Test
    void getMatchProposals_InvalidStatus_ShouldThrowException() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getMatchProposals(1, authentication));
        verify(matchingService, never()).findMatches(any());
    }

    @Test
    void getMatchProposals_RequestNotFound_ShouldThrowException() {
        // Arrange
        when(requestRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getMatchProposals(999, authentication));
    }

    @Test
    void getMatchProposals_RequestNotOwnedByRider_ShouldThrowException() {
        // Arrange
        RiderProfile anotherRider = new RiderProfile();
        anotherRider.setRiderId(999);
        rideRequest.setRider(anotherRider);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getMatchProposals(1, authentication));
    }

    @Test
    void getMatchProposals_MatchingFails_ShouldThrowException() {
        // Arrange
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(matchingService.findMatches(rideRequest)).thenThrow(new RuntimeException("Matching failed"));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getMatchProposals(1, authentication));
    }

    // Tests for getRequestsByRider method
    @Test
    void getRequestsByRider_Success_WithStatusFilter_ShouldReturnPagedRequests() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<SharedRideRequest> requestPage = new PageImpl<>(List.of(rideRequest));
        when(requestRepository.findByRiderRiderIdAndStatusOrderByCreatedAtDesc(1, SharedRideRequestStatus.PENDING, pageable))
            .thenReturn(requestPage);

        // Act
        Page<SharedRideRequestResponse> response = sharedRideRequestService.getRequestsByRider(1, "PENDING", pageable, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(requestRepository).findByRiderRiderIdAndStatusOrderByCreatedAtDesc(1, SharedRideRequestStatus.PENDING, pageable);
    }

    @Test
    void getRequestsByRider_Success_WithoutStatusFilter_ShouldReturnAllPagedRequests() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<SharedRideRequest> requestPage = new PageImpl<>(List.of(rideRequest));
        when(requestRepository.findByRiderRiderIdOrderByCreatedAtDesc(1, pageable)).thenReturn(requestPage);

        // Act
        Page<SharedRideRequestResponse> response = sharedRideRequestService.getRequestsByRider(1, null, pageable, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(requestRepository).findByRiderRiderIdOrderByCreatedAtDesc(1, pageable);
    }

    @Test
    void getRequestsByRider_InvalidStatus_ShouldThrowException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert - This should throw IllegalArgumentException when parsing invalid enum
        assertThrows(IllegalArgumentException.class,
            () -> sharedRideRequestService.getRequestsByRider(1, "INVALID_STATUS", pageable, authentication));
    }

    // Tests for getRequestsByRide method
    @Test
    void getRequestsByRide_Success_WithStatusFilter_ShouldReturnPagedRequests() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<SharedRideRequest> requests = List.of(rideRequest);
        when(rideRepository.findById(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.PENDING))
            .thenReturn(requests);

        // Act
        Page<SharedRideRequestResponse> response = sharedRideRequestService.getRequestsByRide(1, "PENDING", pageable, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(rideRepository).findById(1);
        verify(requestRepository).findBySharedRideSharedRideIdAndStatus(1, SharedRideRequestStatus.PENDING);
    }

    @Test
    void getRequestsByRide_RideNotFound_ShouldThrowException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(rideRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.getRequestsByRide(999, null, pageable, authentication));
    }

    // Tests for acceptRequest method
    @Test
    void acceptRequest_Success_WithBookingRequest_ShouldAcceptRequest() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(matchingCoordinator.beginDriverAcceptance(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication);

        // Assert
        assertNotNull(response);
        verify(driverRepository).findByUserUserId(1);
        verify(requestRepository).findById(1);
        verify(rideRepository).findByIdForUpdate(1);
        verify(matchingCoordinator).beginDriverAcceptance(1, 1, 1);
        verify(requestRepository).save(any(SharedRideRequest.class));
        verify(matchingCoordinator).completeDriverAcceptance(1);
    }

    @Test
    void acceptRequest_Success_WithJoinRideRequest_ShouldAcceptRequest() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.JOIN_RIDE);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        rideRequest.setSharedRide(ride);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication);

        // Assert
        assertNotNull(response);
        verify(driverRepository).findByUserUserId(1);
        verify(requestRepository).findById(1);
        verify(rideRepository).findByIdForUpdate(1);
        verify(matchingCoordinator, never()).beginDriverAcceptance(anyInt(), anyInt(), anyInt());
        verify(requestRepository).save(any(SharedRideRequest.class));
    }

    @Test
    void acceptRequest_RequestNotFound_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptRequest(999, acceptRequestDto, authentication));
        verify(rideRepository, never()).findByIdForUpdate(anyInt());
    }

    @Test
    void acceptRequest_RideNotFound_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        // Act & Assert
        AcceptRequestDto dtoWithDifferentRideId = new AcceptRequestDto(999, currentDriverLocation);
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptRequest(1, dtoWithDifferentRideId, authentication));
    }

    @Test
    void acceptRequest_InvalidStatus_ShouldThrowException() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication));
    }

    @Test
    void acceptRequest_RideFull_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void acceptRequest_NotRideOwner_ShouldThrowException() {
        // Arrange
        DriverProfile anotherDriver = new DriverProfile();
        anotherDriver.setDriverId(999);
        ride.setDriver(anotherDriver);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication));
        verify(requestRepository, never()).save(any(SharedRideRequest.class));
    }

    @Test
    void acceptRequest_JoinRideWithDifferentRideId_ShouldThrowException() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.JOIN_RIDE);
        rideRequest.setSharedRide(new SharedRide()); // Different ride
        rideRequest.getSharedRide().setSharedRideId(999);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication));
    }

    // Tests for rejectRequest method
    @Test
    void rejectRequest_Success_WithValidRequest_ShouldRejectRequest() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        rideRequest.setSharedRide(ride);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.rejectRequest(1, "Not interested", authentication);

        // Assert
        assertNotNull(response);
        verify(driverRepository).findByUserUserId(1);
        verify(requestRepository).findById(1);
        verify(matchingCoordinator).rejectJoinRequest(1, "Not interested");
    }

    @Test
    void rejectRequest_RequestNotFound_ShouldThrowException() {
        // Arrange
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.rejectRequest(999, "reason", authentication));
    }

    @Test
    void rejectRequest_InvalidStatus_ShouldThrowException() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.rejectRequest(1, "reason", authentication));
        verify(matchingCoordinator, never()).rejectJoinRequest(anyInt(), anyString());
    }

    @Test
    void rejectRequest_NoAssociatedRide_ShouldThrowException() {
        // Arrange
        rideRequest.setSharedRide(null);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.rejectRequest(1, "reason", authentication));
        verify(matchingCoordinator, never()).rejectJoinRequest(anyInt(), anyString());
    }

    @Test
    void rejectRequest_NotRideOwner_ShouldThrowException() {
        // Arrange
        DriverProfile anotherDriver = new DriverProfile();
        anotherDriver.setDriverId(999);
        ride.setDriver(anotherDriver);
        rideRequest.setSharedRide(ride);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.rejectRequest(1, "reason", authentication));
        verify(matchingCoordinator, never()).rejectJoinRequest(anyInt(), anyString());
    }

    // Tests for cancelRequest method
    @Test
    void cancelRequest_Success_WithPendingRequest_ShouldCancelRequest() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        // releaseRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(requestRepository).findById(1);
        verify(riderRepository).findByUserUserId(1);
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
        verify(requestRepository).save(any(SharedRideRequest.class));

        ArgumentCaptor<SharedRideRequest> requestCaptor = ArgumentCaptor.forClass(SharedRideRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        assertEquals(SharedRideRequestStatus.CANCELLED, requestCaptor.getValue().getStatus());
    }

    @Test
    void cancelRequest_Success_WithConfirmedRequest_WithinGracePeriod_ShouldCancelRequest() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
        rideRequest.setCreatedAt(LocalDateTime.now().minusMinutes(5)); // Within grace period
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        RideConfigurationProperties.Cancellation cancellationConfig = new RideConfigurationProperties.Cancellation();
        cancellationConfig.setGracePeriodMinutes(10);
        cancellationConfig.setFeePercentage(BigDecimal.valueOf(0.2));
        when(rideConfig.getCancellation()).thenReturn(cancellationConfig);
        // releaseRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
    }

    @Test
    void cancelRequest_Success_WithConfirmedRequest_OutsideGracePeriod_ShouldApplyCancellationFee() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
        rideRequest.setCreatedAt(LocalDateTime.now().minusMinutes(30)); // Outside grace period
        rideRequest.setTotalFare(BigDecimal.valueOf(50000));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        RideConfigurationProperties.Cancellation cancellationConfig = new RideConfigurationProperties.Cancellation();
        cancellationConfig.setGracePeriodMinutes(10);
        cancellationConfig.setFeePercentage(BigDecimal.valueOf(0.2));
        when(rideConfig.getCancellation()).thenReturn(cancellationConfig);
        // releaseRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
    }

    @Test
    void cancelRequest_RequestNotFound_ShouldThrowException() {
        // Arrange
        when(requestRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.cancelRequest(999, authentication));
        verify(riderRepository, never()).findByUserUserId(anyInt());
    }

    @Test
    void cancelRequest_InvalidStatus_ShouldThrowException() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.COMPLETED);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.cancelRequest(1, authentication));
        verify(rideFundCoordinatingService, never()).releaseRideFunds(any());
    }

    @Test
    void cancelRequest_NotRequestOwner_ShouldThrowException() {
        // Arrange
        RiderProfile anotherRider = new RiderProfile();
        anotherRider.setRiderId(999);
        rideRequest.setRider(anotherRider);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));

        // Act & Assert
        assertThrows(BaseDomainException.class,
            () -> sharedRideRequestService.cancelRequest(1, authentication));
        verify(rideFundCoordinatingService, never()).releaseRideFunds(any());
    }

    @Test
    void cancelRequest_AdminUser_ShouldAllowCancellation() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        RiderProfile anotherRider = new RiderProfile();
        anotherRider.setRiderId(999);
        rideRequest.setRider(anotherRider);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(authentication.getAuthorities()).thenReturn((Collection) Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        // releaseRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(riderRepository, never()).findByUserUserId(anyInt()); // Should not check rider ownership for admin
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
    }

    @Test
    void cancelRequest_BookingRequest_ShouldCancelMatching() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        // releaseRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(matchingCoordinator).cancelMatching(1);
    }

    @Test
    void cancelRequest_WalletReleaseFails_ShouldContinueWithCancellation() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        doThrow(new RuntimeException("Wallet release failed")).when(rideFundCoordinatingService)
            .releaseRideFunds(any(RideHoldReleaseRequest.class));

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
        verify(requestRepository).save(any(SharedRideRequest.class)); // Should still save the cancellation
    }

    // Tests for boundary and edge cases
    @Test
    void createAIBookingRequest_MinimumValidData_ShouldCreateRequest() {
        // Arrange - Test with minimum valid data
        CreateRideRequestDto minimalRequest = new CreateRideRequestDto(
            quote.quoteId(),
            null, // No pickup time
            null
        );
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        // holdRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.createAIBookingRequest(minimalRequest, authentication);

        // Assert
        assertNotNull(response);
        verify(requestRepository).save(any(SharedRideRequest.class));
    }

    @Test
    void createAIBookingRequest_MaximumNotesLength_ShouldCreateRequest() {
        // Arrange - Test with maximum notes length
        String maxLengthNotes = "a".repeat(500);
        CreateRideRequestDto requestWithMaxNotes = new CreateRideRequestDto(
            quote.quoteId(),
            LocalDateTime.now().plusHours(2),
            maxLengthNotes
        );
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(quote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        // holdRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.createAIBookingRequest(requestWithMaxNotes, authentication);

        // Assert
        assertNotNull(response);
        ArgumentCaptor<SharedRideRequest> requestCaptor = ArgumentCaptor.forClass(SharedRideRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        assertEquals(maxLengthNotes, requestCaptor.getValue().getSpecialRequests());
    }

    @Test
    void createAIBookingRequest_ZeroFare_ShouldCreateRequest() {
        // Arrange - Test with zero fare (edge case)
        Quote zeroFareQuote = new Quote(
            UUID.randomUUID(),
            1,
            pickupLocation,
            dropoffLocation,

//            1,
//            2,
//            10.762622,
//            106.660172,
//            10.772622,
//            106.670172,
            0L, // Zero distance
            0L, // Zero duration
            "encoded_polyline",
            new FareBreakdown(
                Instant.now(),
                0L,
                MoneyVnd.VND(BigDecimal.ZERO),
                MoneyVnd.VND(BigDecimal.ZERO),
                MoneyVnd.VND(BigDecimal.ZERO),
                BigDecimal.ZERO
            ),
            Instant.now(),
            Instant.now().plusSeconds(300)
        );
        CreateRideRequestDto requestWithZeroFare = new CreateRideRequestDto(
            zeroFareQuote.quoteId(),
            LocalDateTime.now().plusHours(2),
            "Zero fare test"
        );
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        when(quoteService.getQuote(any(UUID.class))).thenReturn(zeroFareQuote);
        when(pricingConfigRepository.findByVersion(any(Instant.class))).thenReturn(Optional.of(pricingConfig));
        when(requestRepository.save(any(SharedRideRequest.class))).thenReturn(rideRequest);
        // holdRideFunds returns void, so we don't need to set up a return value

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.createAIBookingRequest(requestWithZeroFare, authentication);

        // Assert
        assertNotNull(response);
        verify(requestRepository).save(any(SharedRideRequest.class));
    }

    @Test
    void getRequestsByRider_EmptyResult_ShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<SharedRideRequest> emptyPage = new PageImpl<>(Collections.emptyList());
        when(requestRepository.findByRiderRiderIdOrderByCreatedAtDesc(1, pageable)).thenReturn(emptyPage);

        // Act
        Page<SharedRideRequestResponse> response = sharedRideRequestService.getRequestsByRider(1, null, pageable, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    void getRequestsByRide_EmptyResult_ShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(rideRepository.findById(1)).thenReturn(Optional.of(ride));
        when(requestRepository.findBySharedRideSharedRideId(1)).thenReturn(Collections.emptyList());

        // Act
        Page<SharedRideRequestResponse> response = sharedRideRequestService.getRequestsByRide(1, null, pageable, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    void cancelRequest_ReleaseFundsFails_ShouldContinueWithCancellation() {
        // Arrange
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(riderRepository.findByUserUserId(1)).thenReturn(Optional.of(rider));
        doThrow(new RuntimeException("Release failed")).when(rideFundCoordinatingService)
            .releaseRideFunds(any(RideHoldReleaseRequest.class));

        // Act
        SharedRideRequestResponse response = sharedRideRequestService.cancelRequest(1, authentication);

        // Assert
        assertNotNull(response);
        verify(rideFundCoordinatingService).releaseRideFunds(any(RideHoldReleaseRequest.class));
        verify(requestRepository).save(any(SharedRideRequest.class)); // Should still proceed with cancellation
    }

    @Test
    void acceptBroadcast_ConcurrentModification_ShouldHandleGracefully() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.BROADCASTING);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(vehicleRepository.findById(1)).thenReturn(Optional.of(vehicle));
        when(rideRepository.existsByDriverDriverIdAndStatus(1, SharedRideStatus.ONGOING)).thenReturn(false);
        when(matchingCoordinator.beginBroadcastAcceptance(anyInt(), anyInt())).thenReturn(true);
        when(locationRepository.save(any(Location.class))).thenThrow(new RuntimeException("Concurrent modification"));

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> sharedRideRequestService.acceptBroadcast(1, broadcastAcceptRequest, authentication));
        verify(matchingCoordinator).failBroadcastAcceptance(eq(1), anyString());
    }

    @Test
    void acceptRequest_MatchingCoordinatorFails_ShouldRollbackChanges() {
        // Arrange
        rideRequest.setRequestKind(RequestKind.BOOKING);
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        when(driverRepository.findByUserUserId(1)).thenReturn(Optional.of(driver));
        when(requestRepository.findById(1)).thenReturn(Optional.of(rideRequest));
        when(rideRepository.findByIdForUpdate(1)).thenReturn(Optional.of(ride));
        when(matchingCoordinator.beginDriverAcceptance(anyInt(), anyInt(), anyInt())).thenReturn(true);
        doThrow(new RuntimeException("Coordinator failed")).when(matchingCoordinator).completeDriverAcceptance(1);

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> sharedRideRequestService.acceptRequest(1, acceptRequestDto, authentication));
        verify(matchingCoordinator).failDriverAcceptance(1, "Coordinator failed");
    }
}

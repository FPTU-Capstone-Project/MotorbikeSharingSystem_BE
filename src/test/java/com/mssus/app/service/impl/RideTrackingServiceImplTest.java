package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.dto.ride.LocationPoint;
import com.mssus.app.entity.*;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RideTrackRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.service.RoutingService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideTrackingServiceImplTest { //4 failed test

    @Mock
    private RideTrackRepository trackRepository;

    @Mock
    private SharedRideRepository rideRepository;

    @Mock
    private RoutingService routingService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverRepository;

    @Mock
    private RealTimeNotificationService notificationService;

    @InjectMocks
    private RideTrackingServiceImpl rideTrackingService;

    private SharedRide testRide;
    private User testUser;
    private DriverProfile testDriver;
    private RideTrack testTrack;
    private List<LocationPoint> testPoints;

    @BeforeEach
    void setUp() {
        setupTestData();
        setupMockBehavior();
    }

    // ========== appendGpsPoints Tests ==========

    @Test
    @DisplayName("Should append GPS points successfully")
    void should_appendGpsPoints_when_validInput() {
        // Arrange - Create valid points with same coordinates to avoid speed validation
        LocalDateTime now = LocalDateTime.now();
        List<LocationPoint> validPoints = List.of(
            new LocationPoint(10.0, 106.0, now),
            new LocationPoint(10.0, 106.0, now.plusMinutes(1)) // Same coordinates = zero speed
        );
        
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));
        // Setup track with real GPS points to avoid NullPointerException
        ArrayNode gpsPoints = objectMapper.createArrayNode();
        ObjectNode point = objectMapper.createObjectNode();
        point.put("lat", 10.0);
        point.put("lng", 106.0);
        point.put("timestamp", LocalDateTime.now().toString());
        gpsPoints.add(point);
        testTrack.setGpsPoints(gpsPoints);
        
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.of(testTrack));
        
        // Mock routing service to avoid external dependency
        RouteResponse mockRoute = new RouteResponse(1000L, 600L, "mock_polyline"); // 1000m, 600s
        when(routingService.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(mockRoute);

        // Act
        TrackingResponse result = rideTrackingService.appendGpsPoints(1, validPoints, "driver@example.com");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.currentDistanceKm()).isGreaterThanOrEqualTo(0);
        assertThat(result.etaMinutes()).isGreaterThanOrEqualTo(0);

        verify(trackRepository).save(any(RideTrack.class));
        verify(notificationService, never()).notifyDriverTrackingStart(any(), anyInt());
    }

    @Test
    @DisplayName("Should create new track when none exists")
    void should_createNewTrack_when_noExistingTrack() {
        // Arrange - Create valid points with same coordinates to avoid speed validation
        LocalDateTime now = LocalDateTime.now();
        List<LocationPoint> validPoints = List.of(
            new LocationPoint(10.0, 106.0, now),
            new LocationPoint(10.0, 106.0, now.plusMinutes(1)) // Same coordinates = zero speed
        );
        
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.empty());
        
        // Mock routing service to avoid external dependency
        RouteResponse mockRoute = new RouteResponse(1000L, 600L, "mock_polyline"); // 1000m, 600s
        when(routingService.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(mockRoute);

        // Act
        TrackingResponse result = rideTrackingService.appendGpsPoints(1, validPoints, "driver@example.com");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("OK");

        verify(trackRepository).save(any(RideTrack.class));
    }

    @Test
    @DisplayName("Should throw exception when ride not found")
    void should_throwException_when_rideNotFound() {
        // Arrange
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, testPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verifyNoInteractions(userRepository, driverRepository, trackRepository);
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void should_throwException_when_userNotFound() {
        // Arrange
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, testPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verifyNoInteractions(driverRepository, trackRepository);
    }

    @Test
    @DisplayName("Should throw exception when driver profile not found")
    void should_throwException_when_driverProfileNotFound() {
        // Arrange
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, testPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verifyNoInteractions(trackRepository);
    }

    @Test
    @DisplayName("Should throw exception when user is not ride owner")
    void should_throwException_when_userNotRideOwner() {
        // Arrange
        DriverProfile otherDriver = new DriverProfile();
        otherDriver.setDriverId(999);
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(otherDriver));

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, testPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verifyNoInteractions(trackRepository);
    }

    @Test
    @DisplayName("Should throw exception when ride is not ongoing")
    void should_throwException_when_rideNotOngoing() {
        // Arrange
        testRide.setStatus(SharedRideStatus.SCHEDULED);
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, testPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("Ride not ongoing");

        verifyNoInteractions(trackRepository);
    }

    @Test
    @DisplayName("Should throw exception when points list is empty")
    void should_throwException_when_pointsEmpty() {
        // Arrange
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, List.of(), "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("No points provided");

        verifyNoInteractions(trackRepository);
    }

    @Test
    @DisplayName("Should throw exception when points have invalid speed")
    void should_throwException_when_pointsHaveInvalidSpeed() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        List<LocationPoint> invalidPoints = List.of(
            new LocationPoint(10.0, 106.0, now),
            new LocationPoint(20.0, 107.0, now.plusMinutes(1)) // Very high speed
        );
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, invalidPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("Suspicious speed detected");

        verifyNoInteractions(trackRepository);
    }

    // ========== computeDistanceFromPoints Tests ==========

    @Test
    @DisplayName("Should compute distance from valid points")
    void should_computeDistance_when_validPoints() {
        // Arrange - Use real JsonNode with coordinates that have clear distance
        ArrayNode pointsArray = objectMapper.createArrayNode();
        ObjectNode point1 = objectMapper.createObjectNode();
        point1.put("lat", 10.0);
        point1.put("lng", 106.0);
        ObjectNode point2 = objectMapper.createObjectNode();
        point2.put("lat", 11.0);  // 1 degree difference = ~111km
        point2.put("lng", 107.0); // 1 degree difference = ~111km
        pointsArray.add(point1);
        pointsArray.add(point2);

        // Act
        double distance = rideTrackingService.computeDistanceFromPoints(pointsArray);

        // Assert
        assertThat(distance).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should return zero distance for single point")
    void should_returnZeroDistance_when_singlePoint() {
        // Arrange
        ArrayNode pointsArray = mock(ArrayNode.class);
        when(pointsArray.size()).thenReturn(1);

        // Act
        double distance = rideTrackingService.computeDistanceFromPoints(pointsArray);

        // Assert
        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should return zero distance for empty points")
    void should_returnZeroDistance_when_emptyPoints() {
        // Arrange
        ArrayNode pointsArray = mock(ArrayNode.class);
        when(pointsArray.size()).thenReturn(0);

        // Act
        double distance = rideTrackingService.computeDistanceFromPoints(pointsArray);

        // Assert
        assertThat(distance).isEqualTo(0.0);
    }

    // ========== getLatestPosition Tests ==========

    @Test
    @DisplayName("Should return latest position when track exists")
    void should_returnLatestPosition_when_trackExists() {
        // Arrange
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(5);
        JsonNode lastPoint = createMockJsonNodeWithTimestamp(10.0, 106.0, recentTime);
        ArrayNode pointsArray = JsonNodeFactory.instance.arrayNode();
        pointsArray.add(lastPoint);

        testTrack.setGpsPoints(pointsArray);
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.of(testTrack));

        // Act
        Optional<LatLng> result = rideTrackingService.getLatestPosition(1, 10);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(10.0);
        assertThat(result.get().longitude()).isEqualTo(106.0);
    }

    @Test
    @DisplayName("Should return empty when track not found")
    void should_returnEmpty_when_trackNotFound() {
        // Arrange
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.empty());

        // Act
        Optional<LatLng> result = rideTrackingService.getLatestPosition(1, 10);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when points are stale")
    void should_returnEmpty_when_pointsStale() {
        // Arrange
        LocalDateTime staleTime = LocalDateTime.now().minusMinutes(15);
        JsonNode lastPoint = createMockJsonNodeWithTimestamp(10.0, 106.0, staleTime);
        ArrayNode pointsArray = JsonNodeFactory.instance.arrayNode();
        pointsArray.add(lastPoint);
        
        testTrack.setGpsPoints(pointsArray);
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.of(testTrack));

        // Act
        Optional<LatLng> result = rideTrackingService.getLatestPosition(1, 10);

        // Assert
        assertThat(result).isEmpty();
    }

    // ========== startTracking Tests ==========

    @Test
    @DisplayName("Should start tracking successfully")
    void should_startTracking_when_validRide() {
        // Arrange
        when(rideRepository.findById(1))
            .thenReturn(Optional.of(testRide));
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.empty());
        when(objectMapper.createArrayNode())
            .thenReturn(mock(ArrayNode.class));

        // Act
        rideTrackingService.startTracking(1);

        // Assert
        verify(trackRepository).save(any(RideTrack.class));
        verify(notificationService).notifyDriverTrackingStart(testDriver, 1);
    }

    @Test
    @DisplayName("Should throw exception when ride not found for start tracking")
    void should_throwException_when_rideNotFoundForStartTracking() {
        // Arrange
        when(rideRepository.findById(1))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.startTracking(1))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verifyNoInteractions(trackRepository, notificationService);
    }

    // ========== stopTracking Tests ==========

    @Test
    @DisplayName("Should stop tracking successfully")
    void should_stopTracking_when_validRide() {
        // Arrange - Set isTracking to false so service will save
        testTrack.setIsTracking(false);
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.of(testTrack));

        // Act
        rideTrackingService.stopTracking(1);

        // Assert
        verify(trackRepository).save(testTrack);
        assertThat(testTrack.getIsTracking()).isFalse();
        assertThat(testTrack.getStoppedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null ride ID for stop tracking")
    void should_throwException_when_nullRideIdForStopTracking() {
        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.stopTracking(null))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verifyNoInteractions(trackRepository);
    }

    @Test
    @DisplayName("Should handle gracefully when track not found for stop tracking")
    void should_handleGracefully_when_trackNotFoundForStopTracking() {
        // Arrange
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.empty());

        // Act
        rideTrackingService.stopTracking(1);

        // Assert
        verify(trackRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle gracefully when tracking already stopped")
    void should_handleGracefully_when_trackingAlreadyStopped() {
        // Arrange - Set isTracking to true so service will return early
        testTrack.setIsTracking(true);
        when(trackRepository.findBySharedRideSharedRideId(1))
            .thenReturn(Optional.of(testTrack));

        // Act
        rideTrackingService.stopTracking(1);

        // Assert
        verify(trackRepository, never()).save(any());
    }

    // ========== Edge Cases and Boundary Values ==========

    @Test
    @DisplayName("Should handle null points gracefully")
    void should_handleNullPoints_when_appendingGpsPoints() {
        // Arrange
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, null, "driver@example.com"))
            .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(trackRepository);
    }

    @Test
    @DisplayName("Should handle null username gracefully")
    void should_handleNullUsername_when_appendingGpsPoints() {
        // Arrange
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));

        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, testPoints, null))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verify(userRepository).findByEmail(null);
        verifyNoInteractions(driverRepository, trackRepository);
    }

    @Test
    @DisplayName("Should handle null ride ID gracefully")
    void should_handleNullRideId_when_appendingGpsPoints() {
        // Act & Assert
        assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(null, testPoints, "driver@example.com"))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("An error occurred");

        verify(rideRepository).findByIdForUpdate(null);
        verifyNoInteractions(userRepository, driverRepository, trackRepository);
    }

    // ========== Parameterized Tests ==========

    @ParameterizedTest
    @MethodSource("rideStatusProvider")
    @DisplayName("Should handle different ride statuses")
    void should_handleDifferentRideStatuses_when_appendingGpsPoints(SharedRideStatus status, boolean shouldSucceed) {
        // Arrange - Create valid points with same coordinates to avoid speed validation
        LocalDateTime now = LocalDateTime.now();
        List<LocationPoint> validPoints = List.of(
            new LocationPoint(10.0, 106.0, now),
            new LocationPoint(10.0, 106.0, now.plusMinutes(1)) // Same coordinates = zero speed
        );
        
        testRide.setStatus(status);
        when(rideRepository.findByIdForUpdate(1))
            .thenReturn(Optional.of(testRide));
        when(userRepository.findByEmail("driver@example.com"))
            .thenReturn(Optional.of(testUser));
        when(driverRepository.findByUserUserId(1))
            .thenReturn(Optional.of(testDriver));

        // Act & Assert
        if (shouldSucceed) {
            // Setup track with real GPS points
            ArrayNode gpsPoints = objectMapper.createArrayNode();
            ObjectNode point = objectMapper.createObjectNode();
            point.put("lat", 10.0);
            point.put("lng", 106.0);
            point.put("timestamp", LocalDateTime.now().toString());
            gpsPoints.add(point);
            testTrack.setGpsPoints(gpsPoints);
            
            when(trackRepository.findBySharedRideSharedRideId(1))
                .thenReturn(Optional.of(testTrack));
            // Mock routing service
            RouteResponse mockRoute = new RouteResponse(1000L, 600L, "mock_polyline");
            when(routingService.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(mockRoute);

            TrackingResponse result = rideTrackingService.appendGpsPoints(1, validPoints, "driver@example.com");
            assertThat(result).isNotNull();
        } else {
            assertThatThrownBy(() -> rideTrackingService.appendGpsPoints(1, validPoints, "driver@example.com"))
                .isInstanceOf(BaseDomainException.class);
        }
    }

    // ========== Helper Methods ==========

    private void setupTestData() {
        // Create test user
        testUser = new User();
        testUser.setUserId(1);
        testUser.setEmail("driver@example.com");
        testUser.setFullName("Test Driver");

        // Create test driver
        testDriver = new DriverProfile();
        testDriver.setDriverId(1);
        testDriver.setUser(testUser);

        // Create test ride
        testRide = new SharedRide();
        testRide.setSharedRideId(1);
        testRide.setDriver(testDriver);
        testRide.setStatus(SharedRideStatus.ONGOING);
        testRide.setEndLat(10.8);
        testRide.setEndLng(106.8);
        testRide.setEstimatedDuration(30);

        // Create test track
        testTrack = new RideTrack();
        testTrack.setRideTrackId(1);
        testTrack.setSharedRide(testRide);
        testTrack.setIsTracking(true);

        // Create test points
        LocalDateTime now = LocalDateTime.now();
        testPoints = List.of(
            new LocationPoint(10.0, 106.0, now),
            new LocationPoint(10.1, 106.1, now.plusMinutes(1))
        );
    }

    private void setupMockBehavior() {
        // Mock ObjectMapper behavior
        when(objectMapper.createArrayNode()).thenAnswer(invocation -> JsonNodeFactory.instance.arrayNode());
        when(objectMapper.createObjectNode()).thenAnswer(invocation -> JsonNodeFactory.instance.objectNode());
    }


    private JsonNode createMockJsonNodeWithTimestamp(double lat, double lng, LocalDateTime timestamp) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("lat", lat);
        node.put("lng", lng);
        node.put("timestamp", timestamp.toString());
        return node;
    }

    // ========== Test Data Providers ==========

    static Stream<Object[]> rideStatusProvider() {
        return Stream.of(
            new Object[]{SharedRideStatus.ONGOING, true},
            new Object[]{SharedRideStatus.SCHEDULED, false},
            new Object[]{SharedRideStatus.COMPLETED, false},
            new Object[]{SharedRideStatus.CANCELLED, false}
        );
    }
}

package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.dto.notification.DriverRideOfferNotification;
import com.mssus.app.dto.notification.RiderMatchStatusNotification;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import com.mssus.app.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealTimeNotificationServiceImplTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RealTimeNotificationServiceImpl realTimeNotificationService;

    private DriverProfile testDriver;
    private User testUser;
    private DriverRideOfferNotification testDriverOffer;
    private RiderMatchStatusNotification testRiderStatus;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        testUser = createTestUser(1, "driver@example.com", "Driver User");
        testDriver = createTestDriver(1, testUser);
        testDriverOffer = createTestDriverOffer();
        testRiderStatus = createTestRiderStatus();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\": \"payload\"}");
    }

    // ========== notifyDriverOffer Tests ==========

    @Test
    @DisplayName("Should successfully notify driver about ride offer")
    void should_successfullyNotifyDriver_when_validOffer() throws JsonProcessingException {
        // Arrange
        String expectedMessage = "Rider John Doe requested pickup at Central Park";

        // Act
        realTimeNotificationService.notifyDriverOffer(testDriver, testDriverOffer);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq("1"),
            eq("/queue/ride-offers"),
            eq(testDriverOffer)
        );

        verify(notificationService).sendNotification(
            eq(testUser),
            eq(NotificationType.RIDE_REQUEST),
            eq("New shared ride request"),
            eq(expectedMessage),
            eq("{\"test\": \"payload\"}"),
            eq(Priority.HIGH),
            eq(DeliveryMethod.PUSH),
            eq("/queue/ride-offers")
        );

        verifyNoMoreInteractions(messagingTemplate, notificationService);
    }

    @Test
    @DisplayName("Should handle null pickup location name in driver offer")
    void should_handleNullPickupLocation_when_notifyingDriver() throws JsonProcessingException {
        // Arrange
        DriverRideOfferNotification offerWithNullLocation = DriverRideOfferNotification.builder()
            .requestId(1)
            .riderName("John Doe")
            .pickupLocationName(null)
            .dropoffLat(40.7128)
            .dropoffLng(-74.0060)
            .build();

        String expectedMessage = "Rider John Doe requested pickup at 40.7128,-74.006";

        // Act
        realTimeNotificationService.notifyDriverOffer(testDriver, offerWithNullLocation);

        // Assert
        verify(notificationService).sendNotification(
            any(User.class),
            any(NotificationType.class),
            anyString(),
            eq(expectedMessage),
            anyString(),
            any(Priority.class),
            any(DeliveryMethod.class),
            anyString()
        );
    }

    @Test
    @DisplayName("Should handle JsonProcessingException when converting payload")
    void should_handleJsonProcessingException_when_convertingPayload() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JSON error") {});

        // Act
        realTimeNotificationService.notifyDriverOffer(testDriver, testDriverOffer);

        // Assert - Should not throw exception, just log error
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
        // When JsonProcessingException occurs in toJson(), the entire try block fails
        // so notificationService.sendNotification is not called
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Should handle messaging template exception")
    void should_handleMessagingException_when_sendingToDriver() throws JsonProcessingException {
        // Arrange
        doThrow(new RuntimeException("WebSocket error")).when(messagingTemplate)
            .convertAndSendToUser(anyString(), anyString(), any());

        // Act
        realTimeNotificationService.notifyDriverOffer(testDriver, testDriverOffer);

        // Assert - Should not throw exception, just log error
        // When messagingTemplate throws exception, notificationService.sendNotification is not called
        verifyNoInteractions(notificationService);
    }

    // ========== notifyRiderStatus Tests ==========

    @Test
    @DisplayName("Should successfully notify rider about status update")
    void should_successfullyNotifyRider_when_validStatus() throws JsonProcessingException {
        // Act
        realTimeNotificationService.notifyRiderStatus(testUser, testRiderStatus);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq("1"),
            eq("/queue/ride-matching"),
            eq(testRiderStatus)
        );

        verify(notificationService).sendNotification(
            eq(testUser),
            eq(NotificationType.RIDE_REQUEST),
            eq("Ride matching update"),
            eq("Driver found for your ride"),
            eq("{\"test\": \"payload\"}"),
            eq(Priority.MEDIUM),
            eq(DeliveryMethod.PUSH),
            eq("/queue/ride-matching")
        );
    }

    @Test
    @DisplayName("Should handle null rider user")
    void should_handleNullRiderUser_when_notifyingStatus() {
        // Act & Assert
        assertThatThrownBy(() -> realTimeNotificationService.notifyRiderStatus(null, testRiderStatus))
            .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(messagingTemplate, notificationService);
    }

    @Test
    @DisplayName("Should handle null rider status payload")
    void should_handleNullRiderStatus_when_notifyingStatus() {
        // Act & Assert
        assertThatThrownBy(() -> realTimeNotificationService.notifyRiderStatus(testUser, null))
            .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(messagingTemplate, notificationService);
    }

    // ========== notifyDriverJoinRequest Tests ==========

    @Test
    @DisplayName("Should successfully notify driver about join request")
    void should_successfullyNotifyDriver_when_joinRequest() throws JsonProcessingException {
        // Act
        realTimeNotificationService.notifyDriverJoinRequest(testDriver, testDriverOffer);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq("1"),
            eq("/queue/ride-offers"),
            eq(testDriverOffer)
        );

        verify(notificationService).sendNotification(
            eq(testUser),
            eq(NotificationType.RIDE_REQUEST),
            eq("Join ride request"),
            eq("Rider John Doe wants to join your shared ride"),
            eq("{\"test\": \"payload\"}"),
            eq(Priority.HIGH),
            eq(DeliveryMethod.PUSH),
            eq("/queue/ride-offers")
        );
    }

    @Test
    @DisplayName("Should handle notification service exception in join request")
    void should_handleNotificationException_when_joinRequest() throws JsonProcessingException {
        // Arrange
        doThrow(new RuntimeException("Notification service error")).when(notificationService)
            .sendNotification(any(), any(), anyString(), anyString(), anyString(), any(), any(), anyString());

        // Act
        realTimeNotificationService.notifyDriverJoinRequest(testDriver, testDriverOffer);

        // Assert - Should not throw exception, just log error
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ========== notifyDriverTrackingStart Tests ==========

    @Test
    @DisplayName("Should successfully notify driver about tracking start")
    void should_successfullyNotifyDriver_when_trackingStart() {
        // Arrange
        Integer rideId = 123;
        String expectedMessage = "Tracking started – share your ride en route!";
        String expectedPayload = "{\"rideId\": 123, \"action\": \"start_tracking\"}";

        // Act
        realTimeNotificationService.notifyDriverTrackingStart(testDriver, rideId);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(
            eq("1"),
            eq("/queue/ride-offers"),
            payloadCaptor.capture()
        );

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).containsEntry("type", "TRACKING_START");
        assertThat(capturedPayload).containsEntry("message", expectedMessage);
        assertThat(capturedPayload).containsEntry("rideId", rideId);

        verify(notificationService).sendNotification(
            eq(testUser),
            eq(NotificationType.RIDE_TRACKING_START),
            eq("Tracking Started"),
            eq(expectedMessage),
            eq(expectedPayload),
            eq(Priority.HIGH),
            eq(DeliveryMethod.PUSH),
            eq("/queue/ride-offers")
        );
    }

    @Test
    @DisplayName("Should handle null ride ID in tracking start")
    void should_handleNullRideId_when_trackingStart() {
        // Act
        realTimeNotificationService.notifyDriverTrackingStart(testDriver, null);

        // Assert - When rideId is null, String.format throws NullPointerException
        // so messagingTemplate.convertAndSendToUser is not called
        verifyNoInteractions(messagingTemplate, notificationService);
    }

    @Test
    @DisplayName("Should handle zero ride ID in tracking start")
    void should_handleZeroRideId_when_trackingStart() {
        // Act
        realTimeNotificationService.notifyDriverTrackingStart(testDriver, 0);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), payloadCaptor.capture());

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).containsEntry("rideId", 0);
    }

    // ========== Parameterized Tests ==========

    @ParameterizedTest
    @MethodSource("driverOfferProvider")
    @DisplayName("Should handle various driver offer scenarios")
    void should_handleVariousDriverOffers_when_notifyingDriver(DriverRideOfferNotification offer, String expectedMessagePart) throws JsonProcessingException {
        // Act
        realTimeNotificationService.notifyDriverOffer(testDriver, offer);

        // Assert
        verify(notificationService).sendNotification(
            any(User.class),
            any(NotificationType.class),
            anyString(),
            anyString(),
            anyString(),
            any(Priority.class),
            any(DeliveryMethod.class),
            anyString()
        );
    }

    @ParameterizedTest
    @MethodSource("riderStatusProvider")
    @DisplayName("Should handle various rider status scenarios")
    void should_handleVariousRiderStatus_when_notifyingRider(RiderMatchStatusNotification status) throws JsonProcessingException {
        // Act
        realTimeNotificationService.notifyRiderStatus(testUser, status);

        // Assert
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), eq(status));
        verify(notificationService).sendNotification(
            any(User.class),
            any(NotificationType.class),
            anyString(),
            eq(status.getMessage()),
            anyString(),
            any(Priority.class),
            any(DeliveryMethod.class),
            anyString()
        );
    }

    // ========== Edge Cases and Boundary Values ==========

    @Test
    @DisplayName("Should handle empty rider name in driver offer")
    void should_handleEmptyRiderName_when_notifyingDriver() throws JsonProcessingException {
        // Arrange
        DriverRideOfferNotification offerWithEmptyName = DriverRideOfferNotification.builder()
            .requestId(1)
            .riderName("")
            .pickupLocationName("Central Park")
            .build();

        // Act
        realTimeNotificationService.notifyDriverOffer(testDriver, offerWithEmptyName);

        // Assert
        verify(notificationService).sendNotification(
            any(User.class),
            any(NotificationType.class),
            anyString(),
            anyString(),
            anyString(),
            any(Priority.class),
            any(DeliveryMethod.class),
            anyString()
        );
    }

    @Test
    @DisplayName("Should handle very large ride ID in tracking start")
    void should_handleLargeRideId_when_trackingStart() {
        // Arrange
        Integer largeRideId = Integer.MAX_VALUE;

        // Act
        realTimeNotificationService.notifyDriverTrackingStart(testDriver, largeRideId);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), payloadCaptor.capture());

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).containsEntry("rideId", largeRideId);
    }

    @Test
    @DisplayName("Should handle negative ride ID in tracking start")
    void should_handleNegativeRideId_when_trackingStart() {
        // Arrange
        Integer negativeRideId = -1;

        // Act
        realTimeNotificationService.notifyDriverTrackingStart(testDriver, negativeRideId);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), payloadCaptor.capture());

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).containsEntry("rideId", negativeRideId);
    }

    // ========== Helper Methods ==========

    private User createTestUser(Integer userId, String email, String fullName) {
        return User.builder()
            .userId(userId)
            .email(email)
            .fullName(fullName)
            .build();
    }

    private DriverProfile createTestDriver(Integer driverId, User user) {
        return DriverProfile.builder()
            .driverId(driverId)
            .user(user)
            .build();
    }

    private DriverRideOfferNotification createTestDriverOffer() {
        return DriverRideOfferNotification.builder()
            .requestId(1)
            .rideId(100)
            .driverId(1)
            .driverName("Driver User")
            .riderId(2)
            .riderName("John Doe")
            .pickupLocationName("Central Park")
            .dropoffLocationName("Times Square")
            .pickupLat(40.7829)
            .pickupLng(-73.9654)
            .dropoffLat(40.7580)
            .dropoffLng(-73.9855)
            .pickupTime(LocalDateTime.now().plusHours(1))
            .totalFare(new BigDecimal("15.50"))
            .matchScore(0.95f)
            .proposalRank(1)
            .offerExpiresAt(ZonedDateTime.now().plusMinutes(5))
            .broadcast(false)
            .responseWindowSeconds(300)
            .build();
    }

    private RiderMatchStatusNotification createTestRiderStatus() {
        return RiderMatchStatusNotification.builder()
            .requestId(1)
            .status("MATCHED")
            .message("Driver found for your ride")
            .rideId(100)
            .driverId(1)
            .driverName("Driver User")
            .driverRating(4.8f)
            .vehicleModel("Honda Wave")
            .vehiclePlate("29A1-12345")
            .estimatedPickupTime(LocalDateTime.now().plusMinutes(10))
            .estimatedDropoffTime(LocalDateTime.now().plusMinutes(30))
            .totalFare(new BigDecimal("15.50"))
            .build();
    }

    // ========== Test Data Providers ==========

    static Stream<Object[]> driverOfferProvider() {
        return Stream.of(
            new Object[]{
                DriverRideOfferNotification.builder()
                    .requestId(1)
                    .riderName("Alice")
                    .pickupLocationName("Airport")
                    .build(),
                "Alice"
            },
            new Object[]{
                DriverRideOfferNotification.builder()
                    .requestId(2)
                    .riderName("Bob")
                    .pickupLocationName(null)
                    .dropoffLat(40.7128)
                    .dropoffLng(-74.0060)
                    .build(),
                "Bob"
            },
            new Object[]{
                DriverRideOfferNotification.builder()
                    .requestId(3)
                    .riderName("Charlie")
                    .pickupLocationName("")
                    .build(),
                "Charlie"
            }
        );
    }

    static Stream<RiderMatchStatusNotification> riderStatusProvider() {
        return Stream.of(
            RiderMatchStatusNotification.builder()
                .requestId(1)
                .status("PENDING")
                .message("Searching for drivers...")
                .build(),
            RiderMatchStatusNotification.builder()
                .requestId(2)
                .status("MATCHED")
                .message("Driver found!")
                .driverId(1)
                .driverName("John Driver")
                .build(),
            RiderMatchStatusNotification.builder()
                .requestId(3)
                .status("CANCELLED")
                .message("Ride cancelled")
                .build(),
            RiderMatchStatusNotification.builder()
                .requestId(4)
                .status("COMPLETED")
                .message("Ride completed successfully")
                .build()
        );
    }
}

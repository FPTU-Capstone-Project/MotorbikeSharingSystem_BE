package com.mssus.app.service.domain.matching;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.dto.domain.notification.RiderMatchStatusNotification;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.entity.User;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.appconfig.config.properties.RideConfigurationProperties;
import com.mssus.app.appconfig.config.properties.RideMessagingProperties;
import com.mssus.app.messaging.RideMatchingCommandPublisher;
import com.mssus.app.messaging.RideNotificationEventPublisher;
import com.mssus.app.messaging.dto.MatchingCommandMessage;
import com.mssus.app.messaging.dto.RideRequestCreatedMessage;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.service.RideMatchingService;
import com.mssus.app.service.domain.matching.session.MatchingSessionPhase;
import com.mssus.app.service.domain.matching.session.MatchingSessionRepository;
import com.mssus.app.service.domain.matching.session.MatchingSessionState;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueRideMatchingOrchestratorTest {

    @Mock
    private SharedRideRequestRepository requestRepository;
    
    @Mock
    private DriverProfileRepository driverRepository;
    
    @Mock
    private RideMatchingService rideMatchingService;
    
    @Mock
    private MatchingResponseAssembler responseAssembler;
    
    @Mock
    private RealTimeNotificationService notificationService;
    
    @Mock
    private DriverDecisionGateway decisionGateway;
    
    @Mock
    private MatchingSessionRepository sessionRepository;
    
    @Mock
    private RideMatchingCommandPublisher commandPublisher;
    
    @Mock
    private ObjectProvider<RideNotificationEventPublisher> notificationPublisherProvider;
    
    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    private RideMessagingProperties messagingProperties;
    private RideConfigurationProperties rideConfig;
    private QueueRideMatchingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        messagingProperties = new RideMessagingProperties();
        messagingProperties.setMatchingRequestTimeout(Duration.ofMinutes(5));
        messagingProperties.setDriverResponseWindow(Duration.ofSeconds(90));
        
        rideConfig = new RideConfigurationProperties();
        RideConfigurationProperties.Broadcast broadcast = new RideConfigurationProperties.Broadcast();
        broadcast.setResponseWindowSeconds(120);
        rideConfig.setBroadcast(broadcast);
        
        orchestrator = new QueueRideMatchingOrchestrator(
            requestRepository,
            driverRepository,
            rideMatchingService,
            responseAssembler,
            notificationService,
            decisionGateway,
            sessionRepository,
            commandPublisher,
            messagingProperties,
            rideConfig,
            notificationPublisherProvider,
            meterRegistryProvider
        );
    }

    @Test
    void testOnRideRequestCreated_BookingWithCandidates_ShouldInitiateMatching() {
        // Arrange
        Integer requestId = 1;
        RideRequestCreatedMessage message = RideRequestCreatedMessage.builder()
            .requestId(requestId)
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);
        RideMatchProposalResponse proposal = createMockProposal();
        
        when(sessionRepository.find(requestId)).thenReturn(Optional.empty());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rideMatchingService.findMatches(request)).thenReturn(List.of(proposal));

        // Act
        orchestrator.onRideRequestCreated(message);

        // Assert
        verify(sessionRepository).save(any(MatchingSessionState.class), any(Duration.class));
        verify(commandPublisher).publish(argThat(cmd -> 
            cmd.getRequestId().equals(requestId) && 
            cmd.getCandidateIndex() == 0));
    }

    @Test
    void testOnRideRequestCreated_BookingWithoutCandidates_ShouldNotifyRider() {
        // Arrange
        Integer requestId = 1;
        RideRequestCreatedMessage message = RideRequestCreatedMessage.builder()
            .requestId(requestId)
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);
        
        when(sessionRepository.find(requestId)).thenReturn(Optional.empty());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rideMatchingService.findMatches(request)).thenReturn(List.of());

        // Act
        orchestrator.onRideRequestCreated(message);

        // Assert
        verify(sessionRepository).save(any(MatchingSessionState.class), any(Duration.class));
        verify(commandPublisher, never()).publish(any());
        verify(responseAssembler).toRiderNoMatch(request);
    }

    @Test
    void testOnRideRequestCreated_JoinRequest_ShouldSendToSpecificDriver() {
        // Arrange
        Integer requestId = 1;
        Integer driverId = 10;
        Integer rideId = 100;
        
        RideRequestCreatedMessage message = RideRequestCreatedMessage.builder()
            .requestId(requestId)
            .build();

        SharedRide ride = new SharedRide();
        ride.setSharedRideId(rideId);
        DriverProfile driver = createMockDriver(driverId);
        ride.setDriver(driver);

        SharedRideRequest request = createMockRequest(requestId, RequestKind.JOIN_RIDE);
        request.setSharedRide(ride);
        
        when(sessionRepository.find(requestId)).thenReturn(Optional.empty());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        // Act
        orchestrator.onRideRequestCreated(message);

        // Assert
        verify(sessionRepository, atLeastOnce()).save(any(MatchingSessionState.class), any(Duration.class));
        verify(decisionGateway).registerOffer(eq(requestId), eq(rideId), eq(driverId), any(), any());
        verify(responseAssembler).toDriverJoinRequest(eq(request), eq(driver), any(), any(), any());
    }

    @Test
    void testOnRideRequestCreated_WithStaleSession_ShouldPurgeAndRestart() {
        // Arrange
        Integer requestId = 99;
        RideRequestCreatedMessage message = RideRequestCreatedMessage.builder()
            .requestId(requestId)
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);
        request.setCreatedAt(LocalDateTime.now());

        MatchingSessionState staleSession = MatchingSessionState.builder()
            .requestId(requestId)
            .requestKind(RequestKind.BOOKING)
            .requestCreatedAt(Instant.now().minus(Duration.ofMinutes(10)))
            .phase(MatchingSessionPhase.MATCHING)
            .proposals(List.of())
            .nextProposalIndex(0)
            .requestDeadline(Instant.now().minusSeconds(30))
            .build();

        RideMatchProposalResponse proposal = createMockProposal();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(sessionRepository.find(requestId)).thenReturn(Optional.of(staleSession));
        when(rideMatchingService.findMatches(request)).thenReturn(List.of(proposal));

        // Act
        orchestrator.onRideRequestCreated(message);

        // Assert
        verify(sessionRepository).delete(requestId);
        verify(rideMatchingService).findMatches(request);
        verify(sessionRepository, atLeastOnce()).save(any(MatchingSessionState.class), any(Duration.class));
        verify(commandPublisher).publish(any(MatchingCommandMessage.class));
    }

    @Test
    void testOnRideRequestCreated_LegacySessionWithoutCreatedAt_ShouldStillPurge() {
        Integer requestId = 100;
        RideRequestCreatedMessage message = RideRequestCreatedMessage.builder()
            .requestId(requestId)
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);

        MatchingSessionState legacySession = MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.MATCHING)
            .lastProcessedAt(Instant.now().minus(Duration.ofMinutes(20)))
            .proposals(List.of())
            .nextProposalIndex(0)
            .build();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(sessionRepository.find(requestId)).thenReturn(Optional.of(legacySession));
        when(rideMatchingService.findMatches(request)).thenReturn(List.of(createMockProposal()));

        orchestrator.onRideRequestCreated(message);

        verify(sessionRepository).delete(requestId);
        verify(rideMatchingService).findMatches(request);
    }

    @Test
    void testBroadcastTimeout_ShouldMarkRequestExpired() {
        Integer requestId = 55;
        MatchingCommandMessage command = MatchingCommandMessage.broadcastTimeout(requestId);

        MatchingSessionState session = MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.BROADCASTING)
            .requestDeadline(Instant.now().minusSeconds(10))
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);

        when(sessionRepository.find(requestId)).thenReturn(Optional.of(session));
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(responseAssembler.toRiderNoMatch(request)).thenReturn(mock(RiderMatchStatusNotification.class));

        orchestrator.onMatchingCommand(command);

        verify(requestRepository).save(argThat(saved -> 
            saved.getStatus() == SharedRideRequestStatus.EXPIRED));
    }

    @Test
    void testOnMatchingCommand_DuplicateMessage_ShouldSkip() {
        // Arrange
        Integer requestId = 1;
        String correlationId = "test-correlation-id";
        
        MatchingCommandMessage command = MatchingCommandMessage.sendNext(requestId, 0);
        MatchingSessionState session = MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.MATCHING)
            .lastProcessedMessageId(correlationId) // Already processed
            .build();

        when(sessionRepository.find(requestId)).thenReturn(Optional.of(session));

        // Act
        orchestrator.onMatchingCommand(command);

        // Assert
        verify(requestRepository, never()).findById(any());
        verify(commandPublisher, never()).publish(any());
    }

    @Test
    void testHandleDriverTimeout_ValidTimeout_ShouldSendNextOffer() {
        // Arrange
        Integer requestId = 1;
        Integer driverId = 10;
        Integer rideId = 100;
        
        MatchingCommandMessage command = MatchingCommandMessage.driverTimeout(requestId, driverId, rideId);
        
        RideMatchProposalResponse proposal = createMockProposal();
        MatchingSessionState session = MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.AWAITING_CONFIRMATION)
            .proposals(List.of(proposal))
            .nextProposalIndex(0)
            .activeOffer(com.mssus.app.service.domain.matching.session.ActiveOfferState.builder()
                .driverId(driverId)
                .rideId(rideId)
                .expiresAt(Instant.now().plusSeconds(90))
                .build())
            .build();

        when(sessionRepository.find(requestId)).thenReturn(Optional.of(session));

        // Act
        orchestrator.onMatchingCommand(command);

        // Assert
        ArgumentCaptor<MatchingSessionState> sessionCaptor = ArgumentCaptor.forClass(MatchingSessionState.class);
        verify(sessionRepository, atLeastOnce()).save(sessionCaptor.capture(), any(Duration.class));
        
        MatchingSessionState savedSession = sessionCaptor.getValue();
        assertEquals(MatchingSessionPhase.MATCHING, savedSession.getPhase());
        assertNull(savedSession.getActiveOffer());
    }

    @Test
    void testHandleDriverResponse_ValidResponse_ShouldMarkCompleted() {
        // Arrange
        Integer requestId = 1;
        Integer driverId = 10;
        Integer rideId = 100;
        
        MatchingCommandMessage command = MatchingCommandMessage.driverResponse(
            requestId, driverId, rideId, false, java.util.Map.of());
        
        RideMatchProposalResponse proposal = RideMatchProposalResponse.builder()
            .sharedRideId(rideId)
            .driverId(driverId)
            .build();
            
        MatchingSessionState session = MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.AWAITING_CONFIRMATION)
            .proposals(List.of(proposal))
            .activeOffer(com.mssus.app.service.domain.matching.session.ActiveOfferState.builder()
                .driverId(driverId)
                .rideId(rideId)
                .expiresAt(Instant.now().plusSeconds(90))
                .build())
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);
        
        when(sessionRepository.find(requestId)).thenReturn(Optional.of(session));
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        // Act
        orchestrator.onMatchingCommand(command);

        // Assert
        ArgumentCaptor<MatchingSessionState> sessionCaptor = ArgumentCaptor.forClass(MatchingSessionState.class);
        verify(sessionRepository, atLeastOnce()).save(sessionCaptor.capture(), any(Duration.class));
        
        MatchingSessionState savedSession = sessionCaptor.getValue();
        assertEquals(MatchingSessionPhase.COMPLETED, savedSession.getPhase());
    }

    @Test
    void testSendNextWithoutRemainingCandidates_ShouldEnterBroadcastEvenWhenNoDrivers() {
        // Arrange
        Integer requestId = 42;
        MatchingCommandMessage command = MatchingCommandMessage.sendNext(requestId, 1);

        MatchingSessionState session = MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.MATCHING)
            .proposals(List.of())
            .nextProposalIndex(0)
            .requestDeadline(Instant.now().plusSeconds(300))
            .build();

        SharedRideRequest request = createMockRequest(requestId, RequestKind.BOOKING);

        when(sessionRepository.find(requestId)).thenReturn(Optional.of(session));
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(driverRepository.findBroadcastEligibleDrivers(anyList())).thenReturn(List.of());

        // Act
        orchestrator.onMatchingCommand(command);

        // Assert
        verify(requestRepository).save(argThat(saved -> 
            saved.getStatus() == SharedRideRequestStatus.BROADCASTING));
        verify(commandPublisher).publishBroadcastTimeout(any(MatchingCommandMessage.class), any(Duration.class));
        assertEquals(MatchingSessionPhase.BROADCASTING, session.getPhase());
    }

    // Helper methods
    private SharedRideRequest createMockRequest(Integer requestId, RequestKind kind) {
        SharedRideRequest request = new SharedRideRequest();
        request.setSharedRideRequestId(requestId);
        request.setRequestKind(kind);
        request.setStatus(SharedRideRequestStatus.PENDING);
        
        RiderProfile rider = new RiderProfile();
        User user = new User();
        user.setUserId(1);
        rider.setUser(user);
        request.setRider(rider);
        
        Location pickup = new Location();
        pickup.setLat(10.762622);
        pickup.setLng(106.660172);
        request.setPickupLocation(pickup);
        
        Location dropoff = new Location();
        dropoff.setLat(10.772622);
        dropoff.setLng(106.670172);
        request.setDropoffLocation(dropoff);

        request.setCreatedAt(LocalDateTime.now());
        
        return request;
    }

    private RideMatchProposalResponse createMockProposal() {
        return RideMatchProposalResponse.builder()
            .sharedRideId(100)
            .driverId(10)
            .driverName("Test Driver")
            .build();
    }

    private DriverProfile createMockDriver(Integer driverId) {
        DriverProfile driver = new DriverProfile();
        driver.setDriverId(driverId);
        User user = new User();
        user.setUserId(driverId);
        user.setFullName("Test Driver");
        driver.setUser(user);
        return driver;
    }
}


package com.mssus.app.service.domain.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.VehicleStatus;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Vehicle;
import com.mssus.app.entity.Verification;
import com.mssus.app.dto.response.VehicleInfo;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RiderProfileRepository;
import com.mssus.app.repository.VehicleRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationOutcomeHandler {

    private final RiderProfileRepository riderProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final VerificationRepository verificationRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public void handleApproval(Verification verification) {
        User user = verification.getUser();
        VerificationType type = verification.getType();

        if (type == VerificationType.STUDENT_ID && user.getRiderProfile() != null) {
            activateRiderProfile(user.getRiderProfile(), user);
        } else if (isDriverVerification(type) && user.getDriverProfile() != null) {
            if (type == VerificationType.VEHICLE_REGISTRATION) {
                createVehicleFromVerification(verification);
            }
            checkAndActivateDriverProfile(user);
        }
    }

    public void handleRejection(Verification verification) {
        // Placeholder for future rejection side-effects.
    }

    private void activateRiderProfile(RiderProfile rider, User user) {
        if (rider.getStatus() != RiderProfileStatus.ACTIVE) {
            rider.setStatus(RiderProfileStatus.ACTIVE);
            rider.setActivatedAt(LocalDateTime.now());
            riderProfileRepository.save(rider);
            log.info("Rider profile activated for user: {}", user.getUserId());
            try {
                emailService.notifyUserActivated(user);
            } catch (Exception ex) {
                log.warn("Failed to send rider activation email for user {}: {}", user.getUserId(), ex.getMessage());
            }
        }
    }

    private void checkAndActivateDriverProfile(User user) {
        DriverProfile driver = user.getDriverProfile();

        if (driver == null) {
            return;
        }

        List<VerificationType> requiredTypes = Arrays.asList(
                VerificationType.DRIVER_LICENSE,
                VerificationType.DRIVER_DOCUMENTS,
                VerificationType.VEHICLE_REGISTRATION
        );

        List<Verification> verifications = verificationRepository.findByListUserId(user.getUserId());

        boolean allApproved = verifications.stream()
                .filter(v -> requiredTypes.contains(v.getType()))
                .allMatch(v -> VerificationStatus.APPROVED.equals(v.getStatus()));

        if (allApproved && driver.getStatus() != DriverProfileStatus.ACTIVE) {
            driver.setStatus(DriverProfileStatus.ACTIVE);
            driver.setActivatedAt(LocalDateTime.now());
            driverProfileRepository.save(driver);
            log.info("Driver profile activated for user: {}", user.getUserId());
            try {
                emailService.notifyUserActivated(user);
            } catch (Exception ex) {
                log.warn("Failed to send driver activation email for user {}: {}", user.getUserId(), ex.getMessage());
            }
        }
    }

    private void createVehicleFromVerification(Verification verification) {
        User user = verification.getUser();
        DriverProfile driver = user.getDriverProfile();

        if (driver == null) {
            log.error("Driver profile not found for user {}", user.getUserId());
            return;
        }

        if (vehicleRepository.findByDriver_DriverId(driver.getDriverId()).isPresent()) {
            log.info("Vehicle already exists for driver {}", driver.getDriverId());
            return;
        }

        VehicleInfo vehicleInfo = parseVehicleInfoFromMetadata(verification.getMetadata());

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .plateNumber(vehicleInfo.getPlateNumber())
                .model(vehicleInfo.getModel())
                .color(vehicleInfo.getColor())
                .year(vehicleInfo.getYear())
                .capacity(vehicleInfo.getCapacity())
                .fuelType(vehicleInfo.getFuelType())
                .insuranceExpiry(vehicleInfo.getInsuranceExpiry())
                .status(VehicleStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now())
                .build();

        vehicleRepository.save(vehicle);

        log.info("Vehicle {} created and verified for driver {}", vehicle.getPlateNumber(), driver.getDriverId());
    }

    private VehicleInfo parseVehicleInfoFromMetadata(String metadata) {
        if (metadata == null) {
            throw new ValidationException("Vehicle metadata is required for vehicle registration approval");
        }

        try {
            return objectMapper.readValue(metadata, VehicleInfo.class);
        } catch (Exception e) {
            log.error("Failed to parse vehicle info from metadata: {}", e.getMessage());
            throw new ValidationException("Invalid vehicle information in verification metadata");
        }
    }

    private boolean isDriverVerification(VerificationType type) {
        return type == VerificationType.DRIVER_DOCUMENTS ||
                type == VerificationType.DRIVER_LICENSE ||
                type == VerificationType.VEHICLE_REGISTRATION ||
                type == VerificationType.BACKGROUND_CHECK;
    }
}



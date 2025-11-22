package com.mssus.app.service;

import com.mssus.app.common.enums.PricingConfigStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.pricing.CreatePricingConfigRequest;
import com.mssus.app.dto.request.pricing.FareTierConfigRequest;
import com.mssus.app.dto.request.pricing.ReplaceFareTiersRequest;
import com.mssus.app.dto.request.pricing.UpdatePricingConfigRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.pricing.FareTierAdminResponse;
import com.mssus.app.dto.response.pricing.PricingConfigResponse;
import com.mssus.app.entity.FareTier;
import com.mssus.app.entity.PricingConfig;
import com.mssus.app.entity.User;
import com.mssus.app.repository.FareTierRepository;
import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PricingConfigAdminService {

    private static final ZoneId HANOI_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime GO_LIVE_TIME = LocalTime.of(3, 0);
    private static final int MAX_SERVICE_DISTANCE_KM = 25;

    private final PricingConfigRepository pricingConfigRepository;
    private final FareTierRepository fareTierRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<PricingConfigResponse> list(PricingConfigStatus status, Pageable pageable) {
        Page<PricingConfig> page = status != null
            ? pricingConfigRepository.findByStatus(status, pageable)
            : pricingConfigRepository.findAll(pageable);

        List<PricingConfigResponse> data = page.getContent().stream()
            .map(this::toResponseWithTiers)
            .toList();

        return PageResponse.<PricingConfigResponse>builder()
            .data(data)
            .pagination(PageResponse.PaginationInfo.builder()
                .page(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalRecords(page.getTotalElements())
                .build())
            .build();
    }

    @Transactional(readOnly = true)
    public PricingConfigResponse get(Integer id) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        return toResponseWithTiers(config);
    }

    public PricingConfigResponse createDraft(CreatePricingConfigRequest request, Authentication authentication) {
        User actor = resolveUser(authentication);
        validateCommission(request.systemCommissionRate());
        validateTiers(request.fareTiers());

        PricingConfig config = new PricingConfig();
        config.setVersion(Instant.now());
        config.setSystemCommissionRate(request.systemCommissionRate());
        config.setStatus(PricingConfigStatus.DRAFT);
        config.setValidFrom(null);
        config.setValidUntil(null);
        config.setCreatedBy(actor);
        config.setUpdatedBy(actor);
        config.setChangeReason(request.changeReason());

        PricingConfig saved = pricingConfigRepository.save(config);
        persistFareTiers(saved, request.fareTiers());

        return toResponseWithTiers(saved);
    }

    public PricingConfigResponse updateMetadata(Integer id, UpdatePricingConfigRequest request, Authentication authentication) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        ensureEditable(config);

        if (request.systemCommissionRate() != null) {
            validateCommission(request.systemCommissionRate());
            config.setSystemCommissionRate(request.systemCommissionRate());
        }
        if (request.changeReason() != null) {
            config.setChangeReason(request.changeReason());
        }

        User actor = resolveUser(authentication);
        config.setUpdatedBy(actor);

        PricingConfig saved = pricingConfigRepository.save(config);
        return toResponseWithTiers(saved);
    }

    public PricingConfigResponse replaceTiers(Integer id, ReplaceFareTiersRequest request, Authentication authentication) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        ensureEditable(config);
        validateTiers(request.fareTiers());

        persistFareTiers(config, request.fareTiers());

        User actor = resolveUser(authentication);
        config.setUpdatedBy(actor);
        pricingConfigRepository.save(config);

        return toResponseWithTiers(config);
    }

    public PricingConfigResponse addTier(Integer id, FareTierConfigRequest request, Authentication authentication) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        ensureEditable(config);

        List<FareTier> current = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId());
        List<FareTierConfigRequest> updated = current.stream()
            .map(t -> new FareTierConfigRequest(t.getTierLevel(), t.getMinKm(), t.getMaxKm(), t.getAmount(), t.getDescription()))
            .toList();

        if (updated.stream().anyMatch(t -> t.tierLevel().equals(request.tierLevel()))) {
            throw BaseDomainException.validation("Tier level already exists. Choose a new level or update the existing tier.");
        }

        updated = new java.util.ArrayList<>(updated);
        updated.add(request);

        validateTiers(updated);
        persistFareTiers(config, updated);

        config.setUpdatedBy(resolveUser(authentication));
        pricingConfigRepository.save(config);
        return toResponseWithTiers(config);
    }

    public PricingConfigResponse updateTier(Integer id, Integer tierId, FareTierConfigRequest request, Authentication authentication) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        ensureEditable(config);

        List<FareTier> current = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId());
        FareTier target = current.stream()
            .filter(t -> t.getFareTierId().equals(tierId))
            .findFirst()
            .orElseThrow(() -> BaseDomainException.validation("Fare tier not found for id " + tierId));

        List<FareTierConfigRequest> updated = new java.util.ArrayList<>();
        for (FareTier t : current) {
            if (t.getFareTierId().equals(tierId)) {
                continue; // replaced with request
            }
            updated.add(new FareTierConfigRequest(t.getTierLevel(), t.getMinKm(), t.getMaxKm(), t.getAmount(), t.getDescription()));
        }
        updated.add(request);

        validateTiers(updated);
        persistFareTiers(config, updated);

        config.setUpdatedBy(resolveUser(authentication));
        pricingConfigRepository.save(config);
        return toResponseWithTiers(config);
    }

    public PricingConfigResponse deleteTier(Integer id, Integer tierId, Authentication authentication) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        ensureEditable(config);

        List<FareTier> current = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId());
        FareTier target = current.stream()
            .filter(t -> t.getFareTierId().equals(tierId))
            .findFirst()
            .orElseThrow(() -> BaseDomainException.validation("Fare tier not found for id " + tierId));

        List<FareTierConfigRequest> updated = new java.util.ArrayList<>();
        for (FareTier t : current) {
            if (t.getFareTierId().equals(tierId)) {
                continue;
            }
            updated.add(new FareTierConfigRequest(t.getTierLevel(), t.getMinKm(), t.getMaxKm(), t.getAmount(), t.getDescription()));
        }

        validateTiers(updated);
        persistFareTiers(config, updated);

        config.setUpdatedBy(resolveUser(authentication));
        pricingConfigRepository.save(config);
        return toResponseWithTiers(config);
    }

    public PricingConfigResponse schedule(Integer id, Authentication authentication) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));
        ensureEditable(config);

        List<FareTier> tiers = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId());
        if (tiers.isEmpty()) {
            throw BaseDomainException.validation("Cannot schedule a pricing config without fare tiers");
        }
        validateTiers(tiers.stream()
            .map(t -> new FareTierConfigRequest(t.getTierLevel(), t.getMinKm(), t.getMaxKm(), t.getAmount(), t.getDescription()))
            .toList());

        Optional<PricingConfig> existingScheduled = pricingConfigRepository.findScheduled();
        if (existingScheduled.isPresent() && !existingScheduled.get().getPricingConfigId().equals(config.getPricingConfigId())) {
            throw BaseDomainException.validation("Another pricing config is already scheduled. Cancel it before scheduling a new one.");
        }

        Instant computed = computeGoLive(Instant.now());
        Instant goLive = (config.getValidFrom() != null && config.getValidFrom().isAfter(computed))
            ? config.getValidFrom()
            : computed;
        final Instant effectiveGoLive = goLive;
        config.setValidFrom(effectiveGoLive);
        config.setValidUntil(null);
        config.setStatus(PricingConfigStatus.SCHEDULED);
        config.setVersion(effectiveGoLive);
        config.setUpdatedBy(resolveUser(authentication));
        config.setNoticeSentAt(null);

        pricingConfigRepository.findActive(Instant.now())
            .filter(active -> !active.getPricingConfigId().equals(config.getPricingConfigId()))
            .ifPresent(active -> {
                active.setValidUntil(effectiveGoLive);
                pricingConfigRepository.save(active);
            });

        PricingConfig saved = pricingConfigRepository.save(config);
        sendUserNotification(saved, tiers);
        return toResponseWithTiers(saved);
    }

    public PricingConfigResponse archive(Integer id) {
        PricingConfig config = pricingConfigRepository.findById(id)
            .orElseThrow(() -> BaseDomainException.validation("Pricing config not found for id " + id));

        if (config.getStatus() == PricingConfigStatus.ACTIVE) {
            throw BaseDomainException.validation("Cannot archive an active pricing config while it is in use");
        }
        config.setStatus(PricingConfigStatus.ARCHIVED);
        PricingConfig saved = pricingConfigRepository.save(config);
        return toResponseWithTiers(saved);
    }

    private void ensureEditable(PricingConfig config) {
        if (config.getStatus() == PricingConfigStatus.ACTIVE) {
            throw BaseDomainException.validation("Active pricing config cannot be edited. Create a new version instead.");
        }
        if (config.getStatus() == PricingConfigStatus.ARCHIVED) {
            throw BaseDomainException.validation("Archived pricing config cannot be edited");
        }
    }

    private void validateCommission(BigDecimal value) {
        if (value == null) {
            throw BaseDomainException.validation("systemCommissionRate is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw BaseDomainException.validation("systemCommissionRate must be between 0 and 1");
        }
    }

    private void validateTiers(List<FareTierConfigRequest> requestedTiers) {
        if (requestedTiers == null || requestedTiers.isEmpty()) {
            throw BaseDomainException.validation("At least one fare tier is required");
        }

        List<FareTierConfigRequest> tiers = requestedTiers.stream()
            .sorted(Comparator.comparingInt(FareTierConfigRequest::tierLevel))
            .toList();

        if (!tiers.get(0).minKm().equals(0)) {
            throw BaseDomainException.validation("The first tier must start at 0 km");
        }

        BigDecimal previousAmount = null;
        int expectedLevel = 1;
        Integer previousMax = null;

        for (FareTierConfigRequest tier : tiers) {
            if (tier.tierLevel() != expectedLevel) {
                throw BaseDomainException.validation("Tier levels must be sequential starting from 1");
            }
            if (tier.maxKm() > MAX_SERVICE_DISTANCE_KM) {
                throw BaseDomainException.validation("Tier maxKm cannot exceed " + MAX_SERVICE_DISTANCE_KM + " km");
            }
            if (tier.minKm() >= tier.maxKm()) {
                throw BaseDomainException.validation("Tier maxKm must be greater than minKm");
            }
            if (previousMax != null && !previousMax.equals(tier.minKm())) {
                throw BaseDomainException.validation("Fare tiers must be contiguous with no gaps or overlaps");
            }
            if (previousAmount != null && tier.amount().setScale(0, RoundingMode.HALF_UP)
                .compareTo(previousAmount.setScale(0, RoundingMode.HALF_UP)) < 0) {
                throw BaseDomainException.validation("Each subsequent tier amount must be greater than or equal to the previous tier");
            }

            previousAmount = tier.amount();
            previousMax = tier.maxKm();
            expectedLevel++;
        }

        if (!previousMax.equals(MAX_SERVICE_DISTANCE_KM)) {
            throw BaseDomainException.validation("Fare tiers must cover distances up to " + MAX_SERVICE_DISTANCE_KM + " km");
        }
    }

    private void persistFareTiers(PricingConfig config, List<FareTierConfigRequest> tierRequests) {
        List<FareTier> existing = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId());
        if (!existing.isEmpty()) {
            fareTierRepository.deleteAll(existing);
        }

        List<FareTier> tiers = tierRequests.stream()
            .sorted(Comparator.comparingInt(FareTierConfigRequest::tierLevel))
            .map(req -> {
                FareTier tier = new FareTier();
                tier.setPricingConfig(config);
                tier.setTierLevel(req.tierLevel());
                tier.setMinKm(req.minKm());
                tier.setMaxKm(req.maxKm());
                tier.setAmount(req.amount());
                tier.setDescription(req.description());
                tier.setIsActive(true);
                tier.setCreatedAt(LocalDateTime.now());
                tier.setUpdatedAt(LocalDateTime.now());
                return tier;
            })
            .toList();

        fareTierRepository.saveAll(tiers);
        config.setFareTiers(tiers);
    }

    private PricingConfigResponse toResponseWithTiers(PricingConfig config) {
        List<FareTierAdminResponse> tiers = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId())
            .stream()
            .sorted(Comparator.comparingInt(FareTier::getTierLevel))
            .map(t -> FareTierAdminResponse.builder()
                .fareTierId(t.getFareTierId())
                .tierLevel(t.getTierLevel())
                .minKm(t.getMinKm())
                .maxKm(t.getMaxKm())
                .amount(t.getAmount())
                .description(t.getDescription())
                .isActive(t.getIsActive())
                .build())
            .toList();

        return PricingConfigResponse.builder()
            .pricingConfigId(config.getPricingConfigId())
            .version(config.getVersion())
            .systemCommissionRate(config.getSystemCommissionRate())
            .validFrom(config.getValidFrom())
            .validUntil(config.getValidUntil())
            .status(config.getStatus())
            .changeReason(config.getChangeReason())
            .noticeSentAt(config.getNoticeSentAt())
            .fareTiers(tiers)
            .build();
    }

    private Instant computeGoLive(Instant nowUtc) {
        ZonedDateTime threshold = ZonedDateTime.ofInstant(nowUtc, HANOI_ZONE).plusHours(24);
        ZonedDateTime candidate = threshold.withHour(GO_LIVE_TIME.getHour())
            .withMinute(GO_LIVE_TIME.getMinute())
            .withSecond(0)
            .withNano(0);

        if (!candidate.isAfter(threshold)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private void sendUserNotification(PricingConfig scheduledConfig, List<FareTier> tiers) {
        if (scheduledConfig.getNoticeSentAt() != null) {
            return; // already notified
        }

        if (scheduledConfig.getValidFrom() == null) {
            log.warn("Skipping fare change notification because validFrom is null for config {}", scheduledConfig.getPricingConfigId());
            return;
        }

        ZonedDateTime goLiveLocal = ZonedDateTime.ofInstant(scheduledConfig.getValidFrom(), HANOI_ZONE);
        String title = "Fare update scheduled";
        String message = String.format(
            "New fares take effect at %s. Base fare stays at %s VND. See app for full tiers.",
            goLiveLocal.toLocalDate() + " " + GO_LIVE_TIME,
            tiers.stream()
                .sorted(Comparator.comparingInt(FareTier::getTierLevel))
                .findFirst()
                .map(t -> t.getAmount().setScale(0, RoundingMode.HALF_UP).toPlainString())
                .orElse("0"));

        List<User> users = userRepository.findAll();
        users.stream()
            .filter(u -> u.getStatus() == null || UserStatus.ACTIVE.equals(u.getStatus()))
            .forEach(user -> notificationService.sendNotification(
                user,
                com.mssus.app.common.enums.NotificationType.SYSTEM,
                title,
                message,
                null,
                com.mssus.app.common.enums.Priority.MEDIUM,
                com.mssus.app.common.enums.DeliveryMethod.PUSH,
                null
            ));

        scheduledConfig.setNoticeSentAt(Instant.now());
        pricingConfigRepository.save(scheduledConfig);
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw BaseDomainException.unauthorized("Unauthenticated request");
        }
        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email"));
    }
}

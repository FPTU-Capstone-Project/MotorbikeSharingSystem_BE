package com.mssus.app.service.impl;

import com.mssus.app.common.enums.RatingType;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.rating.RatingResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Rating;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.entity.User;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RatingRepository;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.RatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final SharedRideRequestRepository sharedRideRequestRepository;
    private final DriverProfileRepository driverProfileRepository;

    @Override
    @Transactional
    public void rateDriver(Authentication authentication, int sharedRideRequestId, int rating, String comments) {
        User user = resolveUser(authentication);

        RiderProfile riderProfile = Optional.ofNullable(user.getRiderProfile())
            .orElseThrow(() -> BaseDomainException.unauthorized("User does not have rider profile"));

        if (rating < 1 || rating > 5) {
            throw BaseDomainException.validation("Rating must be between 1 and 5");
        }

        SharedRideRequest sharedRideRequest = sharedRideRequestRepository
            .findBySharedRideRequestIdAndRiderRiderIdAndStatus(sharedRideRequestId, riderProfile.getRiderId(), SharedRideRequestStatus.COMPLETED)
            .orElseThrow(() -> BaseDomainException.validation("Completed ride request not found for rating"));

        if (ratingRepository.existsBySharedRideRequestSharedRideRequestIdAndRaterRiderId(sharedRideRequestId, riderProfile.getRiderId())) {
            throw BaseDomainException.validation("Rating already submitted for this request");
        }

        SharedRide sharedRide = Optional.ofNullable(sharedRideRequest.getSharedRide())
            .orElseThrow(() -> BaseDomainException.validation("Shared ride not found for the request"));

        DriverProfile driverProfile = Optional.ofNullable(sharedRide.getDriver())
            .orElseThrow(() -> BaseDomainException.validation("Driver not found for the request"));

        String normalizedComment = comments != null && !comments.trim().isEmpty() ? comments.trim() : null;

        Rating ratingEntity = Rating.builder()
            .sharedRideRequest(sharedRideRequest)
            .rater(riderProfile)
            .target(driverProfile)
            .ratingType(RatingType.RIDER_TO_DRIVER)
            .score(rating)
            .comment(normalizedComment)
            .build();

        ratingRepository.save(ratingEntity);

        Double averageScore = ratingRepository.calculateAverageScoreForDriver(driverProfile.getDriverId());
        if (averageScore != null) {
            driverProfile.setRatingAvg(averageScore.floatValue());
            driverProfileRepository.save(driverProfile);
        }

        log.info("Rider {} rated driver {} with score {} for request {}", riderProfile.getRiderId(),
            driverProfile.getDriverId(), rating, sharedRideRequestId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> getDriverRatingsHistory(Authentication authentication, Pageable pageable) {
        User user = resolveUser(authentication);

        DriverProfile driverProfile = Optional.ofNullable(user.getDriverProfile())
            .orElseThrow(() -> BaseDomainException.unauthorized("User does not have driver profile"));

        Pageable effectivePageable = pageable != null ? pageable : Pageable.unpaged();
        Page<Rating> page = ratingRepository.findByTargetDriverIdOrderByCreatedAtDesc(driverProfile.getDriverId(), effectivePageable);
        List<RatingResponse> content = page.getContent().stream()
            .map(this::toRatingResponse)
            .toList();

        return buildPageResponse(page, content);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> getRiderRatingsHistory(Authentication authentication, Pageable pageable) {
        User user = resolveUser(authentication);

        RiderProfile riderProfile = Optional.ofNullable(user.getRiderProfile())
            .orElseThrow(() -> BaseDomainException.unauthorized("User does not have rider profile"));

        Pageable effectivePageable = pageable != null ? pageable : Pageable.unpaged();
        Page<Rating> page = ratingRepository.findByRaterRiderIdOrderByCreatedAtDesc(riderProfile.getRiderId(), effectivePageable);
        List<RatingResponse> content = page.getContent().stream()
            .map(this::toRatingResponse)
            .toList();

        return buildPageResponse(page, content);
    }

    private RatingResponse toRatingResponse(Rating rating) {
        SharedRideRequest request = rating.getSharedRideRequest();
        SharedRide sharedRide = request != null ? request.getSharedRide() : null;
        DriverProfile target = rating.getTarget();
        RiderProfile rater = rating.getRater();

        return RatingResponse.builder()
            .ratingId(rating.getRatingId())
            .sharedRideRequestId(request != null ? request.getSharedRideRequestId() : null)
            .sharedRideId(sharedRide != null ? sharedRide.getSharedRideId() : null)
            .driverId(target != null ? target.getDriverId() : null)
            .driverName(target != null && target.getUser() != null ? target.getUser().getFullName() : null)
            .riderId(rater != null ? rater.getRiderId() : null)
            .riderName(rater != null && rater.getUser() != null ? rater.getUser().getFullName() : null)
            .score(rating.getScore())
            .comment(rating.getComment())
            .createdAt(rating.getCreatedAt())
            .build();
    }

    private PageResponse<RatingResponse> buildPageResponse(Page<?> page, List<RatingResponse> content) {
        return PageResponse.<RatingResponse>builder()
            .data(content)
            .pagination(PageResponse.PaginationInfo.builder()
                .page(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalRecords(page.getTotalElements())
                .build())
            .build();
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw BaseDomainException.unauthorized("Unauthenticated request");
        }
        return userRepository.findByEmailWithProfiles(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email"));
    }
}

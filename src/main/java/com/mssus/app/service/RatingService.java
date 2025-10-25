package com.mssus.app.service;

import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.rating.RatingResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface RatingService {

    void rateDriver(Authentication authentication, int sharedRideRequestId, int rating, String comments);

    PageResponse<RatingResponse> getDriverRatingsHistory(Authentication authentication, Pageable pageable);

    PageResponse<RatingResponse> getRiderRatingsHistory(Authentication authentication, Pageable pageable);
}

package com.mssus.app.service;

import org.springframework.security.core.Authentication;

public interface RatingService {
    void rateDriver(Authentication authentication, int rideId, int rating, String comments);
}

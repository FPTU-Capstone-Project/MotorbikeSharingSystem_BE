package com.mssus.app.service;

import com.mssus.app.dto.response.LocationResponse;

import java.util.List;

public interface LocationService {
    List<LocationResponse> getAppPOIs();
}

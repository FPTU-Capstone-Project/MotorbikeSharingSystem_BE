package com.mssus.app.dto.request;

import com.mssus.app.dto.LatLng;

public record QuoteRequest(LatLng pickup, LatLng dropoff) {}

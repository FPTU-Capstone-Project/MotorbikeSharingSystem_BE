package com.mssus.app.dto.domain.ride;

import java.time.ZonedDateTime;

public record LocationPoint(
    double lat,
    double lng,
    ZonedDateTime timestamp
) {}
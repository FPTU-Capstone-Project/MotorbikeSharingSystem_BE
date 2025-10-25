package com.mssus.app.dto.ride;

import java.time.ZonedDateTime;

public record LocationPoint(
    double lat,
    double lng,
    ZonedDateTime timestamp
) {}
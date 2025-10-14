package com.mssus.app.dto.ride;

import java.time.LocalDateTime;

public record LocationPoint(
    double lat,
    double lng,
    LocalDateTime timestamp
) {}
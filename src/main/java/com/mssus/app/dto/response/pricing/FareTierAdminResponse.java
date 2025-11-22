package com.mssus.app.dto.response.pricing;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class FareTierAdminResponse {
    Integer fareTierId;
    Integer tierLevel;
    Integer minKm;
    Integer maxKm;
    BigDecimal amount;
    String description;
    Boolean isActive;
}

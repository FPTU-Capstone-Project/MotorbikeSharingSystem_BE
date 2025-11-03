package com.mssus.app.dto.response.ride;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Summarised information about a route definition")
public class RouteSummaryResponse {

    @JsonProperty("route_id")
    @Schema(description = "Primary identifier of the route", example = "42")
    private Integer routeId;

    @JsonProperty("code")
    @Schema(description = "Public friendly code of the route", example = "GATE1-DORM")
    private String code;

    @JsonProperty("name")
    @Schema(description = "Human readable name", example = "Gate 1 to Dormitory")
    private String name;

    @JsonProperty("route_type")
    @Schema(description = "Route kind (TEMPLATE or CUSTOM)", example = "TEMPLATE")
    private String routeType;

    @JsonProperty("default_price")
    @Schema(description = "Baseline price associated to this route", example = "15000.00")
    private BigDecimal defaultPrice;

    @JsonProperty("polyline")
    @Schema(description = "Polyline representing the planned path", example = "abcdEfghIjklMnop")
    private String polyline;

    @JsonProperty("valid_from")
    @Schema(description = "When this route definition became effective", example = "2025-01-01T00:00:00")
    private LocalDateTime validFrom;

    @JsonProperty("valid_until")
    @Schema(description = "Optional expiry of the route definition", example = "2025-06-30T23:59:59")
    private LocalDateTime validUntil;
}

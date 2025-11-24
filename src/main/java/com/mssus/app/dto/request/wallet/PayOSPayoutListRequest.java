package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class PayOSPayoutListRequest {

    @Min(1)
    @Schema(example = "10")
    private Integer limit = 10;

    @Min(0)
    @Schema(example = "0")
    private Integer offset = 0;

    @Schema(description = "Not required",example = "PAYOUT_123456")
    private String referenceId;
    @Schema(description = "Not required",example = "COMPLETED")
    private String approvalState;
    @Schema(description = "Not required",example = "PAYOUT")
    private List<String> category;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(type = "string", format = "date-time")
    private OffsetDateTime fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(type = "string", format = "date-time")
    private OffsetDateTime toDate;

    public List<String> getCategory() {
        return category == null ? Collections.emptyList() : category;
    }
}


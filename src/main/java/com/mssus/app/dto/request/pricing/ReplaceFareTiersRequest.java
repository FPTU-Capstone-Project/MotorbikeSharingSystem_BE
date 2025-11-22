package com.mssus.app.dto.request.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReplaceFareTiersRequest(
    @NotEmpty List<@Valid FareTierConfigRequest> fareTiers
) {}

package com.mssus.app.dto.domain.sos;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcknowledgeSosRequest {

    @Size(max = 2000)
    private String note;
}

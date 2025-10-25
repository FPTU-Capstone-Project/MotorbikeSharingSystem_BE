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
public class ResolveSosRequest {

    @Size(max = 2000)
    private String resolutionNotes;

    private Boolean falseAlarm;
}

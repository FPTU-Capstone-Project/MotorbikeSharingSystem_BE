package com.mssus.app.dto.request;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@ToString
@Builder
public class PayoutOrderRequest {
    String referenceId;
    long amount;
    String description;
    String toBin;
    String toAccountNumber;
    List<String> category;

    public List<String> getCategory() {
        return category == null ? Collections.emptyList() : category;
    }
}

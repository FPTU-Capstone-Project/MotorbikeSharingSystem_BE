package com.mssus.app.dto.response.bank;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankInfo {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("code")
    private String code;

    @JsonProperty("bin")
    private String bin;

    @JsonProperty("shortName")
    private String shortName;

    @JsonProperty("logo")
    private String logo;

    @JsonProperty("transferSupported")
    private Integer transferSupported;

    @JsonProperty("lookupSupported")
    private Integer lookupSupported;

    @JsonProperty("short_name")
    private String short_name;

    @JsonProperty("support")
    private Integer support;

    @JsonProperty("isTransfer")
    private Integer isTransfer;

    @JsonProperty("swift_code")
    private String swiftCode;
}


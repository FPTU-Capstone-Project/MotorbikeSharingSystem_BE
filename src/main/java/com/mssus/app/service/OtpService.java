package com.mssus.app.service;

import com.mssus.app.common.enums.OtpFor;
import com.mssus.app.dto.request.GetOtpRequest;
import com.mssus.app.dto.request.OtpRequest;
import com.mssus.app.dto.response.OtpResponse;

public interface OtpService {
    OtpResponse requestOtp(GetOtpRequest request);

    OtpResponse verifyOtp(OtpRequest request);
}

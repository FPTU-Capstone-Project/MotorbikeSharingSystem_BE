package com.mssus.app.service;

import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.ride.RideRequestSettledResponse;
import com.mssus.app.service.pricing.model.FareBreakdown;

public interface RideFundCoordinatingService {
    void holdRideFunds(RideConfirmHoldRequest request);

    RideRequestSettledResponse settleRideFunds(RideCompleteSettlementRequest request, FareBreakdown fareBreakdown);

    void releaseRideFunds(RideHoldReleaseRequest request);
}

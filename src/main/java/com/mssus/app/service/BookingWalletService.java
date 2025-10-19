//package com.mssus.app.service;
//
//import com.mssus.app.dto.request.wallet.*;
//import com.mssus.app.dto.response.wallet.BalanceCheckResponse;
//import com.mssus.app.dto.response.wallet.WalletOperationResponse;
//
//import java.math.BigDecimal;
//
//public interface BookingWalletService {
//
//    WalletOperationResponse holdFunds(WalletHoldRequest request);
//
//    void captureFunds(RideCompleteSettlementRequest request);
//
//    WalletOperationResponse releaseFunds(WalletReleaseRequest request);
//
//    WalletOperationResponse refundToUser(WalletRefundRequest request);
//
//    BalanceCheckResponse checkBalance(Integer userId, BigDecimal amount);
//}

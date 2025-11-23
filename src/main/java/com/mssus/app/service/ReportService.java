package com.mssus.app.service;

import com.mssus.app.dto.response.wallet.CommissionReportResponse;
import com.mssus.app.dto.response.wallet.DashboardResponse;
import com.mssus.app.dto.response.wallet.TopUpTrendResponse;

import java.time.LocalDate;

public interface ReportService {

    DashboardResponse getDashboardStats(LocalDate startDate, LocalDate endDate);

    TopUpTrendResponse getTopUpTrends(LocalDate startDate,
                                      LocalDate endDate,
                                      String interval,
                                      String paymentMethod);

    CommissionReportResponse getCommissionReport(LocalDate startDate,
                                                 LocalDate endDate,
                                                 Integer driverId);
}

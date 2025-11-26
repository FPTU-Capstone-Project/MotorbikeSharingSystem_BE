package com.mssus.app.service;

import com.mssus.app.dto.response.DashboardStatsResponse;

import java.time.LocalDate;

public interface DashboardStatisticsService {

    DashboardStatsResponse getDashboardStatistics(LocalDate startDate, LocalDate endDate);
}


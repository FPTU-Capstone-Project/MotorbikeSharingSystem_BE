package com.mssus.app.mapper;

import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import com.mssus.app.entity.UserReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserReportMapper {

    @Mapping(source = "reportId", target = "reportId")
    @Mapping(source = "reportType", target = "reportType")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "reporter.userId", target = "reporterId")
    @Mapping(source = "reporter.fullName", target = "reporterName")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    UserReportSummaryResponse toSummary(UserReport report);

    @Mapping(source = "reportId", target = "reportId")
    @Mapping(source = "reportType", target = "reportType")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "reporter.userId", target = "reporterId")
    @Mapping(source = "reporter.fullName", target = "reporterName")
    @Mapping(source = "reporter.email", target = "reporterEmail")
    @Mapping(source = "resolver.userId", target = "resolverId")
    @Mapping(source = "resolver.fullName", target = "resolverName")
    @Mapping(source = "resolutionMessage", target = "resolutionMessage")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "resolvedAt", target = "resolvedAt")
    UserReportResponse toResponse(UserReport report);
}

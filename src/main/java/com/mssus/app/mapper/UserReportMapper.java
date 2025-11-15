package com.mssus.app.mapper;

import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import com.mssus.app.entity.UserReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Objects;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserReportMapper {

    @Mapping(source = "reportId", target = "reportId")
    @Mapping(source = "reportType", target = "reportType")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "priority", target = "priority")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "reporter.userId", target = "reporterId")
    @Mapping(source = "reporter.fullName", target = "reporterName")
    @Mapping(source = "sharedRide.sharedRideId", target = "sharedRideId")
    @Mapping(source = "driver.driverId", target = "driverId")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "reporterChatStartedAt", target = "reporterChatStartedAt")
    @Mapping(source = "reporterLastReplyAt", target = "reporterLastReplyAt")
    @Mapping(source = "reportedChatStartedAt", target = "reportedChatStartedAt")
    @Mapping(source = "reportedLastReplyAt", target = "reportedLastReplyAt")
    @Mapping(source = "autoClosedAt", target = "autoClosedAt")
    @Mapping(source = "autoClosedReason", target = "autoClosedReason")
    UserReportSummaryResponse toSummary(UserReport report);

    @Mapping(source = "reportId", target = "reportId")
    @Mapping(source = "reportType", target = "reportType")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "priority", target = "priority")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "reporter.userId", target = "reporterId")
    @Mapping(source = "reporter.fullName", target = "reporterName")
    @Mapping(source = "reporter.email", target = "reporterEmail")
    @Mapping(source = "resolver.userId", target = "resolverId")
    @Mapping(source = "resolver.fullName", target = "resolverName")
    @Mapping(source = "resolutionMessage", target = "resolutionMessage")
    @Mapping(source = "sharedRide.sharedRideId", target = "sharedRideId")
    @Mapping(source = "driver.driverId", target = "driverId")
    @Mapping(source = "driver.user.fullName", target = "driverName")
    @Mapping(source = "adminNotes", target = "adminNotes")
    @Mapping(source = "driverResponse", target = "driverResponse")
    @Mapping(source = "driverRespondedAt", target = "driverRespondedAt")
    @Mapping(source = "escalatedAt", target = "escalatedAt")
    @Mapping(source = "escalationReason", target = "escalationReason")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "resolvedAt", target = "resolvedAt")
    @Mapping(source = "reporterChatStartedAt", target = "reporterChatStartedAt")
    @Mapping(source = "reporterLastReplyAt", target = "reporterLastReplyAt")
    @Mapping(source = "reportedChatStartedAt", target = "reportedChatStartedAt")
    @Mapping(source = "reportedLastReplyAt", target = "reportedLastReplyAt")
    @Mapping(source = "autoClosedAt", target = "autoClosedAt")
    @Mapping(source = "autoClosedReason", target = "autoClosedReason")
    @Mapping(target = "reportedUserId", expression = "java(resolveReportedUserId(report))")
    @Mapping(target = "reportedUserName", expression = "java(resolveReportedUserName(report))")
    UserReportResponse toResponse(UserReport report);

    default Integer resolveReportedUserId(UserReport report) {
        if (report == null) {
            return null;
        }

        Integer reporterUserId = report.getReporter() != null ? report.getReporter().getUserId() : null;

        // Nếu report về driver, reportedUserId là driver.user.userId
        if (report.getDriver() != null && report.getDriver().getUser() != null) {
            Integer driverUserId = report.getDriver().getUser().getUserId();
            if (driverUserId != null && !Objects.equals(driverUserId, reporterUserId)) {
                return driverUserId;
            }
        }

        // Nếu report về rider, reportedUserId là rider.user.userId
        if (report.getSharedRide() != null
            && report.getSharedRide().getSharedRideRequest() != null
            && report.getSharedRide().getSharedRideRequest().getRider() != null
            && report.getSharedRide().getSharedRideRequest().getRider().getUser() != null) {
            Integer riderUserId = report.getSharedRide().getSharedRideRequest().getRider().getUser().getUserId();
            if (riderUserId != null && !Objects.equals(riderUserId, reporterUserId)) {
                return riderUserId;
            }
        }

        return null;
    }

    default String resolveReportedUserName(UserReport report) {
        if (report == null) {
            return null;
        }

        Integer reporterUserId = report.getReporter() != null ? report.getReporter().getUserId() : null;

        // Nếu report về driver
        if (report.getDriver() != null && report.getDriver().getUser() != null) {
            Integer driverUserId = report.getDriver().getUser().getUserId();
            if (driverUserId != null && !Objects.equals(driverUserId, reporterUserId)) {
                return report.getDriver().getUser().getFullName();
            }
        }

        // Nếu report về rider
        if (report.getSharedRide() != null
            && report.getSharedRide().getSharedRideRequest() != null
            && report.getSharedRide().getSharedRideRequest().getRider() != null
            && report.getSharedRide().getSharedRideRequest().getRider().getUser() != null) {
            Integer riderUserId = report.getSharedRide().getSharedRideRequest().getRider().getUser().getUserId();
            if (riderUserId != null && !Objects.equals(riderUserId, reporterUserId)) {
                return report.getSharedRide().getSharedRideRequest().getRider().getUser().getFullName();
            }
        }

        return null;
    }
}

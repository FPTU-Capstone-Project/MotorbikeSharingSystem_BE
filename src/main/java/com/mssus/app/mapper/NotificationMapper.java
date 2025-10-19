package com.mssus.app.mapper;

import com.mssus.app.dto.response.notification.NotificationResponse;
import com.mssus.app.dto.response.notification.NotificationSummaryResponse;
import com.mssus.app.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface NotificationMapper {

    @Mapping(source = "notifId", target = "notifId")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "message", target = "message")
    @Mapping(source = "read", target = "isRead")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "priority", target = "priority")
    NotificationSummaryResponse toSummaryResponse(Notification entity);

    @Mapping(source = "notifId", target = "notifId")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "message", target = "message")
    @Mapping(source = "read", target = "isRead")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "priority", target = "priority")
    @Mapping(source = "payload", target = "payload")
    @Mapping(source = "readAt", target = "readAt")
    @Mapping(source = "sentAt", target = "sentAt")
    @Mapping(source = "expiresAt", target = "expiresAt")
    NotificationResponse toResponse(Notification entity);
}

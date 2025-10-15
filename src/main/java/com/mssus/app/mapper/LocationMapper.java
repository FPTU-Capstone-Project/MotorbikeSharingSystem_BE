package com.mssus.app.mapper;

import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.entity.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface LocationMapper {

    @Mapping(source = "locationId", target = "locationId")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "lat", target = "lat")
    @Mapping(source = "lng", target = "lng")
    LocationResponse toResponse(Location entity);
}

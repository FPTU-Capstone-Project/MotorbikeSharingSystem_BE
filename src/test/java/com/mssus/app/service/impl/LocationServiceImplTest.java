package com.mssus.app.service.impl;

import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.entity.Location;
import com.mssus.app.mapper.LocationMapper;
import com.mssus.app.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocationServiceImpl Tests")
class LocationServiceImplTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationMapper locationMapper;

    @InjectMocks
    private LocationServiceImpl locationService;

    @BeforeEach
    void setUp() {
        // Setup is handled by @InjectMocks
    }

    // Helper methods
    private Location createTestLocation(Integer id, String name, Double lat, Double lng, String address) {
        Location location = new Location();
        location.setLocationId(id);
        location.setName(name);
        location.setLat(lat);
        location.setLng(lng);
        location.setAddress(address);
        location.setCreatedAt(LocalDateTime.now());
        return location;
    }

    private LocationResponse createTestLocationResponse(Integer locationId, String name, Double lat, Double lng, String address) {
        return new LocationResponse(locationId, name, lat, lng, address);
    }

    // Tests for getAppPOIs method
    @Test
    @DisplayName("Should return list of POIs when repository has data")
    void should_returnListOfPOIs_when_repositoryHasData() {
        // Arrange
        List<Location> locations = List.of(
                createTestLocation(1, "FPT University", 10.841480, 106.809844, "FPT University, District 9"),
                createTestLocation(2, "Student Culture House", 10.8753395, 106.8000331, "Student Culture House, District 9")
        );

        List<LocationResponse> expectedResponses = List.of(
                createTestLocationResponse(1, "FPT University", 10.841480, 106.809844, "FPT University, District 9"),
                createTestLocationResponse(2, "Student Culture House", 10.8753395, 106.8000331, "Student Culture House, District 9")
        );

        when(locationRepository.findAll()).thenReturn(locations);
        when(locationMapper.toResponse(locations.get(0))).thenReturn(expectedResponses.get(0));
        when(locationMapper.toResponse(locations.get(1))).thenReturn(expectedResponses.get(1));

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(expectedResponses);

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(locations.get(0));
        verify(locationMapper).toResponse(locations.get(1));
        verifyNoMoreInteractions(locationRepository, locationMapper);
    }

    @Test
    @DisplayName("Should return empty list when repository has no data")
    void should_returnEmptyList_when_repositoryHasNoData() {
        // Arrange
        List<Location> emptyLocations = List.of();
        when(locationRepository.findAll()).thenReturn(emptyLocations);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).isEmpty();

        verify(locationRepository).findAll();
        verifyNoMoreInteractions(locationRepository, locationMapper);
    }

    @Test
    @DisplayName("Should handle single location correctly")
    void should_handleSingleLocation_when_repositoryHasOneLocation() {
        // Arrange
        Location location = createTestLocation(1, "Test Location", 10.0, 106.0, "Test Address");
        LocationResponse expectedResponse = createTestLocationResponse(1, "Test Location", 10.0, 106.0, "Test Address");

        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenReturn(expectedResponse);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    @Test
    @DisplayName("Should handle large number of locations")
    void should_handleLargeNumberOfLocations_when_repositoryHasManyLocations() {
        // Arrange
        List<Location> locations = Stream.iterate(1, i -> i <= 100, i -> i + 1)
                .map(i -> createTestLocation(i, "Location " + i, 10.0 + i * 0.001, 106.0 + i * 0.001, "Address " + i))
                .toList();

        List<LocationResponse> expectedResponses = Stream.iterate(1, i -> i <= 100, i -> i + 1)
                .map(i -> createTestLocationResponse(i, "Location " + i, 10.0 + i * 0.001, 106.0 + i * 0.001, "Address " + i))
                .toList();

        when(locationRepository.findAll()).thenReturn(locations);
        for (int i = 0; i < locations.size(); i++) {
            when(locationMapper.toResponse(locations.get(i))).thenReturn(expectedResponses.get(i));
        }

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(100);
        assertThat(result).containsExactlyElementsOf(expectedResponses);

        verify(locationRepository).findAll();
        verify(locationMapper, times(100)).toResponse(any(Location.class));
    }

    @ParameterizedTest
    @MethodSource("locationDataProvider")
    @DisplayName("Should handle different location data correctly")
    void should_handleDifferentLocationData_when_repositoryReturnsVariousData(
            String name, Double lat, Double lng, String address) {
        // Arrange
        Location location = createTestLocation(1, name, lat, lng, address);
        LocationResponse expectedResponse = createTestLocationResponse(1, name, lat, lng, address);

        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenReturn(expectedResponse);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    @Test
    @DisplayName("Should handle null values in location data")
    void should_handleNullValues_when_locationHasNullFields() {
        // Arrange
        Location location = new Location();
        location.setLocationId(1);
        location.setName(null);
        location.setLat(null);
        location.setLng(null);
        location.setAddress(null);

        LocationResponse expectedResponse = new LocationResponse(1, null, null, null, null);

        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenReturn(expectedResponse);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    @Test
    @DisplayName("Should handle mapper throwing exception")
    void should_handleMapperException_when_mapperThrowsException() {
        // Arrange
        Location location = createTestLocation(1, "Test Location", 10.0, 106.0, "Test Address");
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenThrow(new RuntimeException("Mapping failed"));

        // Act & Assert
        assertThatThrownBy(() -> locationService.getAppPOIs())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Mapping failed");

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    @Test
    @DisplayName("Should handle repository throwing exception")
    void should_handleRepositoryException_when_repositoryThrowsException() {
        // Arrange
        when(locationRepository.findAll()).thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        assertThatThrownBy(() -> locationService.getAppPOIs())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection failed");

        verify(locationRepository).findAll();
        verifyNoMoreInteractions(locationRepository, locationMapper);
    }

    @Test
    @DisplayName("Should handle mapper returning null")
    void should_handleMapperReturningNull_when_mapperReturnsNull() {
        // Arrange
        Location location = createTestLocation(1, "Test Location", 10.0, 106.0, "Test Address");
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenReturn(null);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isNull();

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    @Test
    @DisplayName("Should handle mixed null and valid responses")
    void should_handleMixedNullAndValidResponses_when_mapperReturnsMixedResults() {
        // Arrange
        Location location1 = createTestLocation(1, "Location 1", 10.0, 106.0, "Address 1");
        Location location2 = createTestLocation(2, "Location 2", 11.0, 107.0, "Address 2");
        LocationResponse response1 = createTestLocationResponse(1, "Location 1", 10.0, 106.0, "Address 1");

        when(locationRepository.findAll()).thenReturn(List.of(location1, location2));
        when(locationMapper.toResponse(location1)).thenReturn(response1);
        when(locationMapper.toResponse(location2)).thenReturn(null);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(response1);
        assertThat(result.get(1)).isNull();

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location1);
        verify(locationMapper).toResponse(location2);
    }

    @Test
    @DisplayName("Should preserve order of locations from repository")
    void should_preserveOrder_when_repositoryReturnsLocationsInSpecificOrder() {
        // Arrange
        List<Location> locations = List.of(
                createTestLocation(3, "Location C", 10.3, 106.3, "Address C"),
                createTestLocation(1, "Location A", 10.1, 106.1, "Address A"),
                createTestLocation(2, "Location B", 10.2, 106.2, "Address B")
        );

        List<LocationResponse> expectedResponses = List.of(
                createTestLocationResponse(3, "Location C", 10.3, 106.3, "Address C"),
                createTestLocationResponse(1, "Location A", 10.1, 106.1, "Address A"),
                createTestLocationResponse(2, "Location B", 10.2, 106.2, "Address B")
        );

        when(locationRepository.findAll()).thenReturn(locations);
        for (int i = 0; i < locations.size(); i++) {
            when(locationMapper.toResponse(locations.get(i))).thenReturn(expectedResponses.get(i));
        }

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyElementsOf(expectedResponses);

        verify(locationRepository).findAll();
        verify(locationMapper, times(3)).toResponse(any(Location.class));
    }

    @Test
    @DisplayName("Should handle extreme coordinate values")
    void should_handleExtremeCoordinates_when_locationHasExtremeValues() {
        // Arrange
        Location location = createTestLocation(1, "Extreme Location", 
                Double.MAX_VALUE, Double.MIN_VALUE, "Extreme Address");
        LocationResponse expectedResponse = createTestLocationResponse(1, "Extreme Location", 
                Double.MAX_VALUE, Double.MIN_VALUE, "Extreme Address");

        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenReturn(expectedResponse);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    @Test
    @DisplayName("Should handle very long location names")
    void should_handleLongLocationNames_when_locationHasVeryLongName() {
        // Arrange
        String longName = "A".repeat(1000);
        Location location = createTestLocation(1, longName, 10.0, 106.0, "Test Address");
        LocationResponse expectedResponse = createTestLocationResponse(1, longName, 10.0, 106.0, "Test Address");

        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(locationMapper.toResponse(location)).thenReturn(expectedResponse);

        // Act
        List<LocationResponse> result = locationService.getAppPOIs();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);
        assertThat(result.get(0).name()).hasSize(1000);

        verify(locationRepository).findAll();
        verify(locationMapper).toResponse(location);
    }

    // Parameter providers
    private static Stream<Arguments> locationDataProvider() {
        return Stream.of(
                Arguments.of("FPT University", 10.841480, 106.809844, "FPT University, District 9"),
                Arguments.of("Student Culture House", 10.8753395, 106.8000331, "Student Culture House, District 9"),
                Arguments.of("Ho Chi Minh City", 10.8231, 106.6297, "Ho Chi Minh City, Vietnam"),
                Arguments.of("Hanoi", 21.0285, 105.8542, "Hanoi, Vietnam"),
                Arguments.of("Da Nang", 16.0544, 108.2022, "Da Nang, Vietnam"),
                Arguments.of("Can Tho", 10.0452, 105.7469, "Can Tho, Vietnam"),
                Arguments.of("Hue", 16.4637, 107.5909, "Hue, Vietnam"),
                Arguments.of("Nha Trang", 12.2388, 109.1967, "Nha Trang, Vietnam")
        );
    }
}

package com.mssus.app.service.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.QuoteRequest;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.entity.Location;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.service.RoutingService;
import com.mssus.app.service.pricing.PricingService;
import com.mssus.app.service.pricing.QuoteCache;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.PriceInput;
import com.mssus.app.service.pricing.model.Quote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class QuoteServiceImplTest {

    private static final double FPTU_LAT = 10.841480;
    private static final double FPTU_LNG = 106.809844;
    private static final double SCH_LAT = 10.8753395;
    private static final double SCH_LNG = 106.8000331;

    @Mock
    private QuoteCache quoteCache;

    @Mock
    private RoutingService routingService;

    @Mock
    private PricingConfigRepository cfgRepo;

    @Mock
    private PricingService pricingService;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private QuoteServiceImpl quoteService;

    @Test
    void should_generateQuote_when_requestValidWithSavedLocations() {
        QuoteRequest request = new QuoteRequest(
            createLatLng(FPTU_LAT, FPTU_LNG),
            createLatLng(SCH_LAT, SCH_LNG),
            1,
            2
        );
        int userId = 42;
        Location fptLocation = createLocation(1, "FPT University", FPTU_LAT, FPTU_LNG);
        Location schLocation = createLocation(2, "Student Culture House", SCH_LAT, SCH_LNG);

        RouteResponse route = new RouteResponse(5_000, 900, "encoded_polyline");
        FareBreakdown fare = createFareBreakdown(5_000);

        doReturn(Optional.of(fptLocation)).when(locationRepository).findByLatAndLng(FPTU_LAT, FPTU_LNG);
        doReturn(Optional.of(schLocation)).when(locationRepository).findByLatAndLng(SCH_LAT, SCH_LNG);
        doReturn(Optional.of(fptLocation)).when(locationRepository).findById(1);
        doReturn(Optional.of(schLocation)).when(locationRepository).findById(2);
        doReturn(route).when(routingService).getRoute(fptLocation.getLat(), fptLocation.getLng(), schLocation.getLat(), schLocation.getLng());
        doReturn(fare).when(pricingService).quote(any(PriceInput.class));

        Quote result = quoteService.generateQuote(request, userId);

        assertThat(result).isNotNull();
        assertThat(result.riderId()).isEqualTo(userId);
        assertThat(result.pickupLocationId()).isEqualTo(1);
        assertThat(result.dropoffLocationId()).isEqualTo(2);
        assertThat(result.distanceM()).isEqualTo(route.distance());
        assertThat(result.fare()).isEqualTo(fare);

        ArgumentCaptor<PriceInput> priceCaptor = ArgumentCaptor.forClass(PriceInput.class);
        verify(pricingService).quote(priceCaptor.capture());
        PriceInput capturedInput = priceCaptor.getValue();
        assertThat(capturedInput.distanceMeters()).isEqualTo(route.distance());
        assertThat(capturedInput.riderId()).isEqualTo(userId);
        assertThat(capturedInput.promoCode()).isNull();

        ArgumentCaptor<Quote> quoteCaptor = ArgumentCaptor.forClass(Quote.class);
        verify(quoteCache).save(quoteCaptor.capture());
        Quote savedQuote = quoteCaptor.getValue();
        assertThat(savedQuote).isEqualTo(result);

        verify(locationRepository).findByLatAndLng(FPTU_LAT, FPTU_LNG);
        verify(locationRepository).findByLatAndLng(SCH_LAT, SCH_LNG);
        verify(locationRepository).findById(1);
        verify(locationRepository).findById(2);
        verify(routingService).getRoute(fptLocation.getLat(), fptLocation.getLng(), schLocation.getLat(), schLocation.getLng());
        verifyNoMoreInteractions(pricingService, quoteCache, routingService, locationRepository);
        verifyNoInteractions(cfgRepo);
    }

    @Test
    void should_throwDomainException_when_campusLocationMissing() {
        QuoteRequest request = new QuoteRequest(
            createLatLng(FPTU_LAT, FPTU_LNG),
            createLatLng(SCH_LAT, SCH_LNG),
            null,
            null
        );
        doReturn(Optional.empty()).when(locationRepository).findByLatAndLng(FPTU_LAT, FPTU_LNG);

        assertThatThrownBy(() -> quoteService.generateQuote(request, 1))
            .isInstanceOf(BaseDomainException.class)
            .satisfies(ex -> assertThat(((BaseDomainException) ex).getErrorId())
                .isEqualTo("ride.validation.invalid-location"));

        verify(locationRepository).findByLatAndLng(FPTU_LAT, FPTU_LNG);
        verifyNoMoreInteractions(locationRepository);
        verifyNoInteractions(routingService, pricingService, quoteCache, cfgRepo);
    }

    @Test
    void should_throwDomainException_when_pickupEqualsDropoff() {
        stubCampusLocations();
        LatLng samePoint = createLatLng(10.85, 106.81);
        QuoteRequest request = new QuoteRequest(samePoint, samePoint, null, null);

        assertThatThrownBy(() -> quoteService.generateQuote(request, 7))
            .isInstanceOf(BaseDomainException.class)
            .satisfies(ex -> assertThat(((BaseDomainException) ex).getErrorId())
                .isEqualTo("ride.validation.invalid-location"));

        verifyCommonCampusLookups();
        verifyNoInteractions(routingService, pricingService, quoteCache);
        verifyNoInteractions(cfgRepo);
    }

    @ParameterizedTest
    @MethodSource("outsideServiceAreaProvider")
    void should_throwDomainException_when_pickupOrDropoffOutsideServiceArea(LatLng pickup, LatLng dropoff, String scenario) {
        stubCampusLocations();
        QuoteRequest request = new QuoteRequest(pickup, dropoff, null, null);

        assertThatThrownBy(() -> quoteService.generateQuote(request, 9))
            .as(scenario)
            .isInstanceOf(BaseDomainException.class)
            .satisfies(ex -> assertThat(((BaseDomainException) ex).getErrorId())
                .isEqualTo("ride.validation.service-area-violation"));

        verifyCommonCampusLookups();
        verifyNoInteractions(routingService, pricingService, quoteCache);
        verifyNoInteractions(cfgRepo);
    }

    @Test
    void should_returnQuote_when_getQuoteFound() {
        Quote quote = createQuote(123);
        UUID quoteId = quote.quoteId();
        doReturn(Optional.of(quote)).when(quoteCache).load(quoteId);

        Quote result = quoteService.getQuote(quoteId);

        assertThat(result).isEqualTo(quote);
        verify(quoteCache).load(quoteId);
        verifyNoMoreInteractions(quoteCache);
    }

    @Test
    void should_throwDomainException_when_getQuoteNotFound() {
        UUID quoteId = UUID.randomUUID();
        doReturn(Optional.empty()).when(quoteCache).load(quoteId);

        assertThatThrownBy(() -> quoteService.getQuote(quoteId))
            .isInstanceOf(BaseDomainException.class)
            .hasMessageContaining("Quote not found or expired");

        verify(quoteCache).load(quoteId);
        verifyNoMoreInteractions(quoteCache);
    }

    private static Stream<Arguments> outsideServiceAreaProvider() {
        LatLng nearFpt = new LatLng(FPTU_LAT, FPTU_LNG);
        LatLng farPoint = new LatLng(11.5, 107.5);
        LatLng nearSch = new LatLng(SCH_LAT, SCH_LNG);
        return Stream.of(
            Arguments.of(nearFpt, farPoint, "Dropoff far outside service area"),
            Arguments.of(farPoint, nearSch, "Pickup far outside service area")
        );
    }

    private void stubCampusLocations() {
        Location fpt = createLocation(100, "FPT University", FPTU_LAT, FPTU_LNG);
        Location sch = createLocation(200, "Student Culture House", SCH_LAT, SCH_LNG);
        doReturn(Optional.of(fpt)).when(locationRepository).findByLatAndLng(FPTU_LAT, FPTU_LNG);
        doReturn(Optional.of(sch)).when(locationRepository).findByLatAndLng(SCH_LAT, SCH_LNG);
    }

    private void verifyCommonCampusLookups() {
        verify(locationRepository).findByLatAndLng(FPTU_LAT, FPTU_LNG);
        verify(locationRepository).findByLatAndLng(SCH_LAT, SCH_LNG);
        verifyNoMoreInteractions(locationRepository);
    }

    private static Location createLocation(Integer id, String name, double lat, double lng) {
        Location location = new Location();
        location.setLocationId(id);
        location.setName(name);
        location.setLat(lat);
        location.setLng(lng);
        return location;
    }

    private static LatLng createLatLng(double lat, double lng) {
        return new LatLng(lat, lng);
    }

    private static FareBreakdown createFareBreakdown(long distanceMeters) {
        MoneyVnd base = MoneyVnd.VND(10_000);
        MoneyVnd after = MoneyVnd.VND(5_000);
        MoneyVnd discount = MoneyVnd.VND(0);
        MoneyVnd subtotal = MoneyVnd.VND(15_000);
        MoneyVnd total = MoneyVnd.VND(15_000);
        return new FareBreakdown(
            Instant.now(),
            distanceMeters,
            base,
            after,
            discount,
            subtotal,
            total,
            BigDecimal.valueOf(0.15)
        );
    }

    private static Quote createQuote(int riderId) {
        return new Quote(
            UUID.randomUUID(),
            riderId,
            null,
            null,
            10.0,
            106.0,
            10.1,
            106.1,
            10_000,
            1_200,
            "polyline",
            createFareBreakdown(10_000),
            Instant.now(),
            Instant.now().plusSeconds(60)
        );
    }
}

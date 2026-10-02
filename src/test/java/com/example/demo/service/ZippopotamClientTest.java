package com.example.demo.service;

import com.example.demo.exception.PostalLookupException;
import com.example.demo.model.PostalLocation;
import com.example.demo.model.ZippopotamResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZippopotamClientTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock
    private RestClient.RequestHeadersSpec<?> headersSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ZippopotamClient zippopotamClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        zippopotamClient = new ZippopotamClient(restClient);
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString(), any(), any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    private ZippopotamResponse singlePlaceResponse(String place, String stateAbbrev, String longitude, String latitude) {
        return new ZippopotamResponse("35203", "United States", "US",
                List.of(new ZippopotamResponse.Place(place, "Alabama", stateAbbrev, longitude, latitude)));
    }

    @Test
    void lookup_success_mapsFirstPlaceStateAndCoordinates_whenMultiplePlacesReturned() {
        ZippopotamResponse response = new ZippopotamResponse("10001", "United States", "US", List.of(
                new ZippopotamResponse.Place("New York", "New York", "NY", "-74.0", "40.7"),
                new ZippopotamResponse.Place("Brooklyn", "New York", "NY", "-73.9", "40.6")
        ));
        when(responseSpec.body(ZippopotamResponse.class)).thenReturn(response);

        PostalLocation result = zippopotamClient.lookup("US", "10001");

        assertThat(result.place()).isEqualTo("New York");
        assertThat(result.state()).isEqualTo("NY");
        assertThat(result.longitude()).isEqualTo(-74.0);
        assertThat(result.latitude()).isEqualTo(40.7);
    }

    @Test
    void lookup_lowercasesCountryCodeInUri() {
        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(singlePlaceResponse("Birmingham", "AL", "-86.8", "33.5"));

        zippopotamClient.lookup("US", "35203");

        ArgumentCaptor<Object> countryArg = ArgumentCaptor.forClass(Object.class);
        verify(uriSpec).uri(eq("/{country}/{postalCode}"), countryArg.capture(), eq("35203"));
        assertThat(countryArg.getValue()).isEqualTo("us");
    }

    private static Stream<Arguments> emptyOrNullPlaceResponses() {
        return Stream.of(
                Arguments.of("null response body", null),
                Arguments.of("empty places list", new ZippopotamResponse("99999", "United States", "US", List.of())),
                Arguments.of("null places list", new ZippopotamResponse("99999", "United States", "US", null))
        );
    }

    @ParameterizedTest(name = "{0} throws PostalLookupException")
    @MethodSource("emptyOrNullPlaceResponses")
    void lookup_nullOrEmptyPlaces_throwsPostalLookupException(String label, ZippopotamResponse response) {
        when(responseSpec.body(ZippopotamResponse.class)).thenReturn(response);

        assertThatThrownBy(() -> zippopotamClient.lookup("US", "99999")).isInstanceOf(PostalLookupException.class);
    }

    private static Stream<Arguments> upstreamHttpErrors() {
        return Stream.of(
                Arguments.of(
                        HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null),
                        "No location found"),
                Arguments.of(
                        HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", HttpHeaders.EMPTY, new byte[0], null),
                        "unavailable")
        );
    }

    @ParameterizedTest(name = "upstream {0} wraps as PostalLookupException")
    @MethodSource("upstreamHttpErrors")
    void lookup_upstreamHttpError_wrapsAsPostalLookupException(HttpStatusCodeException upstreamError, String expectedMessageFragment) {
        when(responseSpec.body(ZippopotamResponse.class)).thenThrow(upstreamError);

        assertThatThrownBy(() -> zippopotamClient.lookup("US", "35203"))
                .isInstanceOf(PostalLookupException.class)
                .hasMessageContaining(expectedMessageFragment);
    }

    private static Stream<Arguments> unresolvableCoordinates() {
        return Stream.of(
                Arguments.of("non-numeric strings", "not-a-number", ""),
                Arguments.of("null values", null, null)
        );
    }

    @ParameterizedTest(name = "{0} return null lat/long rather than throwing")
    @MethodSource("unresolvableCoordinates")
    void lookup_malformedOrMissingCoordinates_returnNullRatherThanThrow(String label, String longitude, String latitude) {
        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(singlePlaceResponse("Nowhere", "ZZ", longitude, latitude));

        PostalLocation result = zippopotamClient.lookup("US", "35203");

        assertThat(result.longitude()).isNull();
        assertThat(result.latitude()).isNull();
    }
}

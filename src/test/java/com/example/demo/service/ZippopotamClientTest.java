package com.example.demo.service;

import com.example.demo.exception.PostalLookupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

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
    void lookup_success_mapsPlaceStateAndCoordinates() {
        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(singlePlaceResponse("Birmingham", "AL", "-86.8066", "33.521"));

        PostalLocation result = zippopotamClient.lookup("US", "35203");

        assertThat(result.place()).isEqualTo("Birmingham");
        assertThat(result.state()).isEqualTo("AL");
        assertThat(result.longitude()).isEqualTo(-86.8066);
        assertThat(result.latitude()).isEqualTo(33.521);
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

    @Test
    void lookup_usesFirstPlaceWhenMultipleReturned() {
        ZippopotamResponse response = new ZippopotamResponse("10001", "United States", "US", List.of(
                new ZippopotamResponse.Place("New York", "New York", "NY", "-74.0", "40.7"),
                new ZippopotamResponse.Place("Brooklyn", "New York", "NY", "-73.9", "40.6")
        ));
        when(responseSpec.body(ZippopotamResponse.class)).thenReturn(response);

        PostalLocation result = zippopotamClient.lookup("US", "10001");

        assertThat(result.place()).isEqualTo("New York");
    }

    @Test
    void lookup_nullOrEmptyPlaces_throwsPostalLookupException() {
        when(responseSpec.body(ZippopotamResponse.class)).thenReturn(null);
        assertThatThrownBy(() -> zippopotamClient.lookup("US", "99999")).isInstanceOf(PostalLookupException.class);

        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(new ZippopotamResponse("99999", "United States", "US", List.of()));
        assertThatThrownBy(() -> zippopotamClient.lookup("US", "99999")).isInstanceOf(PostalLookupException.class);

        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(new ZippopotamResponse("99999", "United States", "US", null));
        assertThatThrownBy(() -> zippopotamClient.lookup("US", "99999")).isInstanceOf(PostalLookupException.class);
    }

    @Test
    void lookup_notFoundFromUpstream_throwsPostalLookupException() {
        when(responseSpec.body(ZippopotamResponse.class))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> zippopotamClient.lookup("US", "00000"))
                .isInstanceOf(PostalLookupException.class)
                .hasMessageContaining("No location found");
    }

    @Test
    void lookup_upstreamServerError_wrapsAsPostalLookupException() {
        when(responseSpec.body(ZippopotamResponse.class))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> zippopotamClient.lookup("US", "35203"))
                .isInstanceOf(PostalLookupException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void lookup_malformedOrMissingCoordinates_returnNullRatherThanThrow() {
        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(singlePlaceResponse("Nowhere", "ZZ", "not-a-number", ""));
        PostalLocation malformed = zippopotamClient.lookup("US", "35203");
        assertThat(malformed.longitude()).isNull();
        assertThat(malformed.latitude()).isNull();

        when(responseSpec.body(ZippopotamResponse.class))
                .thenReturn(singlePlaceResponse("Nowhere", "ZZ", null, null));
        PostalLocation missing = zippopotamClient.lookup("US", "35203");
        assertThat(missing.longitude()).isNull();
        assertThat(missing.latitude()).isNull();
    }
}

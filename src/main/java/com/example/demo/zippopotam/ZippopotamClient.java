package com.example.demo.zippopotam;

import com.example.demo.exception.PostalLookupException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZippopotamClient {

    private final RestClient zippopotamRestClient;

    public PostalLocation lookup(String countryCode, String postalCode) {
        try {
            ZippopotamResponse response = zippopotamRestClient.get()
                    .uri("/{country}/{postalCode}", countryCode.toLowerCase(), postalCode)
                    .retrieve()
                    .body(ZippopotamResponse.class);

            if (response == null || response.places() == null || response.places().isEmpty()) {
                throw new PostalLookupException(
                        "No location found for country '" + countryCode + "' and postal code '" + postalCode + "'");
            }

            ZippopotamResponse.Place place = response.places().get(0);
            return new PostalLocation(
                    place.placeName(),
                    place.stateAbbreviation(),
                    parseCoordinate(place.longitude()),
                    parseCoordinate(place.latitude())
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new PostalLookupException(
                    "No location found for country '" + countryCode + "' and postal code '" + postalCode + "'");
        } catch (RestClientException e) {
            log.warn("Zippopotam lookup failed for {}/{}: {}", countryCode, postalCode, e.getMessage());
            throw new PostalLookupException("Postal code lookup service is unavailable: " + e.getMessage());
        }
    }

    private Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.example.demo.service;

import com.example.demo.config.CacheConfig;
import com.example.demo.exception.PostalLookupException;
import com.example.demo.model.PostalLocation;
import com.example.demo.model.ZippopotamResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.example.demo.exception.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZippopotamClient {

    private final RestClient zippopotamRestClient;

    @Cacheable(cacheNames = CacheConfig.POSTAL_LOCATIONS_CACHE, key = "#countryCode.toLowerCase() + ':' + #postalCode")
    public PostalLocation lookup(String countryCode, String postalCode) {
        try {
            ZippopotamResponse response = zippopotamRestClient.get()
                    .uri("/{country}/{postalCode}", countryCode.toLowerCase(), postalCode)
                    .retrieve()
                    .body(ZippopotamResponse.class);

            if (response == null || response.places() == null || response.places().isEmpty())
                throw new PostalLookupException(POSTAL_LOOKUP_NOT_FOUND, countryCode, postalCode);

            ZippopotamResponse.Place place = response.places().get(0);
            return new PostalLocation(
                    place.placeName(),
                    place.stateAbbreviation(),
                    parseCoordinate(place.longitude()),
                    parseCoordinate(place.latitude())
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new PostalLookupException(POSTAL_LOOKUP_NOT_FOUND, countryCode, postalCode);
        } catch (RestClientException e) {
            log.warn("Zippopotam lookup failed for {}/{}", countryCode, postalCode, e);
            throw new PostalLookupException(POSTAL_LOOKUP_UNAVAILABLE);
        }
    }

    private Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) return null;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

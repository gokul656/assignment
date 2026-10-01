package com.example.demo.zippopotam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZippopotamResponse(
        @JsonProperty("post code") String postCode,
        String country,
        @JsonProperty("country abbreviation") String countryAbbreviation,
        List<Place> places
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(
            @JsonProperty("place name") String placeName,
            String state,
            @JsonProperty("state abbreviation") String stateAbbreviation,
            String longitude,
            String latitude
    ) {
    }
}

package com.example.demo.model;

public record PostalLocation(
        String place,
        String state,
        Double longitude,
        Double latitude
) {
}

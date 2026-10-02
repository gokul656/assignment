package com.example.demo.service;

public record PostalLocation(
        String place,
        String state,
        Double longitude,
        Double latitude
) {
}

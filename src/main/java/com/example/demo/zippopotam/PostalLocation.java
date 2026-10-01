package com.example.demo.zippopotam;

public record PostalLocation(
        String place,
        String state,
        Double longitude,
        Double latitude
) {
}

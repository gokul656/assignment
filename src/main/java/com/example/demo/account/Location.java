package com.example.demo.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    private String place;
    private String state;
    private String country;
    private String postalCode;
    private Double longitude;
    private Double latitude;
}

package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private String accountId;
    private String name;
    private String email;
    private String country;
    private String postalCode;
    private Integer age;
    private AccountStatus status;
    private String securityPinHash;
    private Location location;
}

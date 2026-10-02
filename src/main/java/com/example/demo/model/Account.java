package com.example.demo.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private String accountId;
    private String name;
    @Column(unique = true)
    private String email;
    private String country;
    private String postalCode;
    private Integer age;
    @Enumerated(EnumType.STRING)
    private AccountStatus status;
    private String securityPinHash;
    // Location.country/postalCode are the resolved/denormalized copies used for display
    // (LocationResponse); Account.country/postalCode above are the account's own input fields -
    // same names, so the embedded columns need overriding to avoid a duplicate-column mapping.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "country", column = @Column(name = "location_country")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "location_postal_code"))
    })
    private Location location;
}

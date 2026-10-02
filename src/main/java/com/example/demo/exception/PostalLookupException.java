package com.example.demo.exception;

import org.springframework.http.HttpStatus;

/** 502 Bad Gateway - the upstream zippopotam.us lookup failed or returned nothing usable. */
public class PostalLookupException extends ApiException {
    public PostalLookupException(String template, Object... args) {
        super(HttpStatus.BAD_GATEWAY, format(template, args));
    }
}

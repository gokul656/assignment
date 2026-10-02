package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class PostalLookupException extends ApiException {
    public PostalLookupException(String template, Object... args) {
        super(HttpStatus.BAD_GATEWAY, format(template, args));
    }
}

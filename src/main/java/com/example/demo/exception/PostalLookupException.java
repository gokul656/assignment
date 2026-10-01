package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class PostalLookupException extends ApiException {
    public PostalLookupException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}

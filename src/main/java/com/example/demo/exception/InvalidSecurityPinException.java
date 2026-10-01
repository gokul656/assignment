package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class InvalidSecurityPinException extends ApiException {
    public InvalidSecurityPinException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}

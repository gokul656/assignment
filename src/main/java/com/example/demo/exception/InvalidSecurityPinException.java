package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class InvalidSecurityPinException extends ApiException {
    public InvalidSecurityPinException(String template, Object... args) {
        super(HttpStatus.FORBIDDEN, format(template, args));
    }
}

package com.example.demo.exception;

import org.springframework.http.HttpStatus;

/** 403 Forbidden - too many consecutive invalid PIN attempts; account is temporarily locked out. */
public class TooManyAttemptsException extends ApiException {
    public TooManyAttemptsException(String template, Object... args) {
        super(HttpStatus.FORBIDDEN, format(template, args));
    }
}

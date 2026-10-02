package com.example.demo.exception;

import org.springframework.http.HttpStatus;

/** 401 Unauthorized - the X-Verification-Token header is missing, invalid, expired, already used, or issued for a different account. */
public class InvalidVerificationTokenException extends ApiException {
    public InvalidVerificationTokenException(String template, Object... args) {
        super(HttpStatus.UNAUTHORIZED, format(template, args));
    }
}

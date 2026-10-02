package com.example.demo.exception;

import org.springframework.http.HttpStatus;

/** 409 Conflict - request conflicts with current account state (duplicate email, wrong status for operation). */
public class ConflictException extends ApiException {
    public ConflictException(String template, Object... args) {
        super(HttpStatus.CONFLICT, format(template, args));
    }
}

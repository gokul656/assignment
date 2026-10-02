package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends ApiException {
    public AccountNotFoundException(String template, Object... args) {
        super(HttpStatus.NOT_FOUND, format(template, args));
    }
}

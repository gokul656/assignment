package com.example.demo.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class ValidationException extends ApiException {
    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, message);
        this.fieldErrors = Collections.singletonMap(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Lets subclasses expose a {@code (String template, Object... args)} constructor without each repeating the format call. */
    protected static String format(String template, Object... args) {
        return args.length == 0 ? template : String.format(template, args);
    }
}

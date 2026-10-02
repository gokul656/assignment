package com.example.demo.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose request-body argument carries a {@code securityPin} field.
 * {@link PinValidationAspect} intercepts calls to annotated methods, pulls the PIN off that
 * argument via reflection, and checks it against the account's stored hash before the method
 * body runs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ValidatePin {
}

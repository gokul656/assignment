package com.example.demo.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller handler method as requiring a valid {@code X-Verification-Token} header
 * (obtained via POST /verify-pin) for the account identified by the {@code accountId} path
 * variable. Enforced by {@link VerificationTokenInterceptor} before the handler method runs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresVerificationToken {
}

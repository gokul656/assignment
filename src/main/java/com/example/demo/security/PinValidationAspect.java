package com.example.demo.security;

import com.example.demo.model.Account;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.Constants;
import com.example.demo.exception.InvalidSecurityPinException;
import com.example.demo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PinValidationAspect {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String MASKED_PIN = "****";

    @Before("@annotation(com.example.demo.security.ValidatePin)")
    public void validatePin(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        String accountId = firstStringArg(args);
        String providedPin = extractSecurityPin(args);

        log.info("Validating security PIN for accountId={}, method={}, providedPin={}",
                accountId, joinPoint.getSignature().toShortString(), mask(providedPin));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(Constants.ACCOUNT_NOT_FOUND_BY_ID, accountId));

        if (providedPin == null || !passwordEncoder.matches(providedPin, account.getSecurityPinHash())) {
            log.warn("PIN validation FAILED for accountId={}, providedPin={}", accountId, mask(providedPin));
            throw new InvalidSecurityPinException(Constants.INVALID_SECURITY_PIN, accountId);
        }

        log.info("PIN validation succeeded for accountId={}", accountId);
    }

    private String mask(String pin) {
        return pin == null ? "<missing>" : MASKED_PIN;
    }

    private String firstStringArg(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String s) {
                return s;
            }
        }
        return null;
    }

    private String extractSecurityPin(Object[] args) {
        for (Object arg : args) {
            if (arg == null || arg instanceof String) {
                continue;
            }
            try {
                Method getter = arg.getClass().getMethod("getSecurityPin");
                Object value = getter.invoke(arg);
                if (value != null) {
                    return value.toString();
                }
            } catch (NoSuchMethodException e) {
                // This argument doesn't carry a securityPin field - keep looking.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to read securityPin from " + arg.getClass().getSimpleName(), e);
            }
        }
        return null;
    }
}

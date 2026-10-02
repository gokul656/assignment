package com.example.demo.service;

import com.example.demo.domain.Account;
import com.example.demo.dto.VerifyPinResponse;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.InvalidSecurityPinException;
import com.example.demo.exception.TooManyAttemptsException;
import com.example.demo.repository.AccountRepository;
import com.example.demo.security.PinAttemptTracker;
import com.example.demo.security.VerificationTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.example.demo.exception.Constants.*;

@Service
@RequiredArgsConstructor
public class PinVerificationService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PinAttemptTracker attemptTracker;
    private final VerificationTokenStore tokenStore;

    public VerifyPinResponse verifyPin(String accountId, String securityPin) {
        if (attemptTracker.isLocked(accountId)) {
            throw new TooManyAttemptsException(TOO_MANY_PIN_ATTEMPTS, accountId);
        }

        Account account = repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(ACCOUNT_NOT_FOUND_BY_ID, accountId));

        if (!passwordEncoder.matches(securityPin, account.getSecurityPinHash())) {
            attemptTracker.recordFailure(accountId);
            throw new InvalidSecurityPinException(INVALID_SECURITY_PIN, accountId);
        }

        attemptTracker.recordSuccess(accountId);
        String token = tokenStore.issue(accountId);
        return new VerifyPinResponse()
                .verificationToken(token)
                .expiresInSeconds(VerificationTokenStore.TTL.toSeconds());
    }
}

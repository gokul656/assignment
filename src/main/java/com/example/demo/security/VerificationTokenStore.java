package com.example.demo.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues short-lived, single-use verification tokens (obtained via POST /verify-pin) that gate
 * PUT/DELETE/status-change. Tokens are opaque, random, and consumed (removed) on first use,
 * successful or not, so a captured token can never be replayed.
 */
@Component
public class VerificationTokenStore {

    public static final Duration TTL = Duration.ofSeconds(300);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, TokenEntry> tokensByValue = new ConcurrentHashMap<>();

    private record TokenEntry(String accountId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String issue(String accountId) {
        String token = generateToken();
        tokensByValue.put(token, new TokenEntry(accountId, Instant.now().plus(TTL)));
        return token;
    }

    /** Consumes (removes) the token regardless of outcome. Returns the account it was issued for, if it was still valid. */
    public Optional<String> consume(String token) {
        if (token == null) {
            return Optional.empty();
        }
        TokenEntry entry = tokensByValue.remove(token);
        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(entry.accountId());
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.example.demo.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks consecutive invalid-PIN attempts per account for POST /verify-pin. After
 * {@value #MAX_ATTEMPTS} consecutive failures, the account is locked out for
 * {@value #LOCKOUT_MINUTES} minutes; any attempt during that window is rejected without even
 * checking the PIN.
 */
@Component
public class PinAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    private static final class State {
        private int failures;
        private Instant lockedUntil;
    }

    public boolean isLocked(String accountId) {
        State state = states.get(accountId);
        return state != null && state.lockedUntil != null && Instant.now().isBefore(state.lockedUntil);
    }

    public void recordFailure(String accountId) {
        states.compute(accountId, (id, existing) -> {
            State state = existing == null ? new State() : existing;
            state.failures++;
            if (state.failures >= MAX_ATTEMPTS) {
                state.lockedUntil = Instant.now().plus(Duration.ofMinutes(LOCKOUT_MINUTES));
                state.failures = 0;
            }
            return state;
        });
    }

    public void recordSuccess(String accountId) {
        states.remove(accountId);
    }
}

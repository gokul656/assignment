package com.example.demo.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PinAttemptTrackerTest {

    private final PinAttemptTracker tracker = new PinAttemptTracker();

    @Test
    void isLocked_defaultsToFalseForUnknownAccount() {
        assertThat(tracker.isLocked("ABC123")).isFalse();
    }

    @Test
    void recordFailure_belowThreshold_doesNotLock() {
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("ABC123");
        }

        assertThat(tracker.isLocked("ABC123")).isFalse();
    }

    @Test
    void recordFailure_fifthConsecutiveFailure_locksAccount() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("ABC123");
        }

        assertThat(tracker.isLocked("ABC123")).isTrue();
    }

    @Test
    void recordSuccess_clearsFailureHistory() {
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("ABC123");
        }
        tracker.recordSuccess("ABC123");

        // Another 4 failures after a reset still shouldn't lock - the counter started over.
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("ABC123");
        }

        assertThat(tracker.isLocked("ABC123")).isFalse();
    }

    @Test
    void failuresAreTrackedIndependentlyPerAccount() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("ABC123");
        }

        assertThat(tracker.isLocked("ABC123")).isTrue();
        assertThat(tracker.isLocked("OTHER99")).isFalse();
    }
}

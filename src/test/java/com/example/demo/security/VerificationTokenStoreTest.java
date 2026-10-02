package com.example.demo.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationTokenStoreTest {

    private final VerificationTokenStore store = new VerificationTokenStore();

    @Test
    void issue_thenConsume_returnsAccountId() {
        String token = store.issue("ABC123");

        assertThat(store.consume(token)).contains("ABC123");
    }

    @Test
    void consume_isSingleUse() {
        String token = store.issue("ABC123");

        store.consume(token);

        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void consume_unknownToken_returnsEmpty() {
        assertThat(store.consume("never-issued")).isEmpty();
    }

    @Test
    void consume_nullToken_returnsEmpty() {
        assertThat(store.consume(null)).isEmpty();
    }

    @Test
    void issue_producesDistinctTokensAcrossCalls() {
        String a = store.issue("ABC123");
        String b = store.issue("ABC123");

        assertThat(a).isNotEqualTo(b);
    }
}

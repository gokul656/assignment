package com.example.demo.service;

import com.example.demo.domain.Account;
import com.example.demo.domain.AccountStatus;
import com.example.demo.dto.VerifyPinResponse;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.InvalidSecurityPinException;
import com.example.demo.exception.TooManyAttemptsException;
import com.example.demo.repository.AccountRepository;
import com.example.demo.security.PinAttemptTracker;
import com.example.demo.security.VerificationTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinVerificationServiceTest {

    private AccountRepository repository;
    private PinVerificationService pinVerificationService;
    private VerificationTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        repository = new AccountRepository();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        tokenStore = new VerificationTokenStore();
        pinVerificationService = new PinVerificationService(repository, encoder, new PinAttemptTracker(), tokenStore);

        repository.save(Account.builder()
                .accountId("ABC123")
                .name("Alice")
                .email("alice@example.com")
                .country("US")
                .postalCode("35203")
                .status(AccountStatus.ACTIVE)
                .securityPinHash(encoder.encode("1234"))
                .build());
    }

    @Test
    void verifyPin_correctPin_issuesRedeemableToken() {
        VerifyPinResponse response = pinVerificationService.verifyPin("ABC123", "1234");

        assertThat(response.getVerificationToken()).isNotBlank();
        assertThat(response.getExpiresInSeconds()).isEqualTo(300);
        assertThat(tokenStore.consume(response.getVerificationToken())).contains("ABC123");
    }

    @Test
    void verifyPin_wrongPin_throwsInvalidSecurityPin() {
        assertThatThrownBy(() -> pinVerificationService.verifyPin("ABC123", "0000"))
                .isInstanceOf(InvalidSecurityPinException.class);
    }

    @Test
    void verifyPin_unknownAccount_throwsNotFound() {
        assertThatThrownBy(() -> pinVerificationService.verifyPin("ZZZZZZ", "1234"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void verifyPin_fiveConsecutiveFailures_locksAccountEvenWithCorrectPinAfterward() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> pinVerificationService.verifyPin("ABC123", "0000"))
                    .isInstanceOf(InvalidSecurityPinException.class);
        }

        assertThatThrownBy(() -> pinVerificationService.verifyPin("ABC123", "1234"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void verifyPin_successResetsFailureCount() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> pinVerificationService.verifyPin("ABC123", "0000"))
                    .isInstanceOf(InvalidSecurityPinException.class);
        }

        pinVerificationService.verifyPin("ABC123", "1234");

        // Another 4 failures after the reset shouldn't trip the 5-failure lockout.
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> pinVerificationService.verifyPin("ABC123", "0000"))
                    .isInstanceOf(InvalidSecurityPinException.class);
        }
        VerifyPinResponse response = pinVerificationService.verifyPin("ABC123", "1234");
        assertThat(response.getVerificationToken()).isNotBlank();
    }
}

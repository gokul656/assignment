package com.example.demo.repository;

import com.example.demo.model.Account;
import com.example.demo.model.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real Spring Data JPA proxy against an embedded H2 database (auto-configured by
 * {@code @DataJpaTest}), rather than hand-rolled in-memory bookkeeping - case-insensitive email
 * lookup/uniqueness is backed by {@code findByEmailIgnoreCase}/{@code existsByEmailIgnoreCase}
 * derived queries, and there's no separate email index to maintain (a plain {@code save()} after
 * changing the email is enough).
 */
@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private AccountRepository repository;

    private Account account(String id, String email) {
        return Account.builder()
                .accountId(id)
                .name("Alice")
                .email(email)
                .country("US")
                .postalCode("35203")
                .status(AccountStatus.ACTIVE)
                .securityPinHash("1234")
                .build();
    }

    @Test
    void save_thenFindByIdAndExistsById_handlePresentAndAbsent() {
        assertThat(repository.existsById("ABC123")).isFalse();

        repository.save(account("ABC123", "alice@example.com"));

        assertThat(repository.findById("ABC123")).isPresent();
        assertThat(repository.findById("ABC123").get().getEmail()).isEqualTo("alice@example.com");
        assertThat(repository.findById("NOPE00")).isEmpty();
        assertThat(repository.existsById("ABC123")).isTrue();
    }

    @Test
    void emailLookups_areCaseInsensitiveAndHandleAbsent() {
        repository.save(account("ABC123", "Alice@Example.com"));

        assertThat(repository.findByEmailIgnoreCase("alice@example.com")).isPresent();
        assertThat(repository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM")).isPresent();
        assertThat(repository.findByEmailIgnoreCase("nobody@example.com")).isEmpty();
        assertThat(repository.existsByEmailIgnoreCase("alice@example.com")).isTrue();
        assertThat(repository.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    void deleteById_removesAccountAndIsNoOpForUnknownId() {
        repository.save(account("ABC123", "alice@example.com"));

        repository.deleteById("ZZZZZZ");
        assertThat(repository.findById("ABC123")).isPresent();

        repository.deleteById("ABC123");
        assertThat(repository.findById("ABC123")).isEmpty();
        assertThat(repository.existsByEmailIgnoreCase("alice@example.com")).isFalse();
    }

    @Test
    void changingEmailAndSaving_updatesTheEmailLookup_noSeparateReindexNeeded() {
        Account account = repository.save(account("ABC123", "old@example.com"));

        account.setEmail("new@example.com");
        repository.save(account);

        assertThat(repository.existsByEmailIgnoreCase("old@example.com")).isFalse();
        assertThat(repository.findByEmailIgnoreCase("new@example.com")).isPresent();
        assertThat(repository.findByEmailIgnoreCase("new@example.com").get().getAccountId()).isEqualTo("ABC123");
    }

    @Test
    void findAll_returnsEverySavedAccount() {
        repository.save(account("ABC123", "alice@example.com"));
        repository.save(account("DEF456", "bob@example.com"));

        assertThat(repository.findAll()).hasSize(2);
    }
}

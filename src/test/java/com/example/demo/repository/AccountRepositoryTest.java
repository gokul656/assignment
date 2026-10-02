package com.example.demo.repository;

import com.example.demo.model.Account;
import com.example.demo.model.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRepositoryTest {

    private AccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AccountRepository();
    }

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

        assertThat(repository.findByEmail("alice@example.com")).isPresent();
        assertThat(repository.findByEmail("ALICE@EXAMPLE.COM")).isPresent();
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
        assertThat(repository.existsByEmail("alice@example.com")).isTrue();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void deleteById_removesAccountAndIsNoOpForUnknownId() {
        repository.save(account("ABC123", "alice@example.com"));

        repository.deleteById("ZZZZZZ");
        assertThat(repository.findById("ABC123")).isPresent();

        repository.deleteById("ABC123");
        assertThat(repository.findById("ABC123")).isEmpty();
        assertThat(repository.existsByEmail("alice@example.com")).isFalse();
    }

    @Test
    void reindexEmail_movesEmailIndexToNewAddress() {
        Account account = account("ABC123", "old@example.com");
        repository.save(account);

        account.setEmail("new@example.com");
        repository.reindexEmail("old@example.com", account);

        assertThat(repository.existsByEmail("old@example.com")).isFalse();
        assertThat(repository.findByEmail("new@example.com")).isPresent();
        assertThat(repository.findByEmail("new@example.com").get().getAccountId()).isEqualTo("ABC123");
    }

    @Test
    void findAll_returnsImmutableSnapshot() {
        repository.save(account("ABC123", "alice@example.com"));
        var snapshot = repository.findAll();

        repository.save(account("DEF456", "bob@example.com"));

        assertThat(snapshot).hasSize(1);
        assertThat(repository.findAll()).hasSize(2);
        assertThatThrownBy(() -> snapshot.add(account("GHI789", "carl@example.com")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

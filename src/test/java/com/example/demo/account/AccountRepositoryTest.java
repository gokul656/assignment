package com.example.demo.account;

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
                .securityPin("1234")
                .build();
    }

    @Test
    void save_thenFindById_returnsAccount() {
        repository.save(account("ABC123", "alice@example.com"));

        assertThat(repository.findById("ABC123")).isPresent();
        assertThat(repository.findById("ABC123").get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertThat(repository.findById("NOPE00")).isEmpty();
    }

    @Test
    void findByEmail_isCaseInsensitive() {
        repository.save(account("ABC123", "Alice@Example.com"));

        assertThat(repository.findByEmail("alice@example.com")).isPresent();
        assertThat(repository.findByEmail("ALICE@EXAMPLE.COM")).isPresent();
    }

    @Test
    void findByEmail_unknownEmail_returnsEmpty() {
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void existsByEmail_isCaseInsensitive() {
        repository.save(account("ABC123", "Bob@Example.com"));

        assertThat(repository.existsByEmail("bob@example.com")).isTrue();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void existsById_reflectsStoredAccounts() {
        assertThat(repository.existsById("ABC123")).isFalse();
        repository.save(account("ABC123", "alice@example.com"));
        assertThat(repository.existsById("ABC123")).isTrue();
    }

    @Test
    void deleteById_removesAccountAndEmailIndex() {
        repository.save(account("ABC123", "alice@example.com"));

        repository.deleteById("ABC123");

        assertThat(repository.findById("ABC123")).isEmpty();
        assertThat(repository.existsByEmail("alice@example.com")).isFalse();
    }

    @Test
    void deleteById_unknownId_isNoOp() {
        repository.save(account("ABC123", "alice@example.com"));

        repository.deleteById("ZZZZZZ");

        assertThat(repository.findById("ABC123")).isPresent();
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
    void findAll_returnsSnapshotNotLiveView() {
        repository.save(account("ABC123", "alice@example.com"));
        var snapshot = repository.findAll();

        repository.save(account("DEF456", "bob@example.com"));

        assertThat(snapshot).hasSize(1);
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void findAll_isImmutable() {
        repository.save(account("ABC123", "alice@example.com"));

        assertThatThrownBy(() -> repository.findAll().add(account("DEF456", "bob@example.com")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

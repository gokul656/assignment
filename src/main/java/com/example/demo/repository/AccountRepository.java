package com.example.demo.repository;

import com.example.demo.domain.Account;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AccountRepository {

    private final ConcurrentHashMap<String, Account> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> accountIdByEmail = new ConcurrentHashMap<>();

    public Account save(Account account) {
        accountsById.put(account.getAccountId(), account);
        accountIdByEmail.put(normalizeEmail(account.getEmail()), account.getAccountId());
        return account;
    }

    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(accountsById.get(accountId));
    }

    public Optional<Account> findByEmail(String email) {
        String id = accountIdByEmail.get(normalizeEmail(email));
        return id == null ? Optional.empty() : findById(id);
    }

    public boolean existsByEmail(String email) {
        return accountIdByEmail.containsKey(normalizeEmail(email));
    }

    public boolean existsById(String accountId) {
        return accountsById.containsKey(accountId);
    }

    public void deleteById(String accountId) {
        Account removed = accountsById.remove(accountId);
        if (removed != null) {
            accountIdByEmail.remove(normalizeEmail(removed.getEmail()));
        }
    }

    public List<Account> findAll() {
        return List.copyOf(accountsById.values());
    }

    public Collection<String> allIds() {
        return accountsById.keySet();
    }

    public void reindexEmail(String oldEmail, Account account) {
        accountIdByEmail.remove(normalizeEmail(oldEmail));
        accountIdByEmail.put(normalizeEmail(account.getEmail()), account.getAccountId());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase();
    }
}

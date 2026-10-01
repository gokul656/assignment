package com.example.demo.account;

import com.example.demo.account.model.AccountStatusValue;
import com.example.demo.account.model.ChangeStatusRequest;
import com.example.demo.account.model.CountryCode;
import com.example.demo.account.model.CreateAccountRequest;
import com.example.demo.account.model.CreateAccountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a handful of demo accounts on startup so the API has data to show off immediately
 * (GET by id/email, counts grouping, update, status change, delete). Each creation hits the
 * real zippopotam.us API, so failures here (e.g. no network) are logged and skipped rather
 * than blocking application startup.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final AccountService accountService;

    @Override
    public void run(String... args) {
        seed("Alice", "alice@example.com", CountryCode.US, "35203", 29);   // Birmingham, AL
        seed("Bob", "bob@example.com", CountryCode.US, "35203", 34);       // Birmingham, AL
        seed("Carl", "carl@example.com", CountryCode.US, "35801", 41);     // Huntsville, AL
        seed("Dana", "dana@example.com", CountryCode.US, "06810", 23);     // Danbury, CT
        seed("Elan", "elan@example.com", CountryCode.US, "06103", 55);     // Hartford, CT
        seed("Frank", "frank@example.com", CountryCode.DE, "10115", 31);   // Berlin
        seed("Greta", "greta@example.com", CountryCode.ES, "28001", 27);   // Madrid
        seed("Hugo", "hugo@example.com", CountryCode.FR, "75001", null);   // Paris

        // Demonstrate a full status lifecycle: deactivate one account so DELETE can be exercised.
        toInactive("ivy@example.com", CountryCode.US, "10001", 38);
    }

    private void seed(String name, String email, CountryCode country, String postalCode, Integer age) {
        try {
            CreateAccountResponse response = accountService.createAccount(
                    new CreateAccountRequest().name(name).email(email).country(country).postalCode(postalCode).age(age));
            log.info("Seeded account {} ({}) -> id={}, pin={}", name, email, response.getAccountId(), response.getSecurityPin());
        } catch (Exception e) {
            log.warn("Skipped seeding account {} ({}): {}", name, email, e.getMessage());
        }
    }

    private void toInactive(String email, CountryCode country, String postalCode, Integer age) {
        try {
            CreateAccountResponse created = accountService.createAccount(
                    new CreateAccountRequest().name("Ivy").email(email).country(country).postalCode(postalCode).age(age));
            accountService.changeStatus(created.getAccountId(), new ChangeStatusRequest().status(AccountStatusValue.INACTIVE));
            log.info("Seeded inactive account Ivy ({}) -> id={}, pin={} (ready for DELETE demo)",
                    email, created.getAccountId(), created.getSecurityPin());
        } catch (Exception e) {
            log.warn("Skipped seeding inactive account ({}): {}", email, e.getMessage());
        }
    }
}

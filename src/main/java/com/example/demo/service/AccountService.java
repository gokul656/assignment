package com.example.demo.service;

import com.example.demo.model.Account;
import com.example.demo.model.AccountStatus;
import com.example.demo.model.Location;
import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.AccountStatusValue;
import com.example.demo.dto.ChangeStatusRequest;
import com.example.demo.dto.CountryCode;
import com.example.demo.dto.CountryCountResponse;
import com.example.demo.dto.CreateAccountRequest;
import com.example.demo.dto.CreateAccountResponse;
import com.example.demo.dto.DeleteAccountRequest;
import com.example.demo.dto.LocationResponse;
import com.example.demo.dto.PlaceCountResponse;
import com.example.demo.dto.StateCountResponse;
import com.example.demo.dto.UpdateAccountRequest;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ValidationException;
import com.example.demo.model.PostalLocation;
import com.example.demo.repository.AccountRepository;
import com.example.demo.security.ValidatePin;
import com.example.demo.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static com.example.demo.exception.Constants.*;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final ZippopotamClient zippopotamClient;
    private final PasswordEncoder passwordEncoder;

    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        String email = request.getEmail();
        if (repository.existsByEmail(email)) {
            throw new ConflictException(EMAIL_ALREADY_EXISTS, email);
        }
        String country = request.getCountry().name();
        String postalCode = request.getPostalCode();

        PostalLocation postalLocation = zippopotamClient.lookup(country, postalCode);
        Location location = Location.builder()
                .place(postalLocation.place())
                .state(postalLocation.state())
                .country(country)
                .postalCode(postalCode)
                .longitude(postalLocation.longitude())
                .latitude(postalLocation.latitude())
                .build();

        String accountId = generateUniqueAccountId();
        String securityPin = IdGenerator.randomSecurityPin();

        Account account = Account.builder()
                .accountId(accountId)
                .name(request.getName())
                .email(email)
                .country(country)
                .postalCode(postalCode)
                .age(request.getAge())
                .status(AccountStatus.ACTIVE)
                .securityPinHash(passwordEncoder.encode(securityPin))
                .location(location)
                .build();

        account = repository.save(account);
        return new CreateAccountResponse()
                .accountId(accountId)
                .status(toStatusValue(account.getStatus()))
                .securityPin(securityPin);
    }

    @ValidatePin
    public AccountResponse updateAccount(String accountId, UpdateAccountRequest request) {
        Account account = findOrThrow(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException(ONLY_ACTIVE_ACCOUNTS_CAN_BE_UPDATED, account.getStatus());
        }

        if (request.getName() != null) {
            account.setName(request.getName());
        }
        if (request.getEmail() != null) {
            String newEmail = request.getEmail();
            if (!newEmail.equalsIgnoreCase(account.getEmail()) && repository.existsByEmail(newEmail)) {
                throw new ConflictException(EMAIL_ALREADY_EXISTS, newEmail);
            }
            String oldEmail = account.getEmail();
            account.setEmail(newEmail);
            repository.reindexEmail(oldEmail, account);
        }
        if (request.getAge() != null) {
            account.setAge(request.getAge());
        }
        if (request.getStatus() != null) {
            account.setStatus(AccountStatus.valueOf(request.getStatus().name()));
        }

        boolean countryChanged = request.getCountry() != null && !request.getCountry().name().equalsIgnoreCase(account.getCountry());
        boolean postalCodeChanged = request.getPostalCode() != null && !request.getPostalCode().equals(account.getPostalCode());

        if (request.getCountry() != null) {
            account.setCountry(request.getCountry().name());
        }
        if (request.getPostalCode() != null) {
            account.setPostalCode(request.getPostalCode());
        }

        if (countryChanged || postalCodeChanged) {
            PostalLocation postalLocation = zippopotamClient.lookup(account.getCountry(), account.getPostalCode());
            account.setLocation(Location.builder()
                    .place(postalLocation.place())
                    .state(postalLocation.state())
                    .country(account.getCountry())
                    .postalCode(account.getPostalCode())
                    .longitude(postalLocation.longitude())
                    .latitude(postalLocation.latitude())
                    .build());
        }

        repository.save(account);
        return toAccountResponse(account);
    }

    @ValidatePin
    public void deleteAccount(String accountId, DeleteAccountRequest request) {
        Account account = findOrThrow(accountId);
        if (account.getStatus() != AccountStatus.INACTIVE) {
            throw new ConflictException(ONLY_INACTIVE_ACCOUNTS_CAN_BE_DELETED, account.getStatus());
        }
        repository.deleteById(accountId);
    }

    public AccountResponse getAccount(String accountId, String email) {
        if ((accountId == null || accountId.isBlank()) && (email == null || email.isBlank())) {
            throw new ValidationException(ACCOUNT_ID_OR_EMAIL_REQUIRED);
        }
        Account account;
        if (accountId != null && !accountId.isBlank()) {
            account = findOrThrow(accountId);
        } else {
            account = repository.findByEmail(email)
                    .orElseThrow(() -> new AccountNotFoundException(ACCOUNT_NOT_FOUND_BY_EMAIL, email));
        }
        return toAccountResponse(account);
    }

    @ValidatePin
    public CreateAccountResponse changeStatus(String accountId, ChangeStatusRequest request) {
        Account account = findOrThrow(accountId);
        account.setStatus(AccountStatus.valueOf(request.getStatus().name()));
        repository.save(account);
        return new CreateAccountResponse()
                .accountId(account.getAccountId())
                .status(toStatusValue(account.getStatus()));
    }

    public CountryCountResponse getCounts(CountryCode country) {
        List<Account> matching = repository.findAll().stream()
                .filter(a -> a.getCountry().equalsIgnoreCase(country.name()))
                .toList();

        TreeMap<String, List<Account>> byState = new TreeMap<>();
        for (Account account : matching) {
            String state = account.getLocation() != null && account.getLocation().getState() != null
                    ? account.getLocation().getState()
                    : "UNKNOWN";
            byState.computeIfAbsent(state, k -> new ArrayList<>()).add(account);
        }

        List<StateCountResponse> states = byState.entrySet().stream()
                .map(entry -> {
                    TreeMap<String, Long> byPlace = new TreeMap<>();
                    for (Account account : entry.getValue()) {
                        String place = account.getLocation() != null && account.getLocation().getPlace() != null
                                ? account.getLocation().getPlace()
                                : "UNKNOWN";
                        byPlace.merge(place, 1L, Long::sum);
                    }
                    List<PlaceCountResponse> places = byPlace.entrySet().stream()
                            .map(e -> new PlaceCountResponse().place(e.getKey()).count(e.getValue()))
                            .toList();
                    return new StateCountResponse()
                            .state(entry.getKey())
                            .count((long) entry.getValue().size())
                            .places(places);
                })
                .sorted(Comparator.comparing(StateCountResponse::getState))
                .toList();

        return new CountryCountResponse()
                .country(country)
                .count((long) matching.size())
                .states(states);
    }

    private Account findOrThrow(String accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(ACCOUNT_NOT_FOUND_BY_ID, accountId));
    }

    private String generateUniqueAccountId() {
        String candidate;
        do {
            candidate = IdGenerator.randomAccountId();
        } while (repository.existsById(candidate));
        return candidate;
    }

    private AccountStatusValue toStatusValue(AccountStatus status) {
        return AccountStatusValue.valueOf(status.name());
    }

    private AccountResponse toAccountResponse(Account account) {
        LocationResponse locationResponse = null;
        if (account.getLocation() != null) {
            Location loc = account.getLocation();
            locationResponse = new LocationResponse()
                    .place(loc.getPlace())
                    .state(loc.getState())
                    .country(CountryCode.valueOf(loc.getCountry()))
                    .postalCode(loc.getPostalCode())
                    .longitude(loc.getLongitude())
                    .latitude(loc.getLatitude());
        }
        return new AccountResponse()
                .accountId(account.getAccountId())
                .email(account.getEmail())
                .status(toStatusValue(account.getStatus()))
                .age(account.getAge())
                .location(locationResponse);
    }
}

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
import com.example.demo.repository.StatePlaceCount;
import com.example.demo.security.ValidatePin;
import com.example.demo.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.example.demo.exception.Constants.*;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final ZippopotamClient zippopotamClient;
    private final PasswordEncoder passwordEncoder;

    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        String email = request.getEmail();
        if (repository.existsByEmailIgnoreCase(email)) throw new ConflictException(EMAIL_ALREADY_EXISTS, email);

        String country = request.getCountry().name();
        String postalCode = request.getPostalCode();

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
                .location(resolveLocation(country, postalCode))
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

        List.<BiConsumer<Account, UpdateAccountRequest>>of(this::applyName, this::applyEmail, this::applyAge, this::applyStatus)
                .forEach(fieldUpdate -> fieldUpdate.accept(account, request));
        relocateIfNeeded(account, request);

        return toAccountResponse(repository.save(account));
    }

    @ValidatePin
    public void deleteAccount(String accountId, DeleteAccountRequest request) {
        Account account = findOrThrow(accountId);
        if (account.getStatus() != AccountStatus.INACTIVE)
            throw new ConflictException(ONLY_INACTIVE_ACCOUNTS_CAN_BE_DELETED, account.getStatus());

        repository.deleteById(accountId);
    }

    public AccountResponse getAccount(String accountId, String email) {
        if ((accountId == null || accountId.isBlank()) && (email == null || email.isBlank()))
            throw new ValidationException(ACCOUNT_ID_OR_EMAIL_REQUIRED);

        Account account;
        if (accountId != null && !accountId.isBlank()) {
            account = findOrThrow(accountId);
        } else {
            account = repository.findByEmailIgnoreCase(email)
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
        String countryName = country.name();
        long totalCount = repository.countByCountryIgnoreCase(countryName);
        List<StatePlaceCount> rows = repository.countGroupedByStateAndPlace(countryName);

        Map<String, List<StatePlaceCount>> byState = rows.stream()
                .collect(Collectors.groupingBy(StatePlaceCount::getState, TreeMap::new, Collectors.toList()));

        List<StateCountResponse> states = byState.entrySet().stream()
                .map(entry -> toStateCountResponse(entry.getKey(), entry.getValue()))
                .toList();

        return new CountryCountResponse()
                .country(country)
                .count(totalCount)
                .states(states);
    }

    private StateCountResponse toStateCountResponse(String state, List<StatePlaceCount> rows) {
        List<PlaceCountResponse> places = rows.stream()
                .sorted(Comparator.comparing(StatePlaceCount::getPlace))
                .map(row -> new PlaceCountResponse().place(row.getPlace()).count(row.getCount()))
                .toList();

        long stateTotal = rows.stream().mapToLong(StatePlaceCount::getCount).sum();

        return new StateCountResponse()
                .state(state)
                .count(stateTotal)
                .places(places);
    }

    private void applyName(Account account, UpdateAccountRequest request) {
        if (request.getName() != null) {
            account.setName(request.getName());
        }
    }

    private void applyEmail(Account account, UpdateAccountRequest request) {
        if (request.getEmail() == null) return;

        String newEmail = request.getEmail();
        if (!newEmail.equalsIgnoreCase(account.getEmail()) && repository.existsByEmailIgnoreCase(newEmail))
            throw new ConflictException(EMAIL_ALREADY_EXISTS, newEmail);

        account.setEmail(newEmail);
    }

    private void applyAge(Account account, UpdateAccountRequest request) {
        if (request.getAge() != null) account.setAge(request.getAge());
    }

    private void applyStatus(Account account, UpdateAccountRequest request) {
        if (request.getStatus() != null) {
            account.setStatus(AccountStatus.valueOf(request.getStatus().name()));
        }
    }

    private void relocateIfNeeded(Account account, UpdateAccountRequest request) {
        boolean countryChanged = request.getCountry() != null && !request.getCountry().name().equalsIgnoreCase(account.getCountry());
        boolean postalCodeChanged = request.getPostalCode() != null && !request.getPostalCode().equals(account.getPostalCode());

        if (request.getCountry() != null) account.setCountry(request.getCountry().name());
        if (request.getPostalCode() != null) account.setPostalCode(request.getPostalCode());

        if (countryChanged || postalCodeChanged) {
            account.setLocation(resolveLocation(account.getCountry(), account.getPostalCode()));
        }
    }

    private Location resolveLocation(String country, String postalCode) {
        PostalLocation postalLocation = zippopotamClient.lookup(country, postalCode);
        return Location.builder()
                .place(postalLocation.place())
                .state(postalLocation.state())
                .country(country)
                .postalCode(postalCode)
                .longitude(postalLocation.longitude())
                .latitude(postalLocation.latitude())
                .build();
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
        return new AccountResponse()
                .accountId(account.getAccountId())
                .email(account.getEmail())
                .status(toStatusValue(account.getStatus()))
                .age(account.getAge())
                .location(toLocationResponse(account.getLocation()));
    }

    private LocationResponse toLocationResponse(Location location) {
        if (location == null) return null;

        return new LocationResponse()
                .place(location.getPlace())
                .state(location.getState())
                .country(CountryCode.valueOf(location.getCountry()))
                .postalCode(location.getPostalCode())
                .longitude(location.getLongitude())
                .latitude(location.getLatitude());
    }
}

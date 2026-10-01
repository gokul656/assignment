package com.example.demo.account;

import com.example.demo.account.model.AccountResponse;
import com.example.demo.account.model.AccountStatusValue;
import com.example.demo.account.model.ChangeStatusRequest;
import com.example.demo.account.model.CountryCode;
import com.example.demo.account.model.CountryCountResponse;
import com.example.demo.account.model.CreateAccountRequest;
import com.example.demo.account.model.CreateAccountResponse;
import com.example.demo.account.model.UpdateAccountRequest;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.InvalidSecurityPinException;
import com.example.demo.exception.ValidationException;
import com.example.demo.zippopotam.PostalLocation;
import com.example.demo.zippopotam.ZippopotamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private ZippopotamClient zippopotamClient;

    private AccountRepository repository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        repository = new AccountRepository();
        accountService = new AccountService(repository, zippopotamClient);
        lenient().when(zippopotamClient.lookup(anyString(), anyString()))
                .thenReturn(new PostalLocation("Birmingham", "AL", -86.8, 33.5));
    }

    private CreateAccountRequest validRequest(String email) {
        return new CreateAccountRequest().name("Alice").email(email).country(CountryCode.US).postalCode("35203").age(30);
    }

    @Test
    void createAccount_returnsActiveStatusAndCredentials() {
        CreateAccountResponse response = accountService.createAccount(validRequest("alice@example.com"));

        assertThat(response.getAccountId()).hasSize(6);
        assertThat(response.getStatus()).isEqualTo(AccountStatusValue.ACTIVE);
        assertThat(response.getSecurityPin()).matches("\\d{4}");
    }

    @Test
    void createAccount_rejectsDuplicateEmail() {
        accountService.createAccount(validRequest("dup@example.com"));

        assertThatThrownBy(() -> accountService.createAccount(validRequest("dup@example.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getAccount_byAccountId_returnsLocation() {
        CreateAccountResponse created = accountService.createAccount(validRequest("loc@example.com"));

        AccountResponse response = accountService.getAccount(created.getAccountId(), null);

        assertThat(response.getEmail()).isEqualTo("loc@example.com");
        assertThat(response.getLocation().getPlace()).isEqualTo("Birmingham");
        assertThat(response.getLocation().getState()).isEqualTo("AL");
    }

    @Test
    void getAccount_byEmail_returnsSameAccount() {
        CreateAccountResponse created = accountService.createAccount(validRequest("byemail@example.com"));

        AccountResponse response = accountService.getAccount(null, "byemail@example.com");

        assertThat(response.getAccountId()).isEqualTo(created.getAccountId());
    }

    @Test
    void getAccount_withNoCriteria_throwsValidationException() {
        assertThatThrownBy(() -> accountService.getAccount(null, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void getAccount_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> accountService.getAccount("ZZZZZZ", null)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void updateAccount_onActiveAccount_appliesChanges() {
        CreateAccountResponse created = accountService.createAccount(validRequest("update@example.com"));

        UpdateAccountRequest update = new UpdateAccountRequest().name("Bob").age(40);
        AccountResponse response = accountService.updateAccount(created.getAccountId(), update);

        assertThat(response.getAge()).isEqualTo(40);
    }

    @Test
    void updateAccount_refreshesLocationWhenPostalCodeChanges() {
        CreateAccountResponse created = accountService.createAccount(validRequest("relocate@example.com"));
        when(zippopotamClient.lookup("US", "06810")).thenReturn(new PostalLocation("Danbury", "CT", -73.4, 41.4));

        UpdateAccountRequest update = new UpdateAccountRequest().postalCode("06810");
        AccountResponse response = accountService.updateAccount(created.getAccountId(), update);

        assertThat(response.getLocation().getPlace()).isEqualTo("Danbury");
        assertThat(response.getLocation().getState()).isEqualTo("CT");
    }

    @Test
    void updateAccount_onInactiveAccount_throwsConflict() {
        CreateAccountResponse created = accountService.createAccount(validRequest("inactive@example.com"));
        accountService.changeStatus(created.getAccountId(), new ChangeStatusRequest().status(AccountStatusValue.INACTIVE));

        UpdateAccountRequest update = new UpdateAccountRequest().name("Bob");
        assertThatThrownBy(() -> accountService.updateAccount(created.getAccountId(), update))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteAccount_requiresInactiveStatus() {
        CreateAccountResponse created = accountService.createAccount(validRequest("del-active@example.com"));

        assertThatThrownBy(() -> accountService.deleteAccount(created.getAccountId(), created.getSecurityPin()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteAccount_requiresCorrectPin() {
        CreateAccountResponse created = accountService.createAccount(validRequest("del-wrongpin@example.com"));
        accountService.changeStatus(created.getAccountId(), new ChangeStatusRequest().status(AccountStatusValue.INACTIVE));

        assertThatThrownBy(() -> accountService.deleteAccount(created.getAccountId(), "0000"))
                .isInstanceOf(InvalidSecurityPinException.class);
    }

    @Test
    void deleteAccount_succeedsWhenInactiveAndPinMatches() {
        CreateAccountResponse created = accountService.createAccount(validRequest("del-ok@example.com"));
        accountService.changeStatus(created.getAccountId(), new ChangeStatusRequest().status(AccountStatusValue.INACTIVE));

        accountService.deleteAccount(created.getAccountId(), created.getSecurityPin());

        assertThatThrownBy(() -> accountService.getAccount(created.getAccountId(), null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getCounts_groupsByStateThenPlace() {
        accountService.createAccount(validRequest("a1@example.com"));
        accountService.createAccount(validRequest("a2@example.com"));
        when(zippopotamClient.lookup("US", "06810")).thenReturn(new PostalLocation("Danbury", "CT", -73.4, 41.4));
        CreateAccountRequest ctRequest = new CreateAccountRequest().name("Carl").email("a3@example.com").country(CountryCode.US).postalCode("06810").age(25);
        accountService.createAccount(ctRequest);

        CountryCountResponse counts = accountService.getCounts(CountryCode.US);

        assertThat(counts.getCountry()).isEqualTo(CountryCode.US);
        assertThat(counts.getCount()).isEqualTo(3);
        assertThat(counts.getStates()).hasSize(2);
        assertThat(counts.getStates().get(0).getState()).isEqualTo("AL");
        assertThat(counts.getStates().get(0).getCount()).isEqualTo(2);
        assertThat(counts.getStates().get(0).getPlaces().get(0).getPlace()).isEqualTo("Birmingham");
        assertThat(counts.getStates().get(0).getPlaces().get(0).getCount()).isEqualTo(2);
        assertThat(counts.getStates().get(1).getState()).isEqualTo("CT");
        assertThat(counts.getStates().get(1).getCount()).isEqualTo(1);
    }

    @Test
    void updateAccount_changingEmailToExistingAccount_throwsConflict() {
        accountService.createAccount(validRequest("taken@example.com"));
        CreateAccountResponse created = accountService.createAccount(validRequest("owner@example.com"));

        UpdateAccountRequest update = new UpdateAccountRequest().email("taken@example.com");
        assertThatThrownBy(() -> accountService.updateAccount(created.getAccountId(), update))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAccount_changingEmailToSameValueCaseInsensitive_doesNotThrow() {
        CreateAccountResponse created = accountService.createAccount(validRequest("same@example.com"));

        UpdateAccountRequest update = new UpdateAccountRequest().email("SAME@example.com");
        AccountResponse response = accountService.updateAccount(created.getAccountId(), update);

        assertThat(response.getEmail()).isEqualTo("SAME@example.com");
        assertThat(accountService.getAccount(null, "same@example.com").getAccountId()).isEqualTo(created.getAccountId());
    }

    @Test
    void updateAccount_emailChange_reindexesOldEmailAway() {
        CreateAccountResponse created = accountService.createAccount(validRequest("old@example.com"));

        accountService.updateAccount(created.getAccountId(), new UpdateAccountRequest().email("new@example.com"));

        assertThatThrownBy(() -> accountService.getAccount(null, "old@example.com"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(accountService.getAccount(null, "new@example.com").getAccountId()).isEqualTo(created.getAccountId());
    }

    @Test
    void updateAccount_countryChangeOnly_refreshesLocation() {
        CreateAccountResponse created = accountService.createAccount(validRequest("country-change@example.com"));
        when(zippopotamClient.lookup("DE", "35203")).thenReturn(new PostalLocation("Berlin", "BE", 13.4, 52.5));

        UpdateAccountRequest update = new UpdateAccountRequest().country(CountryCode.DE);
        AccountResponse response = accountService.updateAccount(created.getAccountId(), update);

        assertThat(response.getLocation().getPlace()).isEqualTo("Berlin");
        assertThat(response.getLocation().getCountry()).isEqualTo(CountryCode.DE);
    }

    @Test
    void updateAccount_withoutCountryOrPostalCodeChange_doesNotCallZippopotamAgain() {
        CreateAccountResponse created = accountService.createAccount(validRequest("no-relocate@example.com"));

        accountService.updateAccount(created.getAccountId(), new UpdateAccountRequest().name("Zed").age(50));

        verify(zippopotamClient, Mockito.times(1)).lookup(anyString(), anyString());
    }

    @Test
    void updateAccount_sameCountryAndPostalCodeResubmitted_doesNotCallZippopotamAgain() {
        CreateAccountResponse created = accountService.createAccount(validRequest("resubmit@example.com"));

        UpdateAccountRequest update = new UpdateAccountRequest().country(CountryCode.US).postalCode("35203");
        accountService.updateAccount(created.getAccountId(), update);

        verify(zippopotamClient, Mockito.times(1)).lookup(anyString(), anyString());
    }

    @Test
    void updateAccount_statusOnlyChange_updatesStatusWithoutTouchingLocation() {
        CreateAccountResponse created = accountService.createAccount(validRequest("status-only@example.com"));

        AccountResponse response = accountService.updateAccount(created.getAccountId(),
                new UpdateAccountRequest().status(AccountStatusValue.INACTIVE));

        assertThat(response.getStatus()).isEqualTo(AccountStatusValue.INACTIVE);
        assertThat(response.getLocation().getPlace()).isEqualTo("Birmingham");
        verify(zippopotamClient, Mockito.times(1)).lookup(anyString(), anyString());
    }

    @Test
    void updateAccount_unknownAccountId_throwsNotFound() {
        assertThatThrownBy(() -> accountService.updateAccount("ZZZZZZ", new UpdateAccountRequest().age(40)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getAccount_bothAccountIdAndEmailProvided_accountIdTakesPrecedence() {
        CreateAccountResponse a = accountService.createAccount(validRequest("a@example.com"));
        CreateAccountResponse b = accountService.createAccount(validRequest("b@example.com"));

        AccountResponse response = accountService.getAccount(a.getAccountId(), "b@example.com");

        assertThat(response.getAccountId()).isEqualTo(a.getAccountId());
    }

    @Test
    void getAccount_unknownEmail_throwsNotFound() {
        assertThatThrownBy(() -> accountService.getAccount(null, "nobody@example.com"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteAccount_unknownAccountId_throwsNotFound() {
        assertThatThrownBy(() -> accountService.deleteAccount("ZZZZZZ", "1234"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteAccount_nullSecurityPin_throwsInvalidPin() {
        CreateAccountResponse created = accountService.createAccount(validRequest("null-pin@example.com"));
        accountService.changeStatus(created.getAccountId(), new ChangeStatusRequest().status(AccountStatusValue.INACTIVE));

        assertThatThrownBy(() -> accountService.deleteAccount(created.getAccountId(), null))
                .isInstanceOf(InvalidSecurityPinException.class);
    }

    @Test
    void changeStatus_unknownAccountId_throwsNotFound() {
        assertThatThrownBy(() -> accountService.changeStatus("ZZZZZZ", new ChangeStatusRequest().status(AccountStatusValue.ACTIVE)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getCounts_noMatchingAccounts_returnsZeroCountAndEmptyStates() {
        accountService.createAccount(validRequest("onlyus@example.com"));

        CountryCountResponse counts = accountService.getCounts(CountryCode.FR);

        assertThat(counts.getCountry()).isEqualTo(CountryCode.FR);
        assertThat(counts.getCount()).isEqualTo(0);
        assertThat(counts.getStates()).isEmpty();
    }

    @Test
    void getCounts_accountWithNoLocation_groupsUnderUnknown() {
        Account noLocationAccount = Account.builder()
                .accountId("NOLOC1")
                .name("Ghost")
                .email("ghost@example.com")
                .country("US")
                .postalCode("00000")
                .status(AccountStatus.ACTIVE)
                .securityPin("0000")
                .location(null)
                .build();
        repository.save(noLocationAccount);

        CountryCountResponse counts = accountService.getCounts(CountryCode.US);

        assertThat(counts.getCount()).isEqualTo(1);
        assertThat(counts.getStates()).hasSize(1);
        assertThat(counts.getStates().get(0).getState()).isEqualTo("UNKNOWN");
        assertThat(counts.getStates().get(0).getPlaces().get(0).getPlace()).isEqualTo("UNKNOWN");
    }

    @Test
    void changeStatus_updatesStatus() {
        CreateAccountResponse created = accountService.createAccount(validRequest("status@example.com"));

        CreateAccountResponse response = accountService.changeStatus(created.getAccountId(), new ChangeStatusRequest().status(AccountStatusValue.INACTIVE));

        assertThat(response.getStatus()).isEqualTo(AccountStatusValue.INACTIVE);
    }
}

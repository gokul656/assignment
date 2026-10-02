package com.example.demo.controller;

import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.AccountStatusValue;
import com.example.demo.dto.ChangeStatusRequest;
import com.example.demo.dto.CountryCode;
import com.example.demo.dto.CreateAccountRequest;
import com.example.demo.dto.CreateAccountResponse;
import com.example.demo.dto.LocationResponse;
import com.example.demo.dto.VerifyPinRequest;
import com.example.demo.dto.VerifyPinResponse;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.InvalidSecurityPinException;
import com.example.demo.exception.TooManyAttemptsException;
import com.example.demo.security.VerificationTokenStore;
import com.example.demo.service.AccountService;
import com.example.demo.service.PinVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final String TOKEN_HEADER = "X-Verification-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private PinVerificationService pinVerificationService;

    // Required to wire VerificationTokenInterceptor/WebConfig, which @WebMvcTest auto-detects as a HandlerInterceptor bean.
    @MockitoBean
    private VerificationTokenStore tokenStore;

    @BeforeEach
    void stubTokenInterceptorToAllowByDefault() {
        when(tokenStore.consume("valid-token")).thenReturn(Optional.of("ABC123"));
    }

    private static CreateAccountRequest validCreateRequest() {
        return new CreateAccountRequest().name("Alice").email("alice@example.com").country(CountryCode.US).postalCode("35203").age(30);
    }

    @Test
    void createAccount_returns201WithBody() throws Exception {
        when(accountService.createAccount(any()))
                .thenReturn(new CreateAccountResponse().accountId("ABC123").status(AccountStatusValue.ACTIVE).securityPin("1234"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("ABC123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.securityPin").value("1234"));
    }

    @Test
    void createAccount_multipleBeanValidationViolations_returns400WithAllFieldErrors() throws Exception {
        // name non-alphanumeric, email malformed, postal code wrong length, age out of range - all in one request.
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest()
                                .name("Al ice!").email("not-an-email").postalCode("123").age(200))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.postalCode").exists())
                .andExpect(jsonPath("$.fieldErrors.age").exists());
    }

    @Test
    void createAccount_invalidCountry_returns400WithFieldError() throws Exception {
        // "CA" isn't a CountryCode constant, so this has to be raw JSON rather than a typed request object.
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"country\":\"CA\",\"postalCode\":\"35203\",\"age\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.country").exists())
                .andExpect(jsonPath("$.fieldErrors.country", not(containsString("at [Source"))))
                .andExpect(jsonPath("$.fieldErrors.country", not(containsString("reference chain"))));
    }

    @Test
    void createAccount_nonRequestedStatus_returns400WithFieldError() throws Exception {
        // "ACTIVE" isn't a valid create-time status (only "Requested" is), so this is raw JSON too.
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"country\":\"US\",\"postalCode\":\"35203\",\"age\":30,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void createAccount_duplicateEmail_returns409() throws Exception {
        when(accountService.createAccount(any())).thenThrow(new ConflictException("An account already exists"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest().email("dup@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyPin_valid_returnsToken() throws Exception {
        when(pinVerificationService.verifyPin("ABC123", "1234"))
                .thenReturn(new VerifyPinResponse().verificationToken("valid-token").expiresInSeconds(300L));

        mockMvc.perform(post("/api/accounts/ABC123/verify-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyPinRequest().securityPin("1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").value("valid-token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    @Test
    void verifyPin_wrongPin_returns403() throws Exception {
        when(pinVerificationService.verifyPin("ABC123", "0000")).thenThrow(new InvalidSecurityPinException("bad pin"));

        mockMvc.perform(post("/api/accounts/ABC123/verify-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyPinRequest().securityPin("0000"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyPin_tooManyAttempts_returns403() throws Exception {
        when(pinVerificationService.verifyPin(anyString(), anyString())).thenThrow(new TooManyAttemptsException("locked out"));

        mockMvc.perform(post("/api/accounts/ABC123/verify-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyPinRequest().securityPin("1234"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyPin_malformedPin_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/ABC123/verify-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"securityPin\":\"12a4\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAccount_found_returns200() throws Exception {
        LocationResponse location = new LocationResponse()
                .place("Birmingham").state("AL").country(CountryCode.US).postalCode("35203").longitude(-86.8).latitude(33.5);
        when(accountService.getAccount("ABC123", null))
                .thenReturn(new AccountResponse().accountId("ABC123").email("alice@example.com").status(AccountStatusValue.ACTIVE).age(30).location(location));

        mockMvc.perform(get("/api/accounts").param("accountId", "ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.location.place").value("Birmingham"));
    }

    @Test
    void getAccount_notFound_returns404() throws Exception {
        when(accountService.getAccount("ZZZZZZ", null)).thenThrow(new AccountNotFoundException("not found"));

        mockMvc.perform(get("/api/accounts").param("accountId", "ZZZZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAccount_onInactive_returns409() throws Exception {
        when(accountService.updateAccount(anyString(), any())).thenThrow(new ConflictException("Only Active accounts can be updated"));

        mockMvc.perform(put("/api/accounts/ABC123")
                        .header(TOKEN_HEADER, "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":40}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateAccount_invalidPostalCode_returns400() throws Exception {
        mockMvc.perform(put("/api/accounts/ABC123")
                        .header(TOKEN_HEADER, "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postalCode\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.postalCode").exists());
    }

    @Test
    void updateAccount_missingToken_returns401() throws Exception {
        mockMvc.perform(put("/api/accounts/ABC123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":40}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAccount_invalidOrExpiredToken_returns401() throws Exception {
        when(tokenStore.consume("stale-token")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/accounts/ABC123")
                        .header(TOKEN_HEADER, "stale-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":40}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAccount_tokenIssuedForDifferentAccount_returns401() throws Exception {
        when(tokenStore.consume("other-accounts-token")).thenReturn(Optional.of("OTHER99"));

        mockMvc.perform(put("/api/accounts/ABC123")
                        .header(TOKEN_HEADER, "other-accounts-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":40}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_missingToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/accounts/ABC123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_valid_returns204() throws Exception {
        mockMvc.perform(delete("/api/accounts/ABC123").header(TOKEN_HEADER, "valid-token"))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount("ABC123");
    }

    @Test
    void changeStatus_valid_returns200() throws Exception {
        when(accountService.changeStatus(anyString(), any()))
                .thenReturn(new CreateAccountResponse().accountId("ABC123").status(AccountStatusValue.INACTIVE));

        mockMvc.perform(patch("/api/accounts/ABC123/status")
                        .header(TOKEN_HEADER, "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest().status(AccountStatusValue.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void changeStatus_missingToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/accounts/ABC123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest().status(AccountStatusValue.INACTIVE))))
                .andExpect(status().isUnauthorized());
    }
}

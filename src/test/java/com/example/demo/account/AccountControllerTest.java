package com.example.demo.account;

import com.example.demo.account.model.AccountResponse;
import com.example.demo.account.model.AccountStatusValue;
import com.example.demo.account.model.ChangeStatusRequest;
import com.example.demo.account.model.CountryCode;
import com.example.demo.account.model.CreateAccountRequest;
import com.example.demo.account.model.CreateAccountResponse;
import com.example.demo.account.model.LocationResponse;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

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
    void createAccount_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest().name(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createAccount_nonAlphanumericName_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest().name("Al ice!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createAccount_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest().email("not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void createAccount_invalidCountry_returns400() throws Exception {
        // "CA" isn't a CountryCode enum constant, so this has to be raw JSON rather than a typed request object.
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"country\":\"CA\",\"postalCode\":\"35203\",\"age\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.country").exists());
    }

    @Test
    void createAccount_invalidPostalCode_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest().postalCode("123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.postalCode").exists());
    }

    @Test
    void createAccount_ageOutOfRange_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest().age(200))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.age").exists());
    }

    @Test
    void createAccount_nonRequestedStatus_returns400() throws Exception {
        // "ACTIVE" isn't a valid CreateStatusValue (only "Requested" is), so this is raw JSON too.
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":40}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateAccount_invalidPostalCode_returns400() throws Exception {
        mockMvc.perform(put("/api/accounts/ABC123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postalCode\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.postalCode").exists());
    }

    @Test
    void deleteAccount_requiresSecurityPinParam() throws Exception {
        mockMvc.perform(delete("/api/accounts/ABC123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAccount_valid_returns204() throws Exception {
        mockMvc.perform(delete("/api/accounts/ABC123").param("securityPin", "1234"))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount("ABC123", "1234");
    }

    @Test
    void changeStatus_returns200() throws Exception {
        when(accountService.changeStatus(anyString(), any()))
                .thenReturn(new CreateAccountResponse().accountId("ABC123").status(AccountStatusValue.INACTIVE));

        mockMvc.perform(patch("/api/accounts/ABC123/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusRequest().status(AccountStatusValue.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }
}

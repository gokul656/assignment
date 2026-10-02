package com.example.demo.controller;

import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.ChangeStatusRequest;
import com.example.demo.dto.CountryCode;
import com.example.demo.dto.CountryCountResponse;
import com.example.demo.dto.CreateAccountRequest;
import com.example.demo.dto.CreateAccountResponse;
import com.example.demo.dto.UpdateAccountRequest;
import com.example.demo.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController implements AccountsApi {

    private final AccountService accountService;

    @Override
    public ResponseEntity<CreateAccountResponse> createAccount(CreateAccountRequest createAccountRequest) {
        CreateAccountResponse response = accountService.createAccount(createAccountRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<AccountResponse> updateAccount(String accountId, UpdateAccountRequest updateAccountRequest) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, updateAccountRequest));
    }

    @Override
    public ResponseEntity<Void> deleteAccount(String accountId, String securityPin) {
        accountService.deleteAccount(accountId, securityPin);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AccountResponse> getAccount(String accountId, String email) {
        return ResponseEntity.ok(accountService.getAccount(accountId, email));
    }

    @Override
    public ResponseEntity<CountryCountResponse> getCounts(CountryCode country) {
        return ResponseEntity.ok(accountService.getCounts(country));
    }

    // Bonus (a): dedicated endpoint to change an account's status.
    @Override
    public ResponseEntity<CreateAccountResponse> changeStatus(String accountId, ChangeStatusRequest changeStatusRequest) {
        return ResponseEntity.ok(accountService.changeStatus(accountId, changeStatusRequest));
    }
}

package com.example.demo.controller;

import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.ChangeStatusRequest;
import com.example.demo.dto.CountryCode;
import com.example.demo.dto.CountryCountResponse;
import com.example.demo.dto.CreateAccountRequest;
import com.example.demo.dto.CreateAccountResponse;
import com.example.demo.dto.UpdateAccountRequest;
import com.example.demo.dto.VerifyPinRequest;
import com.example.demo.dto.VerifyPinResponse;
import com.example.demo.security.RequiresVerificationToken;
import com.example.demo.service.AccountService;
import com.example.demo.service.PinVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountController implements AccountsApi {

    private final AccountService accountService;
    private final PinVerificationService pinVerificationService;

    @Override
    public ResponseEntity<CreateAccountResponse> createAccount(CreateAccountRequest createAccountRequest) {
        CreateAccountResponse response = accountService.createAccount(createAccountRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<VerifyPinResponse> verifyPin(String accountId, VerifyPinRequest verifyPinRequest) {
        return ResponseEntity.ok(pinVerificationService.verifyPin(accountId, verifyPinRequest.getSecurityPin()));
    }

    @Override
    @RequiresVerificationToken
    public ResponseEntity<AccountResponse> updateAccount(String accountId, String xVerificationToken, UpdateAccountRequest updateAccountRequest) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, updateAccountRequest));
    }

    @Override
    @RequiresVerificationToken
    public ResponseEntity<Void> deleteAccount(String accountId, String xVerificationToken) {
        accountService.deleteAccount(accountId);
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

    @Override
    public ResponseEntity<CreateAccountResponse> changeStatus(String accountId, String xVerificationToken, ChangeStatusRequest changeStatusRequest) {
        return ResponseEntity.ok(accountService.changeStatus(accountId, changeStatusRequest));
    }
}

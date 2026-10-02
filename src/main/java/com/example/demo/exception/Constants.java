package com.example.demo.exception;

public final class Constants {

    private Constants() {
    }

    // AccountService
    public static final String EMAIL_ALREADY_EXISTS = "An account already exists for email '%s'";
    public static final String ONLY_ACTIVE_ACCOUNTS_CAN_BE_UPDATED = "Only Active accounts can be updated (current status: %s)";
    public static final String ONLY_INACTIVE_ACCOUNTS_CAN_BE_DELETED = "Only Inactive accounts can be deleted (current status: %s)";
    public static final String INVALID_SECURITY_PIN = "Invalid security PIN for account '%s'";
    public static final String ACCOUNT_ID_OR_EMAIL_REQUIRED = "Either 'accountId' or 'email' must be provided";
    public static final String ACCOUNT_NOT_FOUND_BY_EMAIL = "No account found for email '%s'";
    public static final String ACCOUNT_NOT_FOUND_BY_ID = "No account found with id '%s'";

    // ZippopotamClient
    public static final String POSTAL_LOOKUP_NOT_FOUND = "No location found for country '%s' and postal code '%s'";
    public static final String POSTAL_LOOKUP_UNAVAILABLE = "Postal code lookup service is unavailable: %s";

    // GlobalExceptionHandler
    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String INVALID_FIELD_VALUE = "Invalid value for field '%s': %s";
}

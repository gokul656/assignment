package com.example.demo.account;

import org.apache.commons.lang3.RandomStringUtils;

public final class IdGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private IdGenerator() {
    }

    public static String randomAccountId() {
        return RandomStringUtils.secure().next(6, ALPHANUMERIC);
    }

    public static String randomSecurityPin() {
        return RandomStringUtils.secure().nextNumeric(4);
    }
}

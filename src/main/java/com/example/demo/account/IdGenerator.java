package com.example.demo.account;

import java.security.SecureRandom;

public final class IdGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String randomAccountId() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    public static String randomSecurityPin() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }
}

package com.example.demo.account;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void randomAccountId_isSixCharsAlphanumericUppercase() {
        String id = IdGenerator.randomAccountId();

        assertThat(id).hasSize(6);
        assertThat(id).matches("^[A-Z0-9]{6}$");
    }

    @Test
    void randomAccountId_producesDifferentValuesAcrossCalls() {
        var ids = IntStream.range(0, 50).mapToObj(i -> IdGenerator.randomAccountId()).distinct().toList();

        assertThat(ids).hasSizeGreaterThan(1);
    }

    @Test
    void randomSecurityPin_isFourDigitsZeroPadded() {
        var pins = IntStream.range(0, 200).mapToObj(i -> IdGenerator.randomSecurityPin()).toList();

        assertThat(pins).allMatch(pin -> pin.matches("^\\d{4}$"));
    }
}

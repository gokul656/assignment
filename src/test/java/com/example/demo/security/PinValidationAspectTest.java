package com.example.demo.security;

import com.example.demo.model.Account;
import com.example.demo.model.AccountStatus;
import com.example.demo.dto.AccountStatusValue;
import com.example.demo.dto.ChangeStatusRequest;
import com.example.demo.dto.DeleteAccountRequest;
import com.example.demo.dto.UpdateAccountRequest;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.InvalidSecurityPinException;
import com.example.demo.repository.AccountRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit-tests {@link PinValidationAspect} directly (bypassing Spring AOP proxying) by invoking
 * {@code validatePin(JoinPoint)} with a stubbed {@link JoinPoint}, parameterized across the three
 * request shapes it has to extract a PIN from via reflection (update/delete/change-status).
 */
@ExtendWith(MockitoExtension.class)
class PinValidationAspectTest {

    private static final String ACCOUNT_ID = "ABC123";
    private static final String CORRECT_PIN = "1234";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    private PasswordEncoder passwordEncoder;
    private PinValidationAspect aspect;
    private Account account;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        aspect = new PinValidationAspect(accountRepository, passwordEncoder);
        account = Account.builder()
                .accountId(ACCOUNT_ID)
                .status(AccountStatus.ACTIVE)
                .securityPinHash(passwordEncoder.encode(CORRECT_PIN))
                .build();
    }

    private static Stream<Arguments> requestShapes() {
        Function<String, Object> updateRequest = pin -> new UpdateAccountRequest().securityPin(pin);
        Function<String, Object> deleteRequest = pin -> new DeleteAccountRequest().securityPin(pin);
        Function<String, Object> changeStatusRequest =
                pin -> new ChangeStatusRequest().status(AccountStatusValue.INACTIVE).securityPin(pin);

        return Stream.of(
                Arguments.of("updateAccount", updateRequest),
                Arguments.of("deleteAccount", deleteRequest),
                Arguments.of("changeStatus", changeStatusRequest)
        );
    }

    @ParameterizedTest(name = "{0}: correct PIN passes validation")
    @MethodSource("requestShapes")
    void correctPin_doesNotThrow(String methodName, Function<String, Object> requestFactory) {
        givenArgs(methodName, ACCOUNT_ID, requestFactory.apply(CORRECT_PIN));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatCode(() -> aspect.validatePin(joinPoint)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}: wrong PIN is rejected with 403")
    @MethodSource("requestShapes")
    void wrongPin_throwsInvalidSecurityPin(String methodName, Function<String, Object> requestFactory) {
        givenArgs(methodName, ACCOUNT_ID, requestFactory.apply("0000"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> aspect.validatePin(joinPoint))
                .isInstanceOf(InvalidSecurityPinException.class);
    }

    @ParameterizedTest(name = "{0}: missing PIN is rejected with 403")
    @MethodSource("requestShapes")
    void missingPin_throwsInvalidSecurityPin(String methodName, Function<String, Object> requestFactory) {
        givenArgs(methodName, ACCOUNT_ID, requestFactory.apply(null));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> aspect.validatePin(joinPoint))
                .isInstanceOf(InvalidSecurityPinException.class);
    }

    @ParameterizedTest(name = "{0}: unknown account is rejected with 404")
    @MethodSource("requestShapes")
    void unknownAccount_throwsAccountNotFound(String methodName, Function<String, Object> requestFactory) {
        givenArgs(methodName, "ZZZZZZ", requestFactory.apply(CORRECT_PIN));
        when(accountRepository.findById("ZZZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aspect.validatePin(joinPoint))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private void givenArgs(String methodName, String accountId, Object request) {
        when(joinPoint.getArgs()).thenReturn(new Object[]{accountId, request});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AccountService." + methodName + "(..)");
    }
}

package com.example.demo.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String arg) {
    }

    private static Stream<Arguments> apiExceptionsWithExpectedStatus() {
        return Stream.of(
                Arguments.of(new AccountNotFoundException("no account"), HttpStatus.NOT_FOUND),
                Arguments.of(new ConflictException("dup"), HttpStatus.CONFLICT),
                Arguments.of(new InvalidSecurityPinException("bad pin"), HttpStatus.FORBIDDEN),
                Arguments.of(new PostalLookupException("upstream down"), HttpStatus.BAD_GATEWAY)
        );
    }

    @ParameterizedTest(name = "{0} maps to {1}")
    @MethodSource("apiExceptionsWithExpectedStatus")
    void handleApiException_mapsEachSubtypeToItsDeclaredStatus(ApiException ex, HttpStatus expectedStatus) {
        assertThat(handler.handleApiException(ex).getStatusCode()).isEqualTo(expectedStatus);
    }

    @Test
    void handleApiException_nonValidationException_passesMessageThroughWithNoFieldErrors() {
        ResponseEntity<ErrorResponse> nonValidation = handler.handleApiException(new AccountNotFoundException("no account"));
        assertThat(nonValidation.getBody().message()).isEqualTo("no account");
        assertThat(nonValidation.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void handleApiException_validationException_withAndWithoutField() {
        ResponseEntity<ErrorResponse> withField = handler.handleApiException(new ValidationException("country", "must be US/DE/ES/FR"));
        assertThat(withField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(withField.getBody().fieldErrors()).containsEntry("country", "must be US/DE/ES/FR");

        ResponseEntity<ErrorResponse> withoutField = handler.handleApiException(new ValidationException("either accountId or email required"));
        assertThat(withoutField.getBody().fieldErrors()).isEmpty();
        assertThat(withoutField.getBody().message()).isEqualTo("either accountId or email required");
    }

    @Test
    void handleBeanValidation_collectsAllFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "createAccountRequest");
        bindingResult.addError(new FieldError("createAccountRequest", "name", "Name is mandatory"));
        bindingResult.addError(new FieldError("createAccountRequest", "age", "Age must be between 0 and 150"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleBeanValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fieldErrors())
                .containsEntry("name", "Name is mandatory")
                .containsEntry("age", "Age must be between 0 and 150");
    }

    @Test
    void handleUnreadableBody_withJacksonCauseHavingPropertyName_extractsFieldError() {
        JacksonException jacksonException = Mockito.mock(JacksonException.class);
        JacksonException.Reference reference = new JacksonException.Reference(new Object(), "country");
        when(jacksonException.getPath()).thenReturn(List.of(reference));
        when(jacksonException.getMessage()).thenReturn("Unexpected value 'CA'");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("outer message", jacksonException, null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fieldErrors().get("country")).contains("Unexpected value 'CA'");
    }

    @Test
    void handleUnreadableBody_unrecognizedProperty_returnsCleanFieldError_doesNotLeakClassName() {
        UnrecognizedPropertyException upe = Mockito.mock(UnrecognizedPropertyException.class);
        when(upe.getPropertyName()).thenReturn("notInSpecField");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("outer message", upe, null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsEntry("notInSpecField", "Unrecognized field 'notInSpecField'");
        assertThat(response.getBody().fieldErrors().get("notInSpecField")).doesNotContain("com.example", "known properties");
    }

    private static Stream<Arguments> jacksonPathsWithNoUsableFieldName() {
        return Stream.of(
                Arguments.of("non-empty path but no property name", List.of(new JacksonException.Reference(new Object(), 0))),
                Arguments.of("empty path", List.of())
        );
    }

    @ParameterizedTest(name = "{0} falls back to the generic message")
    @MethodSource("jacksonPathsWithNoUsableFieldName")
    void handleUnreadableBody_noUsableFieldName_fallsBackToGenericMessage(String label, List<JacksonException.Reference> path) {
        JacksonException jacksonException = Mockito.mock(JacksonException.class);
        when(jacksonException.getPath()).thenReturn(path);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("outer message", jacksonException, null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).isEqualTo(Constants.MALFORMED_REQUEST_BODY);
    }

    @Test
    void handleUnreadableBody_withoutJacksonCause_fallsBackToGenericMessage_doesNotLeakInternalDetails() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error at [Source: ...]; nested exception is tools.jackson.core.JsonParseException: boom",
                new RuntimeException("boom"), null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).isEqualTo(Constants.MALFORMED_REQUEST_BODY);
        assertThat(response.getBody().message()).doesNotContain("JsonParseException", "[Source");
    }

    @Test
    void handleMissingParam_returns400_messageMentionsTheParameterName() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("securityPin", "String");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParam(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("securityPin");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void handleTypeMismatch_returns400_withFieldErrorButNoInternalTypeName() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "ZZ", CountryCodeStub.class, "country", dummyMethodParameter(), new IllegalArgumentException("bad enum"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("country");
        assertThat(response.getBody().fieldErrors().get("country")).contains("ZZ");
        assertThat(response.getBody().fieldErrors().get("country")).doesNotContain("CountryCodeStub", "com.example");
    }

    /** Stand-in for a generated DTO enum type, just to give the type-mismatch exception something to name internally. */
    private static final class CountryCodeStub {
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage_doesNotLeakInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("npe at com.internal.SecretClass.method(SecretClass.java:42)"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo(Constants.UNEXPECTED_ERROR);
        assertThat(response.getBody().message()).doesNotContain("SecretClass");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }
}

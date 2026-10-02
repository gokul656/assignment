package com.example.demo.exception;

import org.junit.jupiter.api.Test;
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

import java.lang.reflect.Method;
import java.util.List;

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

    @Test
    void handleApiException_mapsEachSubtypeToItsDeclaredStatus() {
        assertThat(handler.handleApiException(new AccountNotFoundException("no account")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleApiException(new ConflictException("dup")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleApiException(new InvalidSecurityPinException("bad pin")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleApiException(new PostalLookupException("upstream down")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        ResponseEntity<ErrorResponse> nonValidation = handler.handleApiException(new AccountNotFoundException("no account"));
        assertThat(nonValidation.getBody().message()).isEqualTo("no account");
        assertThat(nonValidation.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void handleApiException_validationExceptionWithField_includesFieldError() {
        ValidationException ex = new ValidationException("country", "must be US/DE/ES/FR");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsEntry("country", "must be US/DE/ES/FR");
    }

    @Test
    void handleApiException_validationExceptionWithoutField_hasNoFieldErrors() {
        ValidationException ex = new ValidationException("either accountId or email required");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).isEqualTo("either accountId or email required");
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
    void handleUnreadableBody_jacksonCauseWithoutPropertyName_fallsBackToRawMessage() {
        JacksonException jacksonException = Mockito.mock(JacksonException.class);
        JacksonException.Reference reference = new JacksonException.Reference(new Object(), 0);
        when(jacksonException.getPath()).thenReturn(List.of(reference));
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("outer message", jacksonException, null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).isEqualTo("outer message");
    }

    @Test
    void handleUnreadableBody_jacksonCauseWithEmptyPath_fallsBackToRawMessage() {
        JacksonException jacksonException = Mockito.mock(JacksonException.class);
        when(jacksonException.getPath()).thenReturn(List.of());
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("outer message", jacksonException, null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).isEqualTo("outer message");
    }

    @Test
    void handleUnreadableBody_withoutJacksonCause_fallsBackToRawMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("malformed json", new RuntimeException("boom"), null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().message()).isEqualTo("malformed json");
    }

    @Test
    void handleBadRequest_returns400ForMissingParamAndTypeMismatch() throws NoSuchMethodException {
        MissingServletRequestParameterException missingParam = new MissingServletRequestParameterException("securityPin", "String");
        ResponseEntity<ErrorResponse> missingParamResponse = handler.handleBadRequest(missingParam);
        assertThat(missingParamResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingParamResponse.getBody().message()).contains("securityPin");
        assertThat(missingParamResponse.getBody().fieldErrors()).isEmpty();

        MethodArgumentTypeMismatchException typeMismatch = new MethodArgumentTypeMismatchException(
                "CA", String.class, "country", dummyMethodParameter(), new IllegalArgumentException("bad enum"));
        ResponseEntity<ErrorResponse> typeMismatchResponse = handler.handleBadRequest(typeMismatch);
        assertThat(typeMismatchResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

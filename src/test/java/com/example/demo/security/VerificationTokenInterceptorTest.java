package com.example.demo.security;

import com.example.demo.exception.InvalidVerificationTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationTokenInterceptorTest {

    @Mock
    private VerificationTokenStore tokenStore;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private VerificationTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new VerificationTokenInterceptor(tokenStore);
    }

    static class DummyController {
        @RequiresVerificationToken
        public void protectedMethod() {
        }

        public void unprotectedMethod() {
        }
    }

    private HandlerMethod protectedHandlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(new DummyController(), DummyController.class.getMethod("protectedMethod"));
    }

    private HandlerMethod unprotectedHandlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(new DummyController(), DummyController.class.getMethod("unprotectedMethod"));
    }

    @Test
    void preHandle_nonAnnotatedMethod_allowsThroughWithoutTouchingStore() throws NoSuchMethodException {
        boolean result = interceptor.preHandle(request, response, unprotectedHandlerMethod());

        assertThat(result).isTrue();
        verifyNoInteractions(tokenStore);
    }

    @Test
    void preHandle_nonHandlerMethodObject_allowsThrough() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void preHandle_missingHeader_throwsInvalidToken() throws NoSuchMethodException {
        when(request.getHeader("X-Verification-Token")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, protectedHandlerMethod()))
                .isInstanceOf(InvalidVerificationTokenException.class);
        verifyNoInteractions(tokenStore);
    }

    @Test
    void preHandle_blankHeader_throwsInvalidToken() throws NoSuchMethodException {
        when(request.getHeader("X-Verification-Token")).thenReturn("   ");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, protectedHandlerMethod()))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void preHandle_tokenNotFoundOrExpired_throwsInvalidToken() throws NoSuchMethodException {
        when(request.getHeader("X-Verification-Token")).thenReturn("sometoken");
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("accountId", "ABC123"));
        when(tokenStore.consume("sometoken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, response, protectedHandlerMethod()))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void preHandle_tokenIssuedForDifferentAccount_throwsInvalidToken() throws NoSuchMethodException {
        when(request.getHeader("X-Verification-Token")).thenReturn("sometoken");
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("accountId", "ABC123"));
        when(tokenStore.consume("sometoken")).thenReturn(Optional.of("OTHER99"));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, protectedHandlerMethod()))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void preHandle_validTokenForMatchingAccount_returnsTrue() throws NoSuchMethodException {
        when(request.getHeader("X-Verification-Token")).thenReturn("sometoken");
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("accountId", "ABC123"));
        when(tokenStore.consume("sometoken")).thenReturn(Optional.of("ABC123"));

        assertThat(interceptor.preHandle(request, response, protectedHandlerMethod())).isTrue();
    }
}

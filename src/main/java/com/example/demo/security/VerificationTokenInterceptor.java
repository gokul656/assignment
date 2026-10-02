package com.example.demo.security;

import com.example.demo.exception.Constants;
import com.example.demo.exception.InvalidVerificationTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;

/**
 * Enforces {@link RequiresVerificationToken} on annotated controller methods: reads the
 * {@code X-Verification-Token} header and {@code accountId} path variable directly off the raw
 * request (this runs before Spring's argument binding), consumes the token via
 * {@link VerificationTokenStore}, and confirms it was issued for this same account.
 */
@Component
@RequiredArgsConstructor
public class VerificationTokenInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "X-Verification-Token";

    private final VerificationTokenStore tokenStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(RequiresVerificationToken.class) == null) {
            return true;
        }

        String token = request.getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            throw new InvalidVerificationTokenException(Constants.VERIFICATION_TOKEN_REQUIRED);
        }

        String accountId = extractAccountId(request);
        Optional<String> tokenAccountId = tokenStore.consume(token);
        if (tokenAccountId.isEmpty() || !tokenAccountId.get().equals(accountId)) {
            throw new InvalidVerificationTokenException(Constants.INVALID_OR_EXPIRED_TOKEN);
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private String extractAccountId(HttpServletRequest request) {
        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return pathVariables == null ? null : pathVariables.get("accountId");
    }
}

package com.example.demo.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Logback pattern converter (registered as {@code %mask} in logback-spring.xml) that masks any
 * {@code providedPin=1234} / {@code "securityPin":"1234"} / {@code name: Alice} shaped value for a
 * fixed set of sensitive field names in the fully rendered log line - including generated DTOs'
 * {@code toString()} output (e.g. {@code name: Alice\n    age: 30\n    securityPin: 1234}), in
 * case one is ever logged whole via something like {@code log.debug("{}", request)}.
 *
 * <p>This is a defense-in-depth backstop, not a replacement for masking at the source
 * ({@code PinValidationAspect} already logs {@code providedPin=****}/{@code <missing>} directly).
 * It only catches values that are already present in the rendered text, so it's a no-op against
 * already-masked output - its job is to catch anything that reaches the logs without going
 * through that helper, now or in a future change.
 *
 * <p>Deliberately scoped to an explicit field-name allow-list ({@link #SENSITIVE_FIELDS}) rather
 * than a bare {@code pin} - {@code DemoDataSeeder} intentionally logs the demo accounts' PINs in
 * plain (as {@code pin=1234}) so they can be used immediately after startup, and a bare-"pin"
 * pattern would have masked that too.
 */
public class SensitiveDataMaskingConverter extends ClassicConverter {

    private static final Set<String> SENSITIVE_FIELDS = Set.of("securityPin", "providedPin", "name", "age");

    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)\\b(" + String.join("|", SENSITIVE_FIELDS) + ")(\"?\\s*[:=]\\s*\"?)([\\w.@-]{1,50})(\"?)");

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    private String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return SENSITIVE_FIELD_PATTERN.matcher(message).replaceAll("$1$2****$4");
    }
}

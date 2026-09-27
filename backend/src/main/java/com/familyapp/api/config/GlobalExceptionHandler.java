package com.familyapp.api.config;

import com.familyapp.application.i18n.MessageTranslator;
import com.familyapp.domain.i18n.LocalizedException;
import com.familyapp.domain.subscription.SubscriptionRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageTranslator translator;

    public GlobalExceptionHandler(MessageTranslator translator) {
        this.translator = translator;
    }

    /**
     * 402, not 403. The caller is authenticated and would be allowed to do this -- the
     * only thing in the way is an unpaid subscription, and a client should send them to
     * a paywall rather than show a permissions error.
     */
    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<Map<String, String>> handleSubscriptionRequired(SubscriptionRequiredException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("error", ex.getMessage()));
    }

    /** User-facing validation errors, rendered in the caller's language. */
    @ExceptionHandler(LocalizedException.class)
    public ResponseEntity<Map<String, String>> handleLocalized(LocalizedException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", translator.get(ex)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}

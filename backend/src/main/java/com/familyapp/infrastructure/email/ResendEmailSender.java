package com.familyapp.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends transactional mail through Resend.
 *
 * One method, because this app sends exactly one kind of e-mail. It stays a separate
 * class rather than living inside PasswordResetService so that swapping provider later
 * is one file, and so the reset logic can be tested without a network in sight.
 *
 * Without an API key it logs and does nothing rather than failing. That keeps local
 * development working without credentials -- but it is also why the caller must never
 * treat "no exception" as "the mail arrived".
 */
@Component
public class ResendEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient client = RestClient.create();
    private final String apiKey;
    private final String from;

    public ResendEmailSender(
            @Value("${kidquest.email.api-key:}") String apiKey,
            @Value("${kidquest.email.from:KidQuest <no-reply@kidquest.se>}") String from
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * @return true if the provider accepted it. False means it was not sent -- which the
     *   caller may legitimately ignore, since a failed reset e-mail must not tell the
     *   requester whether the address existed.
     */
    public boolean send(String to, String subject, String html) {
        if (!isConfigured()) {
            log.warn("RESEND_API_KEY is not set; not sending '{}'", subject);
            return false;
        }
        try {
            client.post()
                    .uri(ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", from, "to", new String[]{to}, "subject", subject, "html", html))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent '{}'", subject);
            return true;
        } catch (Exception e) {
            // Deliberately without the recipient: this line ends up in a log aggregator,
            // and "who asked to reset a password" is not something to leave lying around.
            log.error("Could not send '{}': {}", subject, e.getMessage());
            return false;
        }
    }
}

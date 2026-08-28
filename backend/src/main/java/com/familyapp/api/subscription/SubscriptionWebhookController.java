package com.familyapp.api.subscription;

import com.familyapp.application.subscription.RevenueCatEvent;
import com.familyapp.application.subscription.SubscriptionWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Where RevenueCat tells us what the stores did.
 *
 * Kept apart from SubscriptionController because the authentication is nothing like
 * the rest of the API: no device token, no member, no family. The caller is a machine
 * proving itself with a shared secret, and mixing that in with the device-token
 * endpoints is how a header check ends up applied to the wrong one.
 *
 * On status codes, which decide whether RevenueCat retries:
 *
 *   200  stored -- including events we deliberately do not act on. Stop sending it.
 *   401  the secret did not match. Retrying will not help, but a wrong secret is
 *        worth shouting about rather than silently accepting.
 *   400  the body is not a webhook we can identify. Retrying will not help.
 *   500  we broke. Please retry; the transaction rolled back, so it is safe to.
 *
 * The important one is that an event we stored but could not interpret still answers
 * 200. Answering an error there would have RevenueCat redeliver it for days while the
 * outcome never changes.
 */
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionWebhookController.class);

    private final SubscriptionWebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final String sharedSecret;

    public SubscriptionWebhookController(
            SubscriptionWebhookService webhookService,
            ObjectMapper objectMapper,
            @Value("${kidquest.subscription.webhook-secret:}") String sharedSecret
    ) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
    }

    @PostConstruct
    void warnIfUnconfigured() {
        if (sharedSecret.isEmpty()) {
            log.warn("KIDQUEST_SUBSCRIPTION_WEBHOOK_SECRET is not set; the subscription "
                    + "webhook will refuse every call. Purchases will not reach this database.");
        }
        if (webhookService.acceptsSandbox()) {
            log.warn("Subscription webhook is accepting SANDBOX events. Set "
                    + "kidquest.subscription.accept-sandbox=false before launch.");
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> receive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) String rawBody
    ) {
        // An unset secret must not mean "accept anything". Refusing everything is the
        // safe failure: no purchases get through, which is visible, rather than
        // anyone being able to grant themselves a subscription, which is not.
        if (sharedSecret.isEmpty()) {
            log.error("Subscription webhook called but no shared secret is configured; refusing");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Webhook not configured"));
        }
        if (!secretMatches(authorization)) {
            log.warn("Subscription webhook rejected: Authorization header did not match");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty body"));
        }

        RevenueCatEvent parsed;
        try {
            parsed = objectMapper.readValue(rawBody, RevenueCatEvent.class);
        } catch (Exception malformed) {
            // Logged without the body: it is unparseable, so it may be anything at
            // all, and webhook bodies carry store identifiers.
            log.error("Subscription webhook body could not be parsed: {}", malformed.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Malformed webhook body"));
        }
        if (parsed.event() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Webhook has no event"));
        }

        var outcome = webhookService.handle(parsed.event(), rawBody);
        return ResponseEntity.ok(Map.of("outcome", outcome.name().toLowerCase()));
    }

    /**
     * RevenueCat sends whatever value you configure as the Authorization header,
     * verbatim -- there is no signature to verify, so this is a bearer comparison.
     * Constant-time, because a timing oracle on a secret is free to avoid.
     */
    private boolean secretMatches(String authorization) {
        if (authorization == null) {
            return false;
        }
        var expected = sharedSecret.getBytes(StandardCharsets.UTF_8);
        var actual = authorization.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}

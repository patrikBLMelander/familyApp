package com.familyapp.api.family;

import com.familyapp.application.i18n.MessageTranslator;
import com.familyapp.application.passwordreset.PasswordResetService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Forgotten passwords. The only two endpoints in this app that need no authentication
 * of any kind -- by definition, since the caller cannot get in.
 *
 * Both answer the same way no matter what happened. The request endpoint cannot say
 * whether an address exists, and the confirm endpoint cannot say why a link failed.
 * That is not politeness: an endpoint that distinguishes them is a way to enumerate the
 * families using this app.
 */
@RestController
@RequestMapping("/api/v1/families/password-reset")
public class PasswordResetController {

    private final PasswordResetService service;
    private final MessageTranslator translator;

    public PasswordResetController(PasswordResetService service, MessageTranslator translator) {
        this.service = service;
        this.translator = translator;
    }

    /**
     * Always 200 with the same body, whether a mail was sent or the address is unknown.
     */
    @PostMapping("/request")
    public Map<String, String> request(
            @RequestBody RequestResetRequest body,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        // Mail language for a member with no saved language: the caller's language if it
        // named one we have, otherwise Swedish -- the web reset page predates i18n.
        var fallback = acceptLanguage != null ? LocaleContextHolder.getLocale().getLanguage() : "sv";
        service.request(body.email(), fallback);
        return Map.of(
                "message",
                translator.get("passwordReset.requested")
        );
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@RequestBody ConfirmResetRequest body) {
        service.confirm(body.token(), body.password());
        return Map.of("message", translator.get("passwordReset.done"));
    }

    public record RequestResetRequest(String email) {
    }

    public record ConfirmResetRequest(String token, String password) {
    }
}

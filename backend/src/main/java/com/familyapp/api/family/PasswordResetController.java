package com.familyapp.api.family;

import com.familyapp.application.passwordreset.PasswordResetService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    /**
     * Always 200 with the same body, whether a mail was sent or the address is unknown.
     */
    @PostMapping("/request")
    public Map<String, String> request(@RequestBody RequestResetRequest body) {
        service.request(body.email());
        return Map.of(
                "message",
                "Om adressen finns hos oss har vi skickat en återställningslänk."
        );
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@RequestBody ConfirmResetRequest body) {
        service.confirm(body.token(), body.password());
        return Map.of("message", "Lösenordet är uppdaterat.");
    }

    public record RequestResetRequest(String email) {
    }

    public record ConfirmResetRequest(String token, String password) {
    }
}

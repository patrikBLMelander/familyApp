package com.familyapp.application.passwordreset;

import java.util.Map;

/**
 * The reset mail in each app language. Kept as code rather than in messages*.properties
 * because it is HTML with three placeholders, which reads badly as a property value.
 */
final class PasswordResetEmail {

    record Texts(String subject, String heading, String greeting, String body, String button,
                 String notYou, String copyLink) {
    }

    private static final Map<String, Texts> TEXTS = Map.of(
            "sv", new Texts(
                    "Återställ ditt lösenord i KidQuest",
                    "Återställ ditt lösenord",
                    "Hej %s,",
                    "Någon har begärt ett nytt lösenord till ditt KidQuest-konto. Klicka på knappen nedan för att välja ett nytt. Länken gäller i en timme.",
                    "Välj nytt lösenord",
                    "Var det inte du? Då behöver du inte göra någonting — ditt nuvarande lösenord fortsätter att gälla.",
                    "Fungerar inte knappen? Kopiera den här länken:"),
            "en", new Texts(
                    "Reset your KidQuest password",
                    "Reset your password",
                    "Hi %s,",
                    "Someone asked for a new password for your KidQuest account. Click the button below to choose a new one. The link is valid for one hour.",
                    "Choose a new password",
                    "Wasn't you? Then you don't need to do anything — your current password keeps working.",
                    "Button not working? Copy this link:"),
            "de", new Texts(
                    "Setze dein KidQuest-Passwort zurück",
                    "Passwort zurücksetzen",
                    "Hallo %s,",
                    "Jemand hat ein neues Passwort für dein KidQuest-Konto angefordert. Klicke auf den Button unten, um ein neues zu wählen. Der Link ist eine Stunde lang gültig.",
                    "Neues Passwort wählen",
                    "Das warst du nicht? Dann musst du nichts tun — dein aktuelles Passwort gilt weiter.",
                    "Der Button funktioniert nicht? Kopiere diesen Link:"),
            "es", new Texts(
                    "Restablece tu contraseña de KidQuest",
                    "Restablece tu contraseña",
                    "Hola, %s:",
                    "Alguien ha pedido una contraseña nueva para tu cuenta de KidQuest. Pulsa el botón de abajo para elegir una nueva. El enlace es válido durante una hora.",
                    "Elegir contraseña nueva",
                    "¿No has sido tú? Entonces no tienes que hacer nada: tu contraseña actual sigue funcionando.",
                    "¿No funciona el botón? Copia este enlace:")
    );

    private PasswordResetEmail() {
    }

    /** Texts for sv/en/de/es; anything else gets English. */
    static Texts forLanguage(String language) {
        return TEXTS.getOrDefault(language, TEXTS.get("en"));
    }
}

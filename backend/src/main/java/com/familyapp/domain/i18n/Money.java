package com.familyapp.domain.i18n;

/**
 * A whole-unit amount in a currency, as a message argument. Formatted with the
 * caller's locale when the message is rendered (e.g. "120 kr", "120 €").
 */
public record Money(int amount, String currency) {
}

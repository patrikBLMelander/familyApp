package com.familyapp.domain.i18n;

import java.util.Arrays;

/**
 * A user-facing validation error, carried as a message code plus arguments.
 *
 * The API layer turns the code into text in the caller's language (see
 * messages*.properties). {@link #getMessage()} is the bare code, so logs and tests
 * stay language-independent.
 */
public class LocalizedException extends IllegalArgumentException {

    private final String code;
    private final transient Object[] args;

    public LocalizedException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }

    public String code() {
        return code;
    }

    public Object[] args() {
        return Arrays.copyOf(args, args.length);
    }
}

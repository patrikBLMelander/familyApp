package com.familyapp.domain.allowance;

/** Hur en automatisk utbetalning räknas ut. */
public enum AllowanceKind {
    /** Samma belopp varje vecka, på en vald veckodag. */
    WEEKLY,
    /** Samma belopp varje månad, på en vald dag 1-28. */
    MONTHLY,
    /** Beloppet för den XP-nivå barnet nådde under månaden som gick. */
    LEVEL
}

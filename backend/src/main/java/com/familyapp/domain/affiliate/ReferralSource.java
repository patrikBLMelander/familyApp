package com.familyapp.domain.affiliate;

/** How a family came to be attributed to an affiliate. */
public enum ReferralSource {
    /** A code typed into the app. */
    CODE,
    /** A cookie set from an affiliate link on the website. */
    WEB,
    /** Captured via the Play Install Referrer (Phase 4). */
    PLAY
}

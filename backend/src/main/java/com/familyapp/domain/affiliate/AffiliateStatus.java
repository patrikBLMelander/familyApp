package com.familyapp.domain.affiliate;

/** Whether an affiliate can log in and earn. */
public enum AffiliateStatus {
    /** Created by an admin; has no password yet. Activation sets one. */
    INVITED,
    /** Password set; can log in and earn commission. */
    ACTIVE,
    /** Temporarily suspended: no new commission, cannot log in. */
    PAUSED
}

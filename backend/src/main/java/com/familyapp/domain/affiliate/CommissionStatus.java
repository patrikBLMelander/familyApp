package com.familyapp.domain.affiliate;

/** Lifecycle of one affiliate commission row. */
public enum CommissionStatus {
    /** Accrued from a paid period; not yet clearing-checked. */
    PENDING,
    /** Past the clearance window with no refund; payable. */
    APPROVED,
    /** Included in a payout that was marked paid. */
    PAID,
    /** The underlying period was refunded; the commission is reversed. */
    CLAWED_BACK
}

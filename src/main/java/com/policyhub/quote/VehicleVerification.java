package com.policyhub.quote;

/** How the vehicle details on a quote were confirmed. Kept on the quote for underwriting and audit. */
public enum VehicleVerification {
    /** Confirmed live by the vehicle registry API. */
    VERIFIED_ONLINE,
    /** Registry unavailable; confirmed against the nightly imported copy. */
    VERIFIED_OFFLINE,
    /** No source knew the plate; the declared details were used as given. */
    UNVERIFIED
}

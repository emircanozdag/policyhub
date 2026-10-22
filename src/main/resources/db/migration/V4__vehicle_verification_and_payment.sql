-- How the vehicle on a quote was confirmed (live registry API, nightly copy, or not at all)
ALTER TABLE quote ADD (
    vehicle_verification VARCHAR2(20) DEFAULT 'UNVERIFIED' NOT NULL,
    CONSTRAINT ck_quote_vehicle_verification
        CHECK (vehicle_verification IN ('VERIFIED_ONLINE', 'VERIFIED_OFFLINE', 'UNVERIFIED'))
);

-- Payment id returned by the virtual POS provider for the collected premium.
-- Nullable because policies issued before payment integration have none.
ALTER TABLE policy ADD (
    payment_reference VARCHAR2(64)
);

-- Vehicle registry / kasko value list, loaded nightly from the mainframe extract (see docs/mainframe/VEHREC.cpy)
CREATE TABLE vehicle (
    plate_number        VARCHAR2(16)    PRIMARY KEY,
    chassis_number      VARCHAR2(17)    NOT NULL,
    brand               VARCHAR2(15)    NOT NULL,
    model               VARCHAR2(20)    NOT NULL,
    model_year          NUMBER(4)       NOT NULL,
    kasko_value         NUMBER(13, 2)   NOT NULL,
    status              VARCHAR2(10)    NOT NULL,
    source_file_date    DATE            NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    CONSTRAINT ck_vehicle_status CHECK (status IN ('ACTIVE', 'SCRAPPED'))
);

-- A renewal quote points at the policy it renews. The unique constraint guarantees at most one
-- renewal quote per policy, which keeps the renewal batch idempotent across reruns and restarts.
ALTER TABLE quote ADD (
    renewal_of_policy_id NUMBER(19),
    CONSTRAINT uk_quote_renewal_of_policy UNIQUE (renewal_of_policy_id),
    CONSTRAINT fk_quote_renewal_policy FOREIGN KEY (renewal_of_policy_id) REFERENCES policy (id)
);

-- OSV Vulnerability DB Schema (H2-compatible)
-- Mirrors the OSV schema v1.6: https://ossf.github.io/osv-schema/

CREATE TABLE IF NOT EXISTS osv_vulnerability (
    id             VARCHAR(255) NOT NULL,
    schema_version VARCHAR(50),
    published      TIMESTAMP,
    modified       TIMESTAMP,
    withdrawn      TIMESTAMP,
    summary        VARCHAR(2000),
    details        CLOB,
    CONSTRAINT pk_osv_vulnerability PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS osv_vulnerability_alias (
    vulnerability_id VARCHAR(255) NOT NULL,
    alias            VARCHAR(255) NOT NULL,
    CONSTRAINT fk_alias_vuln FOREIGN KEY (vulnerability_id)
        REFERENCES osv_vulnerability (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_vulnerability_related (
    vulnerability_id VARCHAR(255) NOT NULL,
    related_id       VARCHAR(255) NOT NULL,
    CONSTRAINT fk_related_vuln FOREIGN KEY (vulnerability_id)
        REFERENCES osv_vulnerability (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_reference (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    vulnerability_id VARCHAR(255) NOT NULL,
    type             VARCHAR(50),
    url              VARCHAR(2048),
    CONSTRAINT pk_osv_reference PRIMARY KEY (id),
    CONSTRAINT fk_reference_vuln FOREIGN KEY (vulnerability_id)
        REFERENCES osv_vulnerability (id) ON DELETE CASCADE
);

-- OsvAffected embeds OsvPackage (1:1 relationship, inlined as columns)
CREATE TABLE IF NOT EXISTS osv_affected (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    vulnerability_id  VARCHAR(255) NOT NULL,
    package_name      VARCHAR(500),
    package_ecosystem VARCHAR(100),
    package_purl      VARCHAR(1000),
    CONSTRAINT pk_osv_affected PRIMARY KEY (id),
    CONSTRAINT fk_affected_vuln FOREIGN KEY (vulnerability_id)
        REFERENCES osv_vulnerability (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_affected_version (
    affected_id BIGINT       NOT NULL,
    version     VARCHAR(255) NOT NULL,
    CONSTRAINT fk_affected_version FOREIGN KEY (affected_id)
        REFERENCES osv_affected (id) ON DELETE CASCADE
);

-- OsvSeverity belongs to either a vulnerability or an affected entry
CREATE TABLE IF NOT EXISTS osv_severity (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    vulnerability_id VARCHAR(255),
    affected_id      BIGINT,
    type             VARCHAR(50),
    score            VARCHAR(100),
    CONSTRAINT pk_osv_severity PRIMARY KEY (id),
    CONSTRAINT fk_severity_vuln FOREIGN KEY (vulnerability_id)
        REFERENCES osv_vulnerability (id) ON DELETE CASCADE,
    CONSTRAINT fk_severity_affected FOREIGN KEY (affected_id)
        REFERENCES osv_affected (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_range (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    affected_id BIGINT       NOT NULL,
    type        VARCHAR(50),
    repo        VARCHAR(2048),
    CONSTRAINT pk_osv_range PRIMARY KEY (id),
    CONSTRAINT fk_range_affected FOREIGN KEY (affected_id)
        REFERENCES osv_affected (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    range_id       BIGINT       NOT NULL,
    introduced     VARCHAR(255),
    fixed          VARCHAR(255),
    limit_version  VARCHAR(255),
    last_affected  VARCHAR(255),
    CONSTRAINT pk_osv_event PRIMARY KEY (id),
    CONSTRAINT fk_event_range FOREIGN KEY (range_id)
        REFERENCES osv_range (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_credit (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    vulnerability_id VARCHAR(255) NOT NULL,
    name             VARCHAR(500),
    type             VARCHAR(50),
    CONSTRAINT pk_osv_credit PRIMARY KEY (id),
    CONSTRAINT fk_credit_vuln FOREIGN KEY (vulnerability_id)
        REFERENCES osv_vulnerability (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS osv_credit_contact (
    credit_id BIGINT       NOT NULL,
    contact   VARCHAR(500) NOT NULL,
    CONSTRAINT fk_contact_credit FOREIGN KEY (credit_id)
        REFERENCES osv_credit (id) ON DELETE CASCADE
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_vuln_modified         ON osv_vulnerability (modified);
CREATE INDEX IF NOT EXISTS idx_affected_vuln         ON osv_affected (vulnerability_id);
CREATE INDEX IF NOT EXISTS idx_affected_pkg          ON osv_affected (package_ecosystem, package_name);
CREATE INDEX IF NOT EXISTS idx_severity_vuln         ON osv_severity (vulnerability_id);
CREATE INDEX IF NOT EXISTS idx_severity_affected     ON osv_severity (affected_id);
CREATE INDEX IF NOT EXISTS idx_range_affected        ON osv_range (affected_id);
CREATE INDEX IF NOT EXISTS idx_event_range           ON osv_event (range_id);
CREATE INDEX IF NOT EXISTS idx_reference_vuln        ON osv_reference (vulnerability_id);
CREATE INDEX IF NOT EXISTS idx_credit_vuln           ON osv_credit (vulnerability_id);
CREATE INDEX IF NOT EXISTS idx_alias_vuln            ON osv_vulnerability_alias (vulnerability_id);
CREATE INDEX IF NOT EXISTS idx_related_vuln          ON osv_vulnerability_related (vulnerability_id);

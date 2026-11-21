-- V1: full MVP schema. Later days add seed data / new tables in new migrations;
-- never edit this file once it has been applied anywhere.
-- Money: DECIMAL(15,2). Timestamps: DATETIME(6), always written in UTC.
-- Enum-like columns are VARCHAR + CHECK (not MySQL ENUM) so adding a value is a cheap migration.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE statement_uploads (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename   VARCHAR(64)  NOT NULL,
    file_type         VARCHAR(8)   NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    bank_code         VARCHAR(16)  NULL,
    account_last4     VARCHAR(4)   NULL,
    rows_total        INT          NOT NULL DEFAULT 0,
    rows_inserted     INT          NOT NULL DEFAULT 0,
    rows_duplicate    INT          NOT NULL DEFAULT 0,
    rows_failed       INT          NOT NULL DEFAULT 0,
    error_summary     JSON         NULL,
    failure_reason    VARCHAR(64)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    started_at        DATETIME(6)  NULL,
    completed_at      DATETIME(6)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_uploads_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_uploads_status CHECK (status IN
        ('PENDING', 'PARSING', 'CATEGORISING', 'DONE', 'DONE_WITH_WARNINGS', 'FAILED')),
    CONSTRAINT chk_uploads_file_type CHECK (file_type IN ('CSV', 'XLS', 'XLSX')),
    INDEX idx_uploads_user_created (user_id, created_at),
    INDEX idx_uploads_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE transactions (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    user_id              BIGINT        NOT NULL,
    upload_id            BIGINT        NOT NULL,
    bank_code            VARCHAR(16)   NOT NULL,
    account_last4        VARCHAR(4)    NULL,
    txn_date             DATE          NOT NULL,
    value_date           DATE          NULL,
    amount               DECIMAL(15,2) NOT NULL,
    direction            VARCHAR(6)    NOT NULL,
    balance_after        DECIMAL(15,2) NULL,
    raw_narration        VARCHAR(512)  NOT NULL,
    normalized_narration VARCHAR(512)  NOT NULL,
    channel              VARCHAR(16)   NOT NULL DEFAULT 'OTHER',
    merchant_key         VARCHAR(128)  NULL,
    vpa                  VARCHAR(128)  NULL,
    category             VARCHAR(32)   NOT NULL DEFAULT 'UNCATEGORISED',
    category_source      VARCHAR(16)   NOT NULL DEFAULT 'NONE',
    txn_hash             CHAR(64)      NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transactions_user_hash UNIQUE (user_id, txn_hash),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_upload FOREIGN KEY (upload_id) REFERENCES statement_uploads (id),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_transactions_source CHECK (category_source IN
        ('USER_RULE', 'DICTIONARY', 'KEYWORD', 'CACHE', 'LLM', 'NONE')),
    INDEX idx_transactions_user_date (user_id, txn_date),
    INDEX idx_transactions_user_merchant (user_id, merchant_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE merchant_dictionary (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    merchant_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    category     VARCHAR(32)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_merchant_dictionary_key UNIQUE (merchant_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE merchant_category_cache (
    merchant_key VARCHAR(128) NOT NULL,
    category     VARCHAR(32)  NOT NULL,
    source       VARCHAR(16)  NOT NULL DEFAULT 'LLM',
    model        VARCHAR(64)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (merchant_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_merchant_rules (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    merchant_key VARCHAR(128) NOT NULL,
    category     VARCHAR(32)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_merchant_rules UNIQUE (user_id, merchant_key),
    CONSTRAINT fk_rules_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

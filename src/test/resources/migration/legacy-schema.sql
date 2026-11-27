-- Hibernate 7.2.7.Final with ddl-auto=update on empty MySQL 8.4, before Flyway.
CREATE TABLE conversation_sessions (
    id binary(16) NOT NULL,
    expires_at datetime(6) NOT NULL,
    last_category tinyint CHECK ((last_category BETWEEN 0 AND 2)),
    last_period varchar(255),
    pending_amount_in_cents bigint,
    pending_category enum ('AUTO','GROCERIES','PHARMA'),
    pending_confirmation_token binary(16),
    pending_description varchar(255),
    pending_intent varchar(255),
    pending_period varchar(255),
    user_id binary(16) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE transaction_entity (
    id binary(16) NOT NULL,
    amount bigint NOT NULL,
    category enum ('AUTO','GROCERIES','PHARMA'),
    created_at datetime(6),
    description varchar(255),
    source enum ('AI','MANUAL'),
    user_id binary(16) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

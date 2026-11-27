CREATE TABLE conversation_exchanges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BINARY(16) NOT NULL,
    question LONGTEXT NOT NULL,
    answer LONGTEXT NOT NULL,
    exchanged_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_conversation_exchanges_session
        FOREIGN KEY (conversation_id) REFERENCES conversation_sessions (id)
);

CREATE INDEX idx_conversation_exchanges_order
    ON conversation_exchanges (conversation_id, exchanged_at, id);

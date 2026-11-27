-- Spring AI 2.0.0-M4, org/springframework/ai/chat/memory/repository/jdbc/schema-mysql.sql.
-- The application uses two 36-character UUIDs separated by a colon.
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    `conversation_id` VARCHAR(73) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') NOT NULL,
    `timestamp` TIMESTAMP NOT NULL,

    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

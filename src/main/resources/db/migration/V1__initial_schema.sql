CREATE TABLE `conversation_sessions` (
  `id` binary(16) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_category` tinyint DEFAULT NULL,
  `last_period` varchar(255) DEFAULT NULL,
  `pending_amount_in_cents` bigint DEFAULT NULL,
  `pending_category` enum('AUTO','GROCERIES','PHARMA') DEFAULT NULL,
  `pending_confirmation_token` binary(16) DEFAULT NULL,
  `pending_description` varchar(255) DEFAULT NULL,
  `pending_intent` varchar(255) DEFAULT NULL,
  `pending_period` varchar(255) DEFAULT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `conversation_sessions_chk_1` CHECK ((`last_category` between 0 and 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `transaction_entity` (
  `id` binary(16) NOT NULL,
  `amount` bigint NOT NULL,
  `category` enum('AUTO','GROCERIES','PHARMA') DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `source` enum('AI','MANUAL') DEFAULT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


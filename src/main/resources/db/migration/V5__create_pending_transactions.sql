CREATE TABLE `pending_transactions` (
  `token` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `description` varchar(255) NOT NULL,
  `amount_in_cents` bigint NOT NULL,
  `category` enum('AUTO','GROCERIES','PHARMA') NOT NULL,
  `type` enum('EXPENSE','INCOME') NOT NULL,
  `occurred_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`token`),
  CONSTRAINT `fk_pending_transactions_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `transaction_entity`
    ADD CONSTRAINT `fk_transaction_entity_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
    ON DELETE RESTRICT;

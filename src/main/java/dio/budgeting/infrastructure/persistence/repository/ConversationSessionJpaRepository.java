package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.ConversationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationSessionJpaRepository
        extends JpaRepository<ConversationSessionEntity, UUID> {
}
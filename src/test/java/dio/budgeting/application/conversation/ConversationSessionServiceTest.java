package dio.budgeting.application.conversation;

import dio.budgeting.application.conversation.ConversationSession;
import dio.budgeting.application.conversation.ConversationSessionService;
import dio.budgeting.application.conversation.SessionAccessDeniedException;
import dio.budgeting.infrastructure.persistence.entity.ConversationSessionEntity;
import dio.budgeting.infrastructure.persistence.repository.ConversationSessionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationSessionServiceTest {

    private static final Instant TEST_INSTANT = Instant.parse("2026-11-23T15:00:00Z");

    private ConversationSessionJpaRepository repository;
    private ConversationSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationSessionJpaRepository.class);
        service = new ConversationSessionService(
                repository,
                Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        );
    }

    @Test
    void shouldAllowOwnerToAccessSession() {
        var userId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();

        var entity = new ConversationSessionEntity(
                sessionId,
                userId,
                TEST_INSTANT.plusSeconds(900)
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(entity));

        var session = service.getOrCreate(sessionId, userId);

        assertEquals(sessionId, session.id());
        assertEquals(userId, session.userId());
        verify(repository).save(entity);
    }

    @Test
    void shouldRejectAnotherUserWithoutSavingSession() {
        var ownerId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var expiration = TEST_INSTANT.plusSeconds(900);

        var entity = new ConversationSessionEntity(
                sessionId,
                ownerId,
                expiration
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(entity));

        assertThrows(
                SessionAccessDeniedException.class,
                () -> service.getOrCreate(sessionId, otherUserId)
        );

        assertEquals(expiration, entity.getExpiresAt());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateNewIdWhenOwnerSessionHasExpired() {
        var userId = UUID.randomUUID();
        var oldSessionId = UUID.randomUUID();

        var entity = new ConversationSessionEntity(
                oldSessionId,
                userId,
                TEST_INSTANT.minusSeconds(60)
        );

        when(repository.findById(oldSessionId))
                .thenReturn(Optional.of(entity));

        var session = service.getOrCreate(oldSessionId, userId);

        assertNotEquals(oldSessionId, session.id());
        assertEquals(userId, session.userId());
        verify(repository).save(any(ConversationSessionEntity.class));
    }

    @Test
    void shouldRejectAnotherUserEvenWhenSessionHasExpired() {
        var sessionId = UUID.randomUUID();

        var entity = new ConversationSessionEntity(
                sessionId,
                UUID.randomUUID(),
                TEST_INSTANT.minusSeconds(60)
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(entity));

        assertThrows(
                SessionAccessDeniedException.class,
                () -> service.getOrCreate(
                        sessionId,
                        UUID.randomUUID()
                )
        );

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectSavingSessionOwnedByAnotherUser() {
        var sessionId = UUID.randomUUID();

        var entity = new ConversationSessionEntity(
                sessionId,
                UUID.randomUUID(),
                TEST_INSTANT.plusSeconds(900)
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(entity));

        var unauthorizedSession = new ConversationSession(
                sessionId,
                UUID.randomUUID(),
                Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        );

        assertThrows(
                SessionAccessDeniedException.class,
                () -> service.save(unauthorizedSession)
        );

        verify(repository, never()).save(any());
    }
}
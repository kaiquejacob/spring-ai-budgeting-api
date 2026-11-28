package dio.budgeting.application.conversation;

import dio.budgeting.application.conversation.ConversationSessionStore.StoredSession;
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

    private ConversationSessionStore repository;
    private ConversationSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationSessionStore.class);
        service = new ConversationSessionService(
                repository,
                Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        );
    }

    @Test
    void shouldAllowOwnerToAccessSession() {
        var userId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();

        var stored = new StoredSession(
                sessionId,
                userId,
                TEST_INSTANT.plusSeconds(900), null, null, null, null
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(stored));

        var session = service.getOrCreate(sessionId, userId);

        assertEquals(sessionId, session.id());
        assertEquals(userId, session.userId());
        verify(repository).save(stored);
    }

    @Test
    void shouldRejectAnotherUserWithoutSavingSession() {
        var ownerId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var expiration = TEST_INSTANT.plusSeconds(900);

        var stored = new StoredSession(
                sessionId,
                ownerId,
                expiration, null, null, null, null
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(stored));

        assertThrows(
                SessionAccessDeniedException.class,
                () -> service.getOrCreate(sessionId, otherUserId)
        );

        assertEquals(expiration, stored.expiresAt());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateNewIdWhenOwnerSessionHasExpired() {
        var userId = UUID.randomUUID();
        var oldSessionId = UUID.randomUUID();

        var stored = new StoredSession(
                oldSessionId,
                userId,
                TEST_INSTANT.minusSeconds(60), null, null, null, null
        );

        when(repository.findById(oldSessionId))
                .thenReturn(Optional.of(stored));

        var session = service.getOrCreate(oldSessionId, userId);

        assertNotEquals(oldSessionId, session.id());
        assertEquals(userId, session.userId());
        verify(repository).save(any(StoredSession.class));
    }

    @Test
    void shouldRejectAnotherUserEvenWhenSessionHasExpired() {
        var sessionId = UUID.randomUUID();

        var stored = new StoredSession(
                sessionId,
                UUID.randomUUID(),
                TEST_INSTANT.minusSeconds(60), null, null, null, null
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(stored));

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

        var stored = new StoredSession(
                sessionId,
                UUID.randomUUID(),
                TEST_INSTANT.plusSeconds(900), null, null, null, null
        );

        when(repository.findById(sessionId))
                .thenReturn(Optional.of(stored));

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
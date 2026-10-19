package dio.budgeting.application;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationSessionService {

    private final Map<UUID, ConversationSession> sessions =
            new ConcurrentHashMap<>();

    public ConversationSession getOrCreate(UUID sessionId) {
        if (sessionId == null) {
            var session = new ConversationSession(UUID.randomUUID());
            sessions.put(session.id(), session);
            return session;
        }

        var session = sessions.get(sessionId);

        if (session == null || session.isExpired()) {
            session = new ConversationSession(sessionId);
            sessions.put(sessionId, session);
        }

        session.renew();
        return session;
    }

    public void remove(UUID sessionId) {
        sessions.remove(sessionId);
    }
}
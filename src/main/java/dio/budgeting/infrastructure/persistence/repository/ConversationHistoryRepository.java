package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.application.conversation.ConversationHistoryWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Repository
public class ConversationHistoryRepository implements ConversationHistoryWriter {

    private final JdbcClient jdbc;

    public ConversationHistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(UUID conversationId, String question, String answer, Instant exchangedAt) {
        jdbc.sql("""
                INSERT INTO conversation_exchanges (conversation_id, question, answer, exchanged_at)
                VALUES (:conversationId, :question, :answer, :exchangedAt)
                """)
                .param("conversationId", bytes(conversationId))
                .param("question", question)
                .param("answer", answer)
                .param("exchangedAt", LocalDateTime.ofInstant(exchangedAt.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC))
                .update();
    }

    public List<Summary> list(UUID ownerId, long offset, int limit) {
        return jdbc.sql("""
                SELECT s.id AS conversation_id,
                       (SELECT SUBSTRING(first_exchange.question, 1, 80)
                        FROM conversation_exchanges first_exchange
                        WHERE first_exchange.conversation_id = s.id
                        ORDER BY first_exchange.exchanged_at, first_exchange.id
                        LIMIT 1) AS preview,
                       MAX(e.exchanged_at) AS last_exchange_at
                FROM conversation_sessions s
                JOIN conversation_exchanges e ON e.conversation_id = s.id
                WHERE s.user_id = :ownerId
                GROUP BY s.id
                ORDER BY last_exchange_at DESC, s.id
                LIMIT :limit OFFSET :offset
                """)
                .param("ownerId", bytes(ownerId))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, row) -> {
                    var id = ByteBuffer.wrap(rs.getBytes("conversation_id"));
                    return new Summary(new UUID(id.getLong(), id.getLong()), rs.getString("preview"),
                            rs.getObject("last_exchange_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
                })
                .list();
    }

    public boolean belongsTo(UUID conversationId, UUID ownerId) {
        return jdbc.sql("SELECT 1 FROM conversation_sessions WHERE id = :conversationId AND user_id = :ownerId")
                .param("conversationId", bytes(conversationId))
                .param("ownerId", bytes(ownerId))
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public List<Exchange> messages(UUID conversationId, UUID ownerId, long offset, int limit) {
        return jdbc.sql("""
                SELECT e.question, e.answer, e.exchanged_at
                FROM conversation_exchanges e
                JOIN conversation_sessions s ON s.id = e.conversation_id
                WHERE s.id = :conversationId AND s.user_id = :ownerId
                ORDER BY e.exchanged_at, e.id
                LIMIT :limit OFFSET :offset
                """)
                .param("conversationId", bytes(conversationId))
                .param("ownerId", bytes(ownerId))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, row) -> new Exchange(rs.getString("question"), rs.getString("answer"),
                        rs.getObject("exchanged_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .list();
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    public record Summary(UUID conversationId, String preview, Instant lastExchangeAt) {}

    public record Exchange(String question, String answer, Instant exchangedAt) {}
}

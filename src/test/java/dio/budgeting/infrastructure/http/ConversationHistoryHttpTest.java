package dio.budgeting.infrastructure.http;

import dio.budgeting.application.conversation.ConversationSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static dio.budgeting.infrastructure.http.AuthenticatedTestSession.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:conversation-history;MODE=MySQL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/db/migration/V7__create_conversation_exchanges.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ConversationHistoryHttpTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConversationSessionService sessions;

    @Test
    void requiresAuthenticationAndReturnsAnEmptyListWithoutTextHistory() throws Exception {
        mvc.perform(get("/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/conversations").with(authenticated(UUID.randomUUID().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void listsOnlyTextConversationsByLastExchangeAndPaginates() throws Exception {
        var older = sessions.getOrCreate(null, OWNER).id();
        var newer = sessions.getOrCreate(null, OWNER).id();
        var foreign = sessions.getOrCreate(null, OTHER).id();
        sessions.getOrCreate(null, OWNER);
        var firstQuestion = "a".repeat(90);
        addExchange(newer, "ultima pergunta", "2026-11-27T16:00:00");
        addExchange(newer, firstQuestion, "2026-11-27T14:00:00");
        addExchange(older, "conversa anterior", "2026-11-27T15:00:00");
        addExchange(foreign, "conversa alheia", "2026-11-27T17:00:00");

        mvc.perform(get("/conversations?size=1").with(authenticated(OWNER.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].conversationId").value(newer.toString()))
                .andExpect(jsonPath("$.items[0].preview").value(firstQuestion.substring(0, 80)))
                .andExpect(jsonPath("$.items[0].lastExchangeAt").value("2026-11-27T16:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/conversations?page=1&size=1").with(authenticated(OWNER.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].conversationId").value(older.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void readsExpiredHistoryInStableOrderWithoutRenewingTheSession() throws Exception {
        var id = sessions.getOrCreate(null, OWNER).id();
        jdbc.update("UPDATE conversation_sessions SET expires_at = '2000-01-01 00:00:00' WHERE id = ?", bytes(id));
        var expiration = jdbc.queryForMap("SELECT expires_at FROM conversation_sessions WHERE id = ?", bytes(id));
        addExchange(id, "posterior", "2026-11-27T16:00:00");
        addExchange(id, "primeira", "2026-11-27T15:00:00");
        addExchange(id, "segunda", "2026-11-27T15:00:00");

        var path = "/conversations/" + id + "/messages";
        mvc.perform(get(path + "?size=1").with(authenticated(OWNER.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].question").value("primeira"))
                .andExpect(jsonPath("$.items[0].answer").value("Resposta simulada"))
                .andExpect(jsonPath("$.items[0].exchangedAt").value("2026-11-27T15:00:00Z"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(path + "?page=1&size=1").with(authenticated(OWNER.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].question").value("segunda"));
        mvc.perform(get(path).with(authenticated(OTHER.toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/conversations/" + UUID.randomUUID() + "/messages").with(authenticated(OWNER.toString())))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForMap("SELECT expires_at FROM conversation_sessions WHERE id = ?", bytes(id)))
                .isEqualTo(expiration);
        assertThat(sessions.getOrCreate(id, OWNER).id()).isNotEqualTo(id);
    }

    @Test
    void rejectsPaginationOutsideTheAllowedBounds() throws Exception {
        mvc.perform(get("/conversations?page=-1").with(authenticated(OWNER.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/conversations/" + UUID.randomUUID() + "/messages?size=101")
                        .with(authenticated(OWNER.toString())))
                .andExpect(status().isBadRequest());
    }

    private void addExchange(UUID id, String question, String exchangedAt) {
        jdbc.update("""
                INSERT INTO conversation_exchanges (conversation_id, question, answer, exchanged_at)
                VALUES (?, ?, ?, ?)
                """, bytes(id), question, "Resposta simulada", LocalDateTime.parse(exchangedAt));
    }

    private static byte[] bytes(UUID id) {
        return HexFormat.of().parseHex(id.toString().replace("-", ""));
    }
}

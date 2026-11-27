package dio.budgeting.migration;

import dio.budgeting.BudgetingApplication;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.conversation.ConversationSessionService;
import dio.budgeting.application.conversation.FinancialChatUseCase;
import dio.budgeting.application.conversation.SessionAccessDeniedException;
import dio.budgeting.application.proposal.ConfirmPendingTransactionUseCase;
import dio.budgeting.application.proposal.PendingTransactionException;
import dio.budgeting.application.proposal.RegisterPendingProposalUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.TransactionType;
import dio.budgeting.infrastructure.persistence.repository.ConversationHistoryRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("mysql-migration")
@Testcontainers
class MySqlMigrationTest {

    private static final String V1 = "V1__initial_schema.sql";
    private static final String V2 = "V2__add_transaction_type_and_occurred_at.sql";
    private static final String V3 = "V3__create_users.sql";
    private static final String V4 = "V4__add_transaction_user_foreign_key.sql";
    private static final String V5 = "V5__create_pending_transactions.sql";
    private static final String V6 = "V6__create_spring_ai_chat_memory.sql";
    private static final String V7 = "V7__create_conversation_exchanges.sql";
    private static final Instant CHAT_TIME = Instant.parse("2026-11-27T15:00:00.123456Z");
    private static final String USER_FK = "fk_transaction_entity_user";
    private static final String LEGACY_USER_ID = "00000000000000000000000000000010";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant OCCURRED_AT = Instant.parse("2025-01-02T03:00:00Z");
    private static final String INSERT_USER = """
            INSERT INTO users (id, email, password_hash, created_at)
            VALUES (UNHEX(?), ?, 'fixture-hash', '2025-01-02 03:04:05.123456')
            """;
    private static final String INSERT_TRANSACTION = """
            INSERT INTO transaction_entity (id, user_id, amount, type)
            VALUES (UNHEX(?), UNHEX(?), 100, 'EXPENSE')
            """;
    private static final String ORIGINAL_TRANSACTIONS = """
            SELECT id, amount, category, created_at, description, source, user_id
            FROM transaction_entity ORDER BY id
            """;

    @Container
    private final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Test
    void remembersChatInOrderAcrossRestartAndIsolatesConversations() {
        UUID sessionId;
        try (var context = startApplication(mysql, SimulatedChat.class)) {
            var result = context.getBean(FinancialChatUseCase.class).execute(OWNER, null, "pergunta inicial");
            assertThat(result.answer()).isEqualTo("resposta: pergunta inicial");
            sessionId = result.sessionId();
            var jdbc = new JdbcTemplate(dataSource(mysql));
            assertThat(jdbc.queryForList("""
                    SELECT question, answer FROM conversation_exchanges WHERE conversation_id = UNHEX(?)
                    """, sessionId.toString().replace("-", "")))
                    .containsExactly(java.util.Map.of("question", "pergunta inicial",
                            "answer", "resposta: pergunta inicial"));
        }

        try (var context = startApplication(mysql, SimulatedChat.class)) {
            var chat = context.getBean(FinancialChatUseCase.class);
            var prompts = context.getBean(CapturedPrompts.class).messages;
            chat.execute(OWNER, sessionId, "continuação");
            assertThat(prompts.getLast())
                    .containsExactly("pergunta inicial", "resposta: pergunta inicial", "continuação");

            chat.execute(OWNER, null, "nova sessão");
            assertThat(prompts.getLast()).containsExactly("nova sessão");
            var other = UUID.randomUUID();
            assertThatThrownBy(() -> chat.execute(other, sessionId, "sessão alheia"))
                    .isInstanceOf(SessionAccessDeniedException.class);
            assertThat(prompts).hasSize(2);
            chat.execute(other, null, "outra conta");
            assertThat(prompts.getLast()).containsExactly("outra conta");

            var expected = new ArrayList<String>();
            for (int turn = 1; turn <= 10; turn++) {
                var message = "janela " + turn;
                chat.execute(OWNER, sessionId, message);
                expected.add(message);
                expected.add("resposta: " + message);
            }
            var memory = context.getBean("financialChatMemory", ChatMemory.class);
            var conversationId = OWNER + ":" + sessionId;
            assertThat(memory.get(conversationId)).extracting(message -> message.getText())
                    .containsExactlyElementsOf(expected);
            assertThat(new JdbcTemplate(dataSource(mysql)).queryForObject("""
                    SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?
                    """, Long.class, conversationId)).isEqualTo(20L);
        }
        try (var context = startApplication(mysql, SimulatedChat.class)) {
            var history = context.getBean(ConversationHistoryRepository.class);
            assertThat(history.messages(sessionId, OWNER, 0, 20)).hasSize(12)
                    .first().isEqualTo(new ConversationHistoryRepository.Exchange(
                            "pergunta inicial", "resposta: pergunta inicial", CHAT_TIME));
            assertThat(history.messages(sessionId, OWNER, 2, 10))
                    .extracting(ConversationHistoryRepository.Exchange::question)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 10)
                            .mapToObj(turn -> "janela " + turn).toList());
            assertThat(history.list(OWNER, 0, 20)).hasSize(2)
                    .contains(new ConversationHistoryRepository.Summary(sessionId, "pergunta inicial", CHAT_TIME));

            var memory = context.getBean("financialChatMemory", ChatMemory.class);
            assertThat(memory.get(OWNER + ":" + sessionId)).extracting(message -> message.getText())
                    .doesNotContain("pergunta inicial", "resposta: pergunta inicial");
            var chat = context.getBean(FinancialChatUseCase.class);
            assertThatThrownBy(() -> chat.execute(OWNER, sessionId, "falha do modelo"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("provider unavailable");
            assertThat(history.messages(sessionId, OWNER, 0, 20)).hasSize(12);
        }
        System.out.println("History restart: preserved all 12 exchanges including the first evicted from model memory.");
        System.out.println("Chat restart: prior user and assistant messages restored in order; other sessions and accounts isolated; window retained exactly 20 messages.");
    }

    private static class CapturedPrompts {
        private final List<List<String>> messages = new ArrayList<>();
    }

    @TestConfiguration
    static class SimulatedChat {
        @Bean
        @Primary
        Clock simulatedChatClock() {
            return Clock.fixed(CHAT_TIME, ZoneOffset.UTC);
        }

        @Bean
        CapturedPrompts capturedPrompts() {
            return new CapturedPrompts();
        }

        @Bean
        @Primary
        ChatClient.Builder simulatedChatBuilder(CapturedPrompts capture) {
            var model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                var messages = prompt.getInstructions().stream()
                        .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                        .map(message -> message.getText()).toList();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive()).isFalse();
                if (messages.getLast().equals("falha do modelo")) {
                    throw new IllegalStateException("provider unavailable");
                }
                capture.messages.add(messages);
                return new ChatResponse(List.of(new Generation(
                        new AssistantMessage("resposta: " + messages.getLast()))));
            });
            return ChatClient.builder(model);
        }
    }

    @Test
    void confirmsPersistedProposalAfterApplicationRestart() {
        var jdbc = new JdbcTemplate(dataSource(mysql));
        UUID token;
        UUID sessionId;
        try (var context = startApplication(mysql)) {
            jdbc.update(INSERT_USER, LEGACY_USER_ID, "proposal-owner@example.invalid");
            var session = context.getBean(ConversationSessionService.class).getOrCreate(null, OWNER);
            sessionId = session.id();
            token = context.getBean(RegisterPendingProposalUseCase.class).execute(OWNER, proposal(), session);
            assertThat(transactionCount(jdbc)).isZero();
        }

        try (var context = startApplication(mysql)) {
            var session = context.getBean(ConversationSessionService.class).getOrCreate(sessionId, OWNER);
            assertThat(session.pendingConfirmationToken()).isEqualTo(token);
            assertThat(session.pendingProposal().type()).isNull();
            var confirmed = context.getBean(ConfirmPendingTransactionUseCase.class).execute(token, OWNER);
            assertThat(confirmed.type()).isEqualTo(TransactionType.INCOME);
            assertThat(confirmed.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(transactionCount(jdbc)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM transaction_entity
                    WHERE type = 'INCOME' AND occurred_at = '2025-01-02 03:00:00'
                    """, Long.class)).isEqualTo(1L);
        }
        System.out.println("Proposal restart: no transaction before confirmation; reloaded token retained INCOME and occurrence.");
    }

    @Test
    void onlyOneConcurrentConfirmationWinsAndInvalidTokensDoNotConsumeOwnerProposal() throws Exception {
        var jdbc = new JdbcTemplate(dataSource(mysql));
        try (var context = startApplication(mysql)) {
            jdbc.update(INSERT_USER, LEGACY_USER_ID, "proposal-owner@example.invalid");
            var other = UUID.fromString("00000000-0000-0000-0000-000000000011");
            jdbc.update(INSERT_USER, "00000000000000000000000000000011", "other-owner@example.invalid");
            var sessions = context.getBean(ConversationSessionService.class);
            var register = context.getBean(RegisterPendingProposalUseCase.class);
            var confirm = context.getBean(ConfirmPendingTransactionUseCase.class);
            var session = sessions.getOrCreate(null, OWNER);
            var expired = register.execute(OWNER, proposal(), session);
            jdbc.update("UPDATE pending_transactions SET expires_at = '2000-01-01 00:00:00' WHERE token = UNHEX(?)",
                    expired.toString().replace("-", ""));
            assertThatThrownBy(() -> confirm.execute(expired, OWNER))
                    .isInstanceOf(PendingTransactionException.class).hasMessage("Proposta expirada.");
            var token = register.execute(OWNER, proposal(), session);
            assertThatThrownBy(() -> confirm.execute(token, other)).isInstanceOf(PendingTransactionException.class);
            assertThat(transactionCount(jdbc)).isZero();

            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> attemptConfirmation(confirm, token, ready, start));
                var second = workers.submit(() -> attemptConfirmation(confirm, token, ready, start));
                try {
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                } finally {
                    start.countDown();
                }
                assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            }
            assertThat(transactionCount(jdbc)).isEqualTo(1L);
            assertThatThrownBy(() -> confirm.execute(token, OWNER)).isInstanceOf(PendingTransactionException.class);
        }
        System.out.println("Proposal concurrency: expired and foreign attempts rejected; two independent confirmations yielded one transaction.");
    }

    @Test
    void rollsBackTokenWhenTransactionOrSessionWriteFails() {
        var jdbc = new JdbcTemplate(dataSource(mysql));
        try (var context = startApplication(mysql)) {
            jdbc.update(INSERT_USER, LEGACY_USER_ID, "proposal-owner@example.invalid");
            var sessions = context.getBean(ConversationSessionService.class);
            var register = context.getBean(RegisterPendingProposalUseCase.class);
            var confirm = context.getBean(ConfirmPendingTransactionUseCase.class);
            var initialSession = sessions.getOrCreate(null, OWNER);
            jdbc.execute("ALTER TABLE conversation_sessions ADD CONSTRAINT fixture_session_failure CHECK (pending_confirmation_token IS NULL)");
            assertThatThrownBy(() -> register.execute(OWNER, proposal(), initialSession))
                    .hasStackTraceContaining("fixture_session_failure");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_transactions", Long.class)).isZero();
            var reloaded = sessions.getOrCreate(initialSession.id(), OWNER);
            assertThat(reloaded.pendingConfirmationToken()).isNull();
            jdbc.execute("ALTER TABLE conversation_sessions DROP CHECK fixture_session_failure");

            var token = register.execute(OWNER, proposal(), reloaded);
            jdbc.execute("ALTER TABLE transaction_entity ADD CONSTRAINT fixture_transaction_failure CHECK (amount <> 2590)");
            assertThatThrownBy(() -> confirm.execute(token, OWNER)).hasStackTraceContaining("fixture_transaction_failure");
            assertThat(transactionCount(jdbc)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_transactions", Long.class)).isEqualTo(1L);
            assertThat(sessions.getOrCreate(reloaded.id(), OWNER).pendingConfirmationToken()).isEqualTo(token);
            jdbc.execute("ALTER TABLE transaction_entity DROP CHECK fixture_transaction_failure");
            assertThat(confirm.execute(token, OWNER).type()).isEqualTo(TransactionType.INCOME);
            assertThat(transactionCount(jdbc)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_transactions", Long.class)).isZero();
        }
        System.out.println("Proposal rollback: session write failure left no proposal or reference; "
                + "transaction write failure left zero transactions and the same token succeeded on retry.");
    }

    private static boolean attemptConfirmation(ConfirmPendingTransactionUseCase confirm, UUID token,
                                               CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent confirmation");
        }
        try {
            confirm.execute(token, OWNER);
            return true;
        } catch (PendingTransactionException exception) {
            return false;
        }
    }

    private static TransactionAiResponse proposal() {
        return new TransactionAiResponse(TransactionIntent.CREATE_TRANSACTION, "Refund", 2590L,
                Category.GROCERIES, null, null, null, TransactionType.INCOME, OCCURRED_AT);
    }

    private static long transactionCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transaction_entity", Long.class);
    }

    @Test
    void migratesAnEmptyDatabaseAndValidatesWithHibernate() {
        var jdbc = new JdbcTemplate(dataSource(mysql));
        assertThat(jdbc.queryForList("SHOW TABLES")).isEmpty();

        try (var context = startApplication(mysql)) {
            assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
            assertThat(schema(jdbc)).hasSize(2);
            var migrations = history(jdbc);
            assertThat(migrations).containsExactly(
                    List.of("1", "SQL", V1, true),
                    List.of("2", "SQL", V2, true),
                    List.of("3", "SQL", V3, true),
                    List.of("4", "SQL", V4, true),
                    List.of("5", "SQL", V5, true),
                    List.of("6", "SQL", V6, true),
                    List.of("7", "SQL", V7, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY", Long.class)).isZero();
            assertThat(jdbc.queryForList("SHOW TABLES LIKE 'pending_transactions'")).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
            assertThat(jdbc.queryForList("""
                    SELECT column_type FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND (
                        (table_name = 'users' AND column_name = 'id') OR
                        (table_name = 'transaction_entity' AND column_name = 'user_id')
                    )
                    """, String.class)).containsExactly("binary(16)", "binary(16)");

            var email = "migration-test@example.invalid";
            assertThat(jdbc.update(INSERT_USER, "00000000000000000000000000000001", email)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update(INSERT_USER, "00000000000000000000000000000002", email))
                    .isInstanceOf(DuplicateKeyException.class)
                    .hasMessageContaining("uk_users_email");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1L);

            var ddl = schema(jdbc).getLast();
            assertThat(ddl).contains("CONSTRAINT `" + USER_FK + "` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)");
            assertThat(jdbc.queryForObject("""
                    SELECT delete_rule FROM information_schema.referential_constraints
                    WHERE constraint_schema = DATABASE() AND constraint_name = ?
                    """, String.class, USER_FK)).isEqualTo("RESTRICT");
            assertThat(jdbc.update(INSERT_TRANSACTION, "00000000000000000000000000000001",
                    "00000000000000000000000000000001")).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update(INSERT_TRANSACTION,
                    "00000000000000000000000000000002", LEGACY_USER_ID))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(failure -> assertForeignKeyViolation(failure, 1452));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_entity", Long.class)).isEqualTo(1L);

            System.out.println("Empty MySQL history (version, type, script, success): " + migrations);
            System.out.println("Empty MySQL: Hibernate validated; valid account accepted; missing account rejected "
                    + "by " + USER_FK + " (MySQL 1452); delete rule RESTRICT.");
        }
    }

    @Test
    void baselinesMatchingLegacySchemaAndPreservesExistingRows() {
        var legacyDataSource = dataSource(mysql);
        new ResourceDatabasePopulator(new ClassPathResource("migration/legacy-schema.sql"),
                new ClassPathResource("migration/legacy-data.sql")).execute(legacyDataSource);

        var jdbc = new JdbcTemplate(legacyDataSource);
        var originalTransactions = jdbc.queryForList(ORIGINAL_TRANSACTIONS);
        var originalSessions = jdbc.queryForList("SELECT * FROM conversation_sessions");
        assertThat(originalTransactions).hasSize(2);
        assertThat(originalSessions).hasSize(1);
        assertThat(jdbc.queryForList("SHOW TABLES LIKE 'flyway_schema_history'")).isEmpty();

        try (var reference = new MySQLContainer("mysql:8.4")) {
            reference.start();
            var referenceDataSource = dataSource(reference);
            Flyway.configure().dataSource(referenceDataSource).target("1").load().migrate();
            var referenceJdbc = new JdbcTemplate(referenceDataSource);
            assertThat(history(referenceJdbc)).containsExactly(List.of("1", "SQL", V1, true));
            assertThat(schema(jdbc)).containsExactlyElementsOf(schema(referenceJdbc));
        }

        Flyway.configure().dataSource(legacyDataSource).baselineVersion("1").load().baseline();
        Flyway.configure().dataSource(legacyDataSource).target("3").load().migrate();
        assertThat(history(jdbc)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
        assertThat(jdbc.update(INSERT_USER, LEGACY_USER_ID, "legacy-owner@example.invalid")).isEqualTo(1);

        try (var context = startApplication(mysql)) {
            assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
            var migrations = history(jdbc);
            assertThat(migrations).satisfiesExactly(
                    baseline -> {
                        assertThat(baseline.get(0)).isEqualTo("1");
                        assertThat(baseline.get(1)).isEqualTo("BASELINE");
                        assertThat(baseline.get(3)).isEqualTo(true);
                    },
                    migration -> assertThat(migration).containsExactly("2", "SQL", V2, true),
                    migration -> assertThat(migration).containsExactly("3", "SQL", V3, true),
                    migration -> assertThat(migration).containsExactly("4", "SQL", V4, true),
                    migration -> assertThat(migration).containsExactly("5", "SQL", V5, true),
                    migration -> assertThat(migration).containsExactly("6", "SQL", V6, true),
                    migration -> assertThat(migration).containsExactly("7", "SQL", V7, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_exchanges", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY", Long.class)).isZero();
            assertThat(jdbc.queryForList("SELECT HEX(id) FROM users", String.class)).containsExactly(LEGACY_USER_ID);
            assertThat(jdbc.queryForList(ORIGINAL_TRANSACTIONS))
                    .usingRecursiveComparison().isEqualTo(originalTransactions);
            assertThat(jdbc.queryForList("SELECT * FROM conversation_sessions"))
                    .usingRecursiveComparison().isEqualTo(originalSessions);

            var transactions = context.getBean(TransactionRepository.class).findByUserIdAndCategory(
                    UUID.fromString("00000000-0000-0000-0000-000000000010"), Category.GROCERIES);
            assertThat(transactions).hasSize(2).allSatisfy(transaction -> {
                assertThat(transaction.getType()).isEqualTo(TransactionType.EXPENSE);
                assertThat(transaction.getOccurredAt()).isEqualTo(transaction.getCreatedAt());
            });
            assertThat(transactions).extracting(transaction -> transaction.getOccurredAt())
                    .containsExactlyInAnyOrder(Instant.parse("2025-01-02T03:04:05.123456Z"), null);
            System.out.println("Legacy MySQL history (version, type, script, success): " + migrations);
            System.out.println("Legacy MySQL: DDL matched V1 before baseline; owner fixture inserted after V3; "
                    + "Hibernate validated; original rows preserved; both EXPENSE, "
                    + "occurred_at = 2025-01-02T03:04:05.123456Z and NULL.");
        }
    }

    @Test
    void rejectsV4WhenAnOrphanExistsWithoutChangingTheTransaction() {
        var source = dataSource(mysql);
        Flyway.configure().dataSource(source).target("3").load().migrate();
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.update(INSERT_TRANSACTION, "00000000000000000000000000000001", LEGACY_USER_ID))
                .isEqualTo(1);
        var before = jdbc.queryForList("SELECT * FROM transaction_entity");
        var ddl = schema(jdbc);

        assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
                .isInstanceOf(FlywayException.class)
                .satisfies(failure -> assertForeignKeyViolation(failure, 1452));
        assertThat(jdbc.queryForList("SELECT * FROM transaction_entity"))
                .usingRecursiveComparison().isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
        assertThat(schema(jdbc)).containsExactlyElementsOf(ddl);
        var migrations = history(jdbc);
        assertThat(migrations).containsExactly(
                List.of("1", "SQL", V1, true),
                List.of("2", "SQL", V2, true),
                List.of("3", "SQL", V3, true),
                List.of("4", "SQL", V4, false));
        System.out.println("Orphan MySQL history (version, type, script, success): " + migrations);
        System.out.println("Orphan MySQL: " + USER_FK + " rejected V4 (MySQL 1452); "
                + "the transaction and table DDL remained intact; users remained empty.");
    }

    private static void assertForeignKeyViolation(Throwable failure, int errorCode) {
        assertThat(failure).rootCause().isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo(errorCode);
            assertThat(cause.getMessage()).contains(USER_FK);
        });
    }

    private static List<List<Object>> history(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT version, type, script, success FROM flyway_schema_history ORDER BY installed_rank
                """).stream().map(row -> List.of(row.get("version"), row.get("type"),
                row.get("script"), row.get("success"))).toList();
    }

    private static List<String> schema(JdbcTemplate jdbc) {
        return List.of(
                jdbc.queryForMap("SHOW CREATE TABLE conversation_sessions").get("Create Table").toString(),
                jdbc.queryForMap("SHOW CREATE TABLE transaction_entity").get("Create Table").toString());
    }

    private static DriverManagerDataSource dataSource(MySQLContainer container) {
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static ConfigurableApplicationContext startApplication(MySQLContainer container, Class<?>... extraSources) {
        return new SpringApplicationBuilder(BudgetingApplication.class)
                .sources(extraSources)
                .web(WebApplicationType.NONE)
                .run("--spring.config.location=classpath:/application.properties",
                        "--spring.profiles.active=mysql-migration",
                        "--spring.datasource.url=" + container.getJdbcUrl(),
                        "--spring.datasource.username=" + container.getUsername(),
                        "--spring.datasource.password=" + container.getPassword(),
                        "--spring.flyway.url=" + container.getJdbcUrl(),
                        "--spring.flyway.user=" + container.getUsername(),
                        "--spring.flyway.password=" + container.getPassword(),
                        "--spring.docker.compose.enabled=false");
    }
}

package dio.budgeting.migration;

import dio.budgeting.BudgetingApplication;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.domain.TransactionType;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("mysql-migration")
@Testcontainers
class MySqlMigrationTest {

    private static final String V1 = "V1__initial_schema.sql";
    private static final String V2 = "V2__add_transaction_type_and_occurred_at.sql";
    private static final String V3 = "V3__create_users.sql";
    private static final String INSERT_USER = """
            INSERT INTO users (id, email, password_hash, created_at)
            VALUES (UNHEX(?), ?, 'fixture-hash', '2025-01-02 03:04:05.123456')
            """;
    private static final String ORIGINAL_TRANSACTIONS = """
            SELECT id, amount, category, created_at, description, source, user_id
            FROM transaction_entity ORDER BY id
            """;

    @Container
    private final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

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
                    List.of("3", "SQL", V3, true));
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

            System.out.println("Empty MySQL history (version, type, script, success): " + migrations);
            System.out.println("Empty MySQL: Hibernate validated; users started empty; UUID columns match; duplicate email rejected.");
            System.out.println(jdbc.queryForMap("SHOW CREATE TABLE users").get("Create Table"));
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
                    migration -> assertThat(migration).containsExactly("3", "SQL", V3, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
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
            System.out.println("Legacy MySQL: DDL matched V1 before baseline; users started empty; "
                    + "Hibernate validated; original rows preserved; both EXPENSE, "
                    + "occurred_at = 2025-01-02T03:04:05.123456Z and NULL.");
        }
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

    private static ConfigurableApplicationContext startApplication(MySQLContainer container) {
        return new SpringApplicationBuilder(BudgetingApplication.class)
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

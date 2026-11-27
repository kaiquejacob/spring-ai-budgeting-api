package dio.budgeting.migration;

import dio.budgeting.BudgetingApplication;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql-migration")
@Testcontainers
class MySqlMigrationTest {

    @Container
    private final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Test
    void migratesAnEmptyDatabaseAndValidatesWithHibernate() {
        var jdbc = new JdbcTemplate(dataSource(mysql));
        assertThat(jdbc.queryForList("SHOW TABLES")).isEmpty();

        try (var context = startApplication(mysql)) {
            assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
            assertThat(schema(jdbc)).hasSize(2);
            assertThat(jdbc.queryForList("""
                    SELECT version, type, script, success FROM flyway_schema_history
                    """).stream().map(row -> List.of(row.get("version"), row.get("type"),
                    row.get("script"), row.get("success"))))
                    .containsExactly(List.of("1", "SQL", "V1__initial_schema.sql", true));
            System.out.println("Empty MySQL: V1 applied, SQL history recorded, Hibernate validated.");
        }
    }

    @Test
    void baselinesMatchingLegacySchemaAndPreservesExistingRows() {
        var legacyDataSource = dataSource(mysql);
        new ResourceDatabasePopulator(new ClassPathResource("migration/legacy-schema.sql"),
                new ClassPathResource("migration/legacy-data.sql")).execute(legacyDataSource);

        var jdbc = new JdbcTemplate(legacyDataSource);
        var originalTransactions = jdbc.queryForList("SELECT * FROM transaction_entity");
        var originalSessions = jdbc.queryForList("SELECT * FROM conversation_sessions");
        assertThat(originalTransactions).hasSize(1);
        assertThat(originalSessions).hasSize(1);
        assertThat(jdbc.queryForList("SHOW TABLES LIKE 'flyway_schema_history'")).isEmpty();

        try (var reference = new MySQLContainer("mysql:8.4")) {
            reference.start();
            Flyway.configure().dataSource(dataSource(reference)).load().migrate();
            assertThat(schema(jdbc)).containsExactlyElementsOf(schema(new JdbcTemplate(dataSource(reference))));
        }

        Flyway.configure().dataSource(legacyDataSource).baselineVersion("1").load().baseline();

        try (var context = startApplication(mysql)) {
            assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
            assertThat(jdbc.queryForList("""
                    SELECT version, type, success FROM flyway_schema_history
                    """).stream().map(row -> List.of(row.get("version"), row.get("type"), row.get("success"))))
                    .containsExactly(List.of("1", "BASELINE", true));
            assertThat(jdbc.queryForList("SELECT * FROM transaction_entity"))
                    .usingRecursiveComparison().isEqualTo(originalTransactions);
            assertThat(jdbc.queryForList("SELECT * FROM conversation_sessions"))
                    .usingRecursiveComparison().isEqualTo(originalSessions);
            System.out.println("Legacy MySQL: SHOW CREATE TABLE matched V1 before baseline; "
                    + "BASELINE 1 recorded, Hibernate validated, both rows preserved.");
        }
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

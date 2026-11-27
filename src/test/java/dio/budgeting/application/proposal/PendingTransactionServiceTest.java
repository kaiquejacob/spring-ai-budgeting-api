package dio.budgeting.application.proposal;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionType;
import dio.budgeting.infrastructure.persistence.repository.PendingTransactionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;

@SpringBootTest
@ActiveProfiles("test")
class PendingTransactionServiceTest {

    @Autowired
    private PendingTransactionJpaRepository proposals;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private PendingTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PendingTransactionService(
                proposals,
                Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        );
    }

    @Test
    void shouldAllowOwnerToConsumeProposal() {
        var userId = UUID.randomUUID();
        var proposal = createProposal();

        var token = service.save(userId, proposal);

        assertEquals(Instant.parse("2026-11-23T15:05:00Z"), proposals.findById(token).orElseThrow().getExpiresAt());
        var result = consume(token, userId);

        assertEquals(proposal, result);
    }

    @Test
    void shouldRejectAnotherUserWithoutConsumingToken() {
        var ownerId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var proposal = createProposal();

        var token = service.save(ownerId, proposal);

        assertThrows(
                PendingTransactionException.class,
                () -> consume(token, otherUserId)
        );

        assertEquals(
                proposal,
                consume(token, ownerId)
        );
    }

    @Test
    void shouldRejectSecondConsumptionOfSameToken() {
        var userId = UUID.randomUUID();
        var token = service.save(userId, createProposal());

        consume(token, userId);

        assertThrows(
                PendingTransactionException.class,
                () -> consume(token, userId)
        );
    }

    @Test
    void shouldRejectUnknownToken() {
        assertThrows(
                PendingTransactionException.class,
                () -> consume(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
    }

    private TransactionAiResponse consume(UUID token, UUID userId) {
        return new TransactionTemplate(transactionManager).execute(status -> service.consume(token, userId));
    }

    private TransactionAiResponse createProposal() {
        return new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Compra no mercado",
                2590L,
                Category.GROCERIES,
                null,
                null,
                null,
                TransactionType.EXPENSE,
                null);
    }
}

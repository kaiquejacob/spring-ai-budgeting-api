package dio.budgeting.application.ai;

import dio.budgeting.application.PendingTransactionException;
import dio.budgeting.application.PendingTransactionService;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingTransactionServiceTest {

    private PendingTransactionService service;

    @BeforeEach
    void setUp() {
        service = new PendingTransactionService(
                Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        );
    }

    @Test
    void shouldAllowOwnerToConsumeProposal() {
        var userId = UUID.randomUUID();
        var proposal = createProposal();

        var token = service.save(userId, proposal);

        var result = service.consume(token, userId);

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
                () -> service.consume(token, otherUserId)
        );

        assertEquals(
                proposal,
                service.consume(token, ownerId)
        );
    }

    @Test
    void shouldRejectSecondConsumptionOfSameToken() {
        var userId = UUID.randomUUID();
        var token = service.save(userId, createProposal());

        service.consume(token, userId);

        assertThrows(
                PendingTransactionException.class,
                () -> service.consume(token, userId)
        );
    }

    @Test
    void shouldRejectUnknownToken() {
        assertThrows(
                PendingTransactionException.class,
                () -> service.consume(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
    }

    private TransactionAiResponse createProposal() {
        return new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Compra no mercado",
                2590L,
                Category.GROCERIES,
                null,
                "",
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }
}
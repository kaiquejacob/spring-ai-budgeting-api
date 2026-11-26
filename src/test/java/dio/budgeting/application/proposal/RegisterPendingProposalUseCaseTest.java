package dio.budgeting.application.proposal;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dio.budgeting.application.conversation.ConversationSession;
import dio.budgeting.application.conversation.ConversationSessionService;

class RegisterPendingProposalUseCaseTest {

    private PendingTransactionService pendingTransactionService;
    private ConversationSessionService conversationSessionService;
    private RegisterPendingProposalUseCase useCase;

    @BeforeEach
    void setUp() {
        pendingTransactionService = mock(PendingTransactionService.class);
        conversationSessionService = mock(ConversationSessionService.class);
        useCase = new RegisterPendingProposalUseCase(
                pendingTransactionService,
                conversationSessionService
        );
    }

    @Test
    void shouldRegisterProposalInOrderAndReturnItsToken() {
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var token = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var session = new ConversationSession(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                userId,
                Clock.fixed(
                        Instant.parse("2026-11-24T00:00:00Z"),
                        ZoneId.of("America/Sao_Paulo")
                )
        );
        var response = new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Compra no mercado",
                2590L,
                Category.GROCERIES,
                null,
                "Confirme a compra.",
                null
        );
        when(pendingTransactionService.save(userId, response)).thenReturn(token);

        var result = useCase.execute(userId, response, session);

        assertEquals(token, result);
        assertEquals(response, session.pendingProposal());
        assertEquals(token, session.pendingConfirmationToken());
        InOrder order = inOrder(pendingTransactionService, conversationSessionService);
        order.verify(pendingTransactionService).save(userId, response);
        order.verify(conversationSessionService).save(session);
        verify(conversationSessionService).save(session);
    }
}

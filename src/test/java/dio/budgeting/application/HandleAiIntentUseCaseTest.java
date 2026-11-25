package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandleAiIntentUseCaseTest {

    private RegisterPendingProposalUseCase registerPendingProposalUseCase;
    private ConfirmPendingTransactionUseCase confirmPendingTransactionUseCase;
    private ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private ConversationSessionService conversationSessionService;
    private HandleAiIntentUseCase useCase;

    private UUID userId;
    private UUID sessionId;
    private UUID token;
    private ConversationSession session;

    @BeforeEach
    void setUp() {
        registerPendingProposalUseCase = mock(RegisterPendingProposalUseCase.class);
        confirmPendingTransactionUseCase = mock(ConfirmPendingTransactionUseCase.class);
        listTransactionsByCategoryUseCase = mock(ListTransactionsByCategoryUseCase.class);
        sumTransactionsByCategoryUseCase = mock(SumTransactionsByCategoryUseCase.class);
        listRecentTransactionsUseCase = mock(ListRecentTransactionsUseCase.class);
        conversationSessionService = mock(ConversationSessionService.class);
        useCase = new HandleAiIntentUseCase(
                registerPendingProposalUseCase,
                confirmPendingTransactionUseCase,
                listTransactionsByCategoryUseCase,
                sumTransactionsByCategoryUseCase,
                listRecentTransactionsUseCase,
                conversationSessionService
        );

        userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        token = UUID.fromString("33333333-3333-3333-3333-333333333333");
        session = mock(ConversationSession.class);
        when(session.id()).thenReturn(sessionId);
        when(session.userId()).thenReturn(userId);
    }

    @Test
    void shouldRegisterCreateTransactionAsPending() {
        var response = response(TransactionIntent.CREATE_TRANSACTION);
        when(registerPendingProposalUseCase.execute(userId, response, session))
                .thenReturn(token);

        var result = useCase.execute(userId, response, session);

        assertEquals(new AiIntentResult.Pending(token, response, sessionId), result);
        verify(registerPendingProposalUseCase).execute(userId, response, session);
    }

    @Test
    void shouldConfirmValidPendingProposalAndClearSession() {
        when(session.pendingConfirmationToken()).thenReturn(token);
        when(session.pendingProposal()).thenReturn(response(TransactionIntent.CREATE_TRANSACTION));
        var output = output();
        when(confirmPendingTransactionUseCase.execute(token, userId)).thenReturn(output);

        var result = useCase.execute(
                userId,
                response(TransactionIntent.CONFIRM_PENDING),
                session
        );

        assertEquals(new AiIntentResult.Created(output, sessionId), result);
        var order = inOrder(confirmPendingTransactionUseCase, session, conversationSessionService);
        order.verify(confirmPendingTransactionUseCase).execute(token, userId);
        order.verify(session).clearPendingProposal();
        order.verify(conversationSessionService).save(session);
    }

    @Test
    void shouldReturnClarificationWhenConfirmingWithoutPendingProposal() {
        when(session.pendingConfirmationToken()).thenReturn(null);
        when(session.pendingProposal()).thenReturn(null);

        var result = useCase.execute(
                userId,
                response(TransactionIntent.CONFIRM_PENDING),
                session
        );

        var clarification = assertInstanceOf(AiIntentResult.Clarification.class, result);
        assertEquals(sessionId, clarification.sessionId());
        assertEquals(
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.",
                clarification.response().s()
        );
        verify(confirmPendingTransactionUseCase, never()).execute(token, userId);
        verify(conversationSessionService, never()).save(session);
    }

    @Test
    void shouldListTransactionsByCategoryAndSaveLastCategory() {
        var response = response(TransactionIntent.LIST_TRANSACTIONS, Category.GROCERIES, null);
        var transactions = List.of(output());
        when(listTransactionsByCategoryUseCase.execute(userId, Category.GROCERIES))
                .thenReturn(transactions);

        var result = useCase.execute(userId, response, session);

        assertEquals(new AiIntentResult.Listed(transactions, sessionId), result);
        var order = inOrder(session, conversationSessionService, listTransactionsByCategoryUseCase);
        order.verify(session).setLastCategory(Category.GROCERIES);
        order.verify(conversationSessionService).save(session);
        order.verify(listTransactionsByCategoryUseCase).execute(userId, Category.GROCERIES);
    }

    @Test
    void shouldAskForCategoryWhenListingWithoutCategory() {
        var result = useCase.execute(
                userId,
                response(TransactionIntent.LIST_TRANSACTIONS, null, null),
                session
        );

        var clarification = assertInstanceOf(AiIntentResult.Clarification.class, result);
        assertEquals(
                "Informe a categoria para listar as transações.",
                clarification.response().messageToUser()
        );
        verify(listTransactionsByCategoryUseCase, never()).execute(userId, Category.GROCERIES);
        verify(conversationSessionService, never()).save(session);
    }

    @Test
    void shouldSummarizeTransactionsAndSaveContext() {
        var response = response(
                TransactionIntent.SUM_TRANSACTIONS_BY_CATEGORY,
                Category.GROCERIES,
                TransactionAiResponse.AiPeriod.THIS_WEEK
        );
        when(sumTransactionsByCategoryUseCase.execute(
                userId,
                Category.GROCERIES,
                TransactionAiResponse.AiPeriod.THIS_WEEK
        )).thenReturn(12345L);

        var result = useCase.execute(userId, response, session);

        assertEquals(
                new AiIntentResult.Summarized(
                        Category.GROCERIES,
                        TransactionAiResponse.AiPeriod.THIS_WEEK,
                        12345L,
                        sessionId
                ),
                result
        );
        var order = inOrder(session, conversationSessionService, sumTransactionsByCategoryUseCase);
        order.verify(session).setLastCategory(Category.GROCERIES);
        order.verify(session).setLastPeriod(TransactionAiResponse.AiPeriod.THIS_WEEK);
        order.verify(conversationSessionService).save(session);
        order.verify(sumTransactionsByCategoryUseCase).execute(
                userId,
                Category.GROCERIES,
                TransactionAiResponse.AiPeriod.THIS_WEEK
        );
    }

    @Test
    void shouldAskForCategoryAndPeriodWhenSummaryContextIsIncomplete() {
        var result = useCase.execute(
                userId,
                response(TransactionIntent.SUM_TRANSACTIONS_BY_CATEGORY, null, null),
                session
        );

        var clarification = assertInstanceOf(AiIntentResult.Clarification.class, result);
        assertEquals(
                "Informe a categoria e o período para calcular o total.",
                clarification.response().messageToUser()
        );
        verify(sumTransactionsByCategoryUseCase, never()).execute(
                userId,
                Category.GROCERIES,
                TransactionAiResponse.AiPeriod.THIS_WEEK
        );
        verify(conversationSessionService, never()).save(session);
    }

    @Test
    void shouldApplyLastCategoryAndPeriodFromSession() {
        when(session.lastCategory()).thenReturn(Category.PHARMA);
        when(session.lastPeriod()).thenReturn(TransactionAiResponse.AiPeriod.CURRENT_MONTH);
        when(sumTransactionsByCategoryUseCase.execute(
                userId,
                Category.PHARMA,
                TransactionAiResponse.AiPeriod.CURRENT_MONTH
        )).thenReturn(9900L);

        var result = useCase.execute(
                userId,
                response(TransactionIntent.SUM_TRANSACTIONS_BY_CATEGORY, null, null),
                session
        );

        assertEquals(
                new AiIntentResult.Summarized(
                        Category.PHARMA,
                        TransactionAiResponse.AiPeriod.CURRENT_MONTH,
                        9900L,
                        sessionId
                ),
                result
        );
        verify(sumTransactionsByCategoryUseCase).execute(
                userId,
                Category.PHARMA,
                TransactionAiResponse.AiPeriod.CURRENT_MONTH
        );
    }

    @Test
    void shouldReturnRecentTransactions() {
        var transactions = List.of(output());
        when(listRecentTransactionsUseCase.execute(userId)).thenReturn(transactions);

        var result = useCase.execute(
                userId,
                response(TransactionIntent.LIST_RECENT_TRANSACTIONS),
                session
        );

        assertEquals(new AiIntentResult.Recent(transactions, sessionId), result);
        verify(listRecentTransactionsUseCase).execute(userId);
    }

    @Test
    void shouldReturnClarificationIntentUnchanged() {
        var response = response(TransactionIntent.CLARIFICATION_NEEDED);

        var result = useCase.execute(userId, response, session);

        assertEquals(new AiIntentResult.Clarification(response, sessionId), result);
    }

    @Test
    void shouldReturnUnsupportedIntentAsClarificationUnchanged() {
        var response = response(TransactionIntent.UNSUPPORTED_REQUEST);

        var result = useCase.execute(userId, response, session);

        assertEquals(new AiIntentResult.Clarification(response, sessionId), result);
    }

    private static TransactionAiResponse response(TransactionIntent intent) {
        return response(intent, null, null);
    }

    private static TransactionAiResponse response(
            TransactionIntent intent,
            Category category,
            TransactionAiResponse.AiPeriod period
    ) {
        return new TransactionAiResponse(
                intent,
                "Compra",
                2500L,
                category,
                period,
                "Resposta",
                null
        );
    }

    private static TransactionOutput output() {
        return new TransactionOutput("txn-1", "GROCERIES", "Compra", 25.0);
    }
}

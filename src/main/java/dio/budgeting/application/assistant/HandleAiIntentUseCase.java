package dio.budgeting.application.assistant;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import org.springframework.stereotype.Service;

import java.util.UUID;
import dio.budgeting.application.proposal.ConfirmPendingTransactionUseCase;
import dio.budgeting.application.conversation.ConversationSession;
import dio.budgeting.application.conversation.ConversationSessionService;
import dio.budgeting.application.transaction.ListRecentTransactionsUseCase;
import dio.budgeting.application.transaction.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.proposal.RegisterPendingProposalUseCase;
import dio.budgeting.application.transaction.SumTransactionsByCategoryUseCase;

@Service
public class HandleAiIntentUseCase {

    private final RegisterPendingProposalUseCase registerPendingProposalUseCase;
    private final ConfirmPendingTransactionUseCase confirmPendingTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private final ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private final ConversationSessionService conversationSessionService;

    public HandleAiIntentUseCase(
            RegisterPendingProposalUseCase registerPendingProposalUseCase,
            ConfirmPendingTransactionUseCase confirmPendingTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase,
            ListRecentTransactionsUseCase listRecentTransactionsUseCase,
            ConversationSessionService conversationSessionService
    ) {
        this.registerPendingProposalUseCase = registerPendingProposalUseCase;
        this.confirmPendingTransactionUseCase = confirmPendingTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.sumTransactionsByCategoryUseCase = sumTransactionsByCategoryUseCase;
        this.listRecentTransactionsUseCase = listRecentTransactionsUseCase;
        this.conversationSessionService = conversationSessionService;
    }

    public AiIntentResult execute(
            UUID userId,
            TransactionAiResponse response,
            ConversationSession session
    ) {
        var aiResponse = applySessionContext(response, session);

        return switch (aiResponse.intent()) {
            case CREATE_TRANSACTION -> new AiIntentResult.Pending(
                    registerPendingProposalUseCase.execute(
                            userId,
                            aiResponse,
                            session
                    ),
                    aiResponse,
                    session.id()
            );
            case CONFIRM_PENDING -> confirmPendingProposal(session);
            case LIST_TRANSACTIONS -> handleListByCategory(
                    userId,
                    aiResponse,
                    session
            );
            case SUM_TRANSACTIONS_BY_CATEGORY -> handleSummary(
                    userId,
                    aiResponse,
                    session
            );
            case LIST_RECENT_TRANSACTIONS -> new AiIntentResult.Recent(
                    listRecentTransactionsUseCase.execute(userId),
                    session.id()
            );
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST ->
                    new AiIntentResult.Clarification(aiResponse, session.id());
        };
    }

    private AiIntentResult confirmPendingProposal(ConversationSession session) {
        var token = session.pendingConfirmationToken();

        if (session.pendingProposal() == null || token == null) {
            return new AiIntentResult.Clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Não há uma proposta válida para confirmar. Gere uma nova proposta."
                    ),
                    session.id()
            );
        }

        var output = confirmPendingTransactionUseCase.execute(
                token,
                session.userId()
        );

        session.clearPendingProposal();
        conversationSessionService.save(session);

        return new AiIntentResult.Created(output, session.id());
    }

    private AiIntentResult handleListByCategory(
            UUID userId,
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var category = aiResponse.category();

        if (category == null) {
            return new AiIntentResult.Clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            "Informe a categoria para listar as transações.",
                            "Não há uma proposta válida para confirmar. Gere uma nova proposta."),
                    session.id()
            );
        }

        session.setLastCategory(category);
        conversationSessionService.save(session);

        var transactions = listTransactionsByCategoryUseCase.execute(
                userId,
                category
        );

        return new AiIntentResult.Listed(transactions, session.id());
    }

    private AiIntentResult handleSummary(
            UUID userId,
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var category = aiResponse.category();
        var period = aiResponse.period();

        if (category == null || period == null) {
            return new AiIntentResult.Clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            "Informe a categoria e o período para calcular o total.",
                            "Não há uma proposta válida para confirmar. Gere uma nova proposta."),
                    session.id()
            );
        }

        session.setLastCategory(category);
        session.setLastPeriod(period);
        conversationSessionService.save(session);

        var total = sumTransactionsByCategoryUseCase.execute(
                userId,
                category,
                period
        );

        return new AiIntentResult.Summarized(
                category,
                period,
                total,
                session.id()
        );
    }

    private TransactionAiResponse applySessionContext(
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        if (aiResponse.intent() != TransactionIntent.SUM_TRANSACTIONS_BY_CATEGORY
                && aiResponse.intent() != TransactionIntent.LIST_TRANSACTIONS) {
            return aiResponse;
        }

        var category = aiResponse.category() != null
                ? aiResponse.category()
                : session.lastCategory();

        var period = aiResponse.period() != null
                ? aiResponse.period()
                : session.lastPeriod();

        return new TransactionAiResponse(
                aiResponse.intent(),
                aiResponse.description(),
                aiResponse.amountInCents(),
                category,
                period,
                aiResponse.messageToUser(),
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }
}

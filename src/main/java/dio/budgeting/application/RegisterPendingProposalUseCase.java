package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RegisterPendingProposalUseCase {

    private final PendingTransactionService pendingTransactionService;
    private final ConversationSessionService conversationSessionService;

    public RegisterPendingProposalUseCase(
            PendingTransactionService pendingTransactionService,
            ConversationSessionService conversationSessionService
    ) {
        this.pendingTransactionService = pendingTransactionService;
        this.conversationSessionService = conversationSessionService;
    }

    public UUID execute(
            UUID userId,
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var token = pendingTransactionService.save(userId, aiResponse);
        session.setPendingProposal(aiResponse);
        session.setPendingConfirmationToken(token);
        conversationSessionService.save(session);
        return token;
    }
}

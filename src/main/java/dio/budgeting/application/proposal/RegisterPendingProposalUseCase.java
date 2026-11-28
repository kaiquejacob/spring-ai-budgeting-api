package dio.budgeting.application.proposal;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import dio.budgeting.application.conversation.ConversationSession;
import dio.budgeting.application.conversation.ConversationSessionService;

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

    @Transactional
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

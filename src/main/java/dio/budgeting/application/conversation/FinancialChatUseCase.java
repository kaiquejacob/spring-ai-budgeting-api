package dio.budgeting.application.conversation;

import dio.budgeting.infrastructure.persistence.repository.ConversationHistoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;
import dio.budgeting.application.transaction.ListRecentTransactionsUseCase;
import dio.budgeting.application.transaction.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.transaction.SumTransactionsByCategoryUseCase;

@Service
public class FinancialChatUseCase {

    private final ConversationSessionService conversationSessionService;
    private final SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private final ChatClient financialToolChatClient;
    private final ConversationHistoryRepository history;
    private final Clock clock;

    public FinancialChatUseCase(
            ConversationSessionService conversationSessionService,
            SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ListRecentTransactionsUseCase listRecentTransactionsUseCase,
            @Qualifier("financialToolChatClient") ChatClient financialToolChatClient,
            ConversationHistoryRepository history,
            Clock clock
    ) {
        this.conversationSessionService = conversationSessionService;
        this.sumTransactionsByCategoryUseCase = sumTransactionsByCategoryUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.listRecentTransactionsUseCase = listRecentTransactionsUseCase;
        this.financialToolChatClient = financialToolChatClient;
        this.history = history;
        this.clock = clock;
    }

    public FinancialChatResult execute(
            UUID userId,
            UUID sessionId,
            String message
    ) {
        var session = conversationSessionService.getOrCreate(
                sessionId,
                userId
        );

        var conversationId = userId + ":" + session.id();

        var tools = new FinancialTools(
                userId,
                sumTransactionsByCategoryUseCase,
                listTransactionsByCategoryUseCase,
                listRecentTransactionsUseCase
        );

        var answer = financialToolChatClient
                .prompt()
                .user(message)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .tools(tools)
                .call()
                .content();

        if (answer != null && !answer.isBlank()) {
            history.append(session.id(), message, answer, clock.instant());
        }
        return new FinancialChatResult(answer, session.id());
    }
}

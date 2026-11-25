package dio.budgeting.application;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FinancialChatUseCase {

    private final ConversationSessionService conversationSessionService;
    private final SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private final ChatClient financialToolChatClient;

    public FinancialChatUseCase(
            ConversationSessionService conversationSessionService,
            SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ListRecentTransactionsUseCase listRecentTransactionsUseCase,
            @Qualifier("financialToolChatClient") ChatClient financialToolChatClient
    ) {
        this.conversationSessionService = conversationSessionService;
        this.sumTransactionsByCategoryUseCase = sumTransactionsByCategoryUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.listRecentTransactionsUseCase = listRecentTransactionsUseCase;
        this.financialToolChatClient = financialToolChatClient;
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

        return new FinancialChatResult(answer, session.id());
    }
}

package dio.budgeting.application.conversation;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dio.budgeting.application.transaction.ListRecentTransactionsUseCase;
import dio.budgeting.application.transaction.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.transaction.SumTransactionsByCategoryUseCase;

class FinancialChatUseCaseTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ChatClient.AdvisorSpec advisorSpec;
    private ConversationSessionService conversationSessionService;
    private SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private FinancialChatUseCase useCase;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        advisorSpec = mock(ChatClient.AdvisorSpec.class);
        conversationSessionService = mock(ConversationSessionService.class);
        sumTransactionsByCategoryUseCase = mock(SumTransactionsByCategoryUseCase.class);
        listTransactionsByCategoryUseCase = mock(ListTransactionsByCategoryUseCase.class);
        listRecentTransactionsUseCase = mock(ListRecentTransactionsUseCase.class);
        useCase = new FinancialChatUseCase(
                conversationSessionService,
                sumTransactionsByCategoryUseCase,
                listTransactionsByCategoryUseCase,
                listRecentTransactionsUseCase,
                chatClient
        );

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(
                org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()
        )).thenAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> advisor = invocation.getArgument(0);
            when(advisorSpec.param(anyString(), any())).thenReturn(advisorSpec);
            advisor.accept(advisorSpec);
            return requestSpec;
        });
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Resposta simulada.");
    }

    @Test
    void shouldCreateSessionUseUserScopedToolsAndReturnAnswerAndSessionId() {
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var sessionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var session = new ConversationSession(
                sessionId,
                userId,
                Clock.fixed(
                        Instant.parse("2026-11-24T12:00:00Z"),
                        ZoneId.of("America/Sao_Paulo")
                )
        );
        when(conversationSessionService.getOrCreate(null, userId)).thenReturn(session);

        var result = useCase.execute(userId, null, "Quanto gastei?");

        assertEquals("Resposta simulada.", result.answer());
        assertEquals(sessionId, result.sessionId());
        verify(conversationSessionService).getOrCreate(null, userId);

        var conversationId = captureConversationId();
        assertEquals(userId + ":" + sessionId, conversationId.get());

        var tools = captureFinancialTools();
        tools.sumByCategory(Category.GROCERIES, TransactionAiResponse.AiPeriod.THIS_WEEK);
        tools.listByCategory(Category.PHARMA);
        tools.listRecent();

        verify(sumTransactionsByCategoryUseCase).execute(
                userId,
                Category.GROCERIES,
                TransactionAiResponse.AiPeriod.THIS_WEEK
        );
        verify(listTransactionsByCategoryUseCase).execute(userId, Category.PHARMA);
        verify(listRecentTransactionsUseCase).execute(userId);
    }

    @Test
    void shouldReuseRequestedSessionAndReturnItsId() {
        var userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        var requestedSessionId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var session = new ConversationSession(
                requestedSessionId,
                userId,
                Clock.fixed(
                        Instant.parse("2026-11-24T12:00:00Z"),
                        ZoneId.of("America/Sao_Paulo")
                )
        );
        when(conversationSessionService.getOrCreate(requestedSessionId, userId))
                .thenReturn(session);

        var result = useCase.execute(userId, requestedSessionId, "Liste minhas despesas.");

        assertEquals("Resposta simulada.", result.answer());
        assertEquals(requestedSessionId, result.sessionId());
        verify(conversationSessionService).getOrCreate(requestedSessionId, userId);
        assertEquals(userId + ":" + requestedSessionId, captureConversationId().get());
    }

    private AtomicReference<String> captureConversationId() {
        var conversationId = new AtomicReference<String>();
        var valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(advisorSpec).param(
                org.mockito.ArgumentMatchers.eq(ChatMemory.CONVERSATION_ID),
                valueCaptor.capture()
        );
        conversationId.set(valueCaptor.getValue());
        return conversationId;
    }

    private FinancialTools captureFinancialTools() {
        org.mockito.ArgumentCaptor<Object[]> captor =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(requestSpec).tools(captor.capture());
        for (var tool : captor.getValue()) {
            if (tool instanceof FinancialTools financialTools) {
                return financialTools;
            }
        }
        throw new AssertionError("FinancialTools was not passed to ChatClient.");
    }
}

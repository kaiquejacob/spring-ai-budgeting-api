package dio.budgeting.application.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.*;

class FinancialChatMemoryTest {

    private ChatMemory memory;

    @BeforeEach
    void setUp() {
        memory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Test
    void shouldStoreConversationMessages() {
        var conversationId = "user-a:session-1";

        memory.add(
                conversationId,
                new UserMessage("Quero consultar gastos com mercado.")
        );

        memory.add(
                conversationId,
                new AssistantMessage("Qual período deseja consultar?")
        );

        var messages = memory.get(conversationId);

        assertEquals(2, messages.size());
        assertEquals(
                "Quero consultar gastos com mercado.",
                messages.get(0).getText()
        );
        assertEquals(
                "Qual período deseja consultar?",
                messages.get(1).getText()
        );
    }

    @Test
    void shouldSeparateSessionsOfSameUser() {
        memory.add(
                "user-a:session-1",
                new UserMessage("Minha categoria é mercado.")
        );

        assertTrue(
                memory.get("user-a:session-2").isEmpty()
        );
    }

    @Test
    void shouldSeparateUsers() {
        memory.add(
                "user-a:session-1",
                new UserMessage("Consultar despesas.")
        );

        assertTrue(
                memory.get("user-b:session-1").isEmpty()
        );
    }

    @Test
    void shouldClearConversationHistory() {
        var conversationId = "user-a:session-1";

        memory.add(
                conversationId,
                new UserMessage("Consultar despesas.")
        );

        memory.clear(conversationId);

        assertTrue(memory.get(conversationId).isEmpty());
    }
}
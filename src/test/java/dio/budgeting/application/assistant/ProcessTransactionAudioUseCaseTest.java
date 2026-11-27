package dio.budgeting.application.assistant;

import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;

class ProcessTransactionAudioUseCaseTest {

    private TranscriptionModel transcriptionModel;
    private ChatClient chatClient;
    private ProcessTransactionAudioUseCase useCase;

    @BeforeEach
    void setUp() {
        transcriptionModel = mock(TranscriptionModel.class);
        chatClient = mock(ChatClient.class);
        useCase = new ProcessTransactionAudioUseCase(
                transcriptionModel,
                chatClient
        );
    }

    @Test
    void shouldReturnClarificationWhenResponseIsNull() {
        stubModelResponse(null);

        var response = useCase.interpret("gastei 80 reais no mercado");

        assertEquals(
                TransactionIntent.CLARIFICATION_NEEDED,
                response.intent()
        );
    }

    @Test
    void shouldReportAllowedCategoriesWhenCreationHasNoCategory() {
        stubModelResponse(new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION, "Compra", 8000L, null, null, null, null));

        var response = useCase.interpret("gastei 80 reais");

        assertEquals(TransactionIntent.CLARIFICATION_NEEDED, response.intent());
        assertEquals("Informe uma categoria válida: " + Category.allowedNamesInPortuguese() + ".",
                response.messageToUser());
    }

    private void stubModelResponse(TransactionAiResponse response) {
        var promptSpec = Mockito.mock(
                ChatClient.ChatClientRequestSpec.class,
                Mockito.RETURNS_SELF
        );
        var callSpec = Mockito.mock(
                ChatClient.CallResponseSpec.class
        );

        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(TransactionAiResponse.class)).thenReturn(response);
    }
}

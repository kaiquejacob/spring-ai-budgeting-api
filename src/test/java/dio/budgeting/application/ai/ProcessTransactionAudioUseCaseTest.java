package dio.budgeting.application.ai;

import dio.budgeting.application.ProcessTransactionAudioUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        when(callSpec.entity(TransactionAiResponse.class)).thenReturn(null);

        var response = useCase.interpret("gastei 80 reais no mercado");

        assertEquals(
                TransactionIntent.CLARIFICATION_NEEDED,
                response.intent()
        );
    }
}
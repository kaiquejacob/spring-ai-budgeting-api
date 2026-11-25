package dio.budgeting.application;

import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessTransactionAudioWithRetryUseCaseTest {

    private ProcessTransactionAudioUseCase audioUseCase;
    private ProcessTransactionAudioWithRetryUseCase useCase;
    private ByteArrayResource audio;
    private TransactionAiResponse response;

    @BeforeEach
    void setUp() {
        audioUseCase = mock(ProcessTransactionAudioUseCase.class);
        useCase = new ProcessTransactionAudioWithRetryUseCase(audioUseCase);
        audio = new ByteArrayResource(new byte[]{1, 2, 3});
        response = new TransactionAiResponse(
                TransactionIntent.CLARIFICATION_NEEDED,
                null,
                null,
                null,
                null,
                "Informe mais detalhes.",
                null
        );
    }

    @Test
    void shouldReturnResponseWhenFirstAttemptSucceeds() {
        when(audioUseCase.execute(audio)).thenReturn(response);

        assertSame(response, useCase.execute(audio));

        verify(audioUseCase).execute(audio);
    }

    @Test
    void shouldRetryAfterTransientFailureAndReturnSecondResponse() {
        when(audioUseCase.execute(audio))
                .thenThrow(new ResourceAccessException("temporary"))
                .thenReturn(response);

        assertSame(response, useCase.execute(audio));

        verify(audioUseCase, times(2)).execute(audio);
    }

    @Test
    void shouldFailWithUnavailableMessageAfterTwoTransientFailures() {
        when(audioUseCase.execute(audio))
                .thenThrow(new ResourceAccessException("first"))
                .thenThrow(new ResourceAccessException("second"));

        var exception = assertThrows(
                AiProcessingException.class,
                () -> useCase.execute(audio)
        );

        assertEquals("AI provider is unavailable after retry.", exception.getMessage());
        verify(audioUseCase, times(2)).execute(audio);
    }

    @Test
    void shouldPropagateAiProcessingExceptionWithoutRetry() {
        var expected = new AiProcessingException("provider rejected request");
        when(audioUseCase.execute(audio)).thenThrow(expected);

        var actual = assertThrows(
                AiProcessingException.class,
                () -> useCase.execute(audio)
        );

        assertSame(expected, actual);
        verify(audioUseCase).execute(audio);
    }

    @Test
    void shouldWrapOtherExceptionsWithoutRetry() {
        when(audioUseCase.execute(audio)).thenThrow(new IllegalStateException("unexpected"));

        var exception = assertThrows(
                AiProcessingException.class,
                () -> useCase.execute(audio)
        );

        assertEquals("Failed to process transaction audio.", exception.getMessage());
        verify(audioUseCase).execute(audio);
    }
}

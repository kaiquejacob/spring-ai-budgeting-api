package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ConversationSession;
import dio.budgeting.application.ConversationSessionService;
import dio.budgeting.application.PendingTransactionException;
import dio.budgeting.application.PendingTransactionService;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.ProcessTransactionAudioUseCase;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionConfirmationTest {

    private PendingTransactionService pendingService;
    private PersistTransactionUseCase persistUseCase;
    private ProcessTransactionAudioUseCase audioUseCase;
    private ConversationSessionService sessionService;
    private TransactionController controller;

    private UUID userId;
    private UUID token;
    private ConversationSession session;
    private MultipartFile audioFile;

    @BeforeEach
    void setUp() throws Exception {
        pendingService = new PendingTransactionService();
        persistUseCase = mock(PersistTransactionUseCase.class);
        audioUseCase = mock(ProcessTransactionAudioUseCase.class);
        sessionService = mock(ConversationSessionService.class);
        audioFile = mock(MultipartFile.class);

        controller = new TransactionController(
                persistUseCase,
                null,
                audioUseCase,
                pendingService,
                null,
                null,
                sessionService,
                null,
                null,
                null,
                null,
                null
        );

        userId = UUID.randomUUID();
        session = new ConversationSession(UUID.randomUUID(), userId);

        var proposal = new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Compra no mercado",
                2590L,
                Category.GROCERIES,
                null,
                "",
                null
        );

        token = pendingService.save(userId, proposal);

        session.setPendingProposal(proposal);
        session.setPendingConfirmationToken(token);

        when(sessionService.getOrCreate(session.id(), userId))
                .thenReturn(session);

        when(audioUseCase.execute(any()))
                .thenReturn(new TransactionAiResponse(
                        TransactionIntent.CONFIRM_PENDING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        when(persistUseCase.execute(any()))
                .thenReturn(mock(TransactionOutput.class));
    }

    @Test
    void shouldRejectTokenAfterConfirmationByAudio() {
        var response = controller.processAudio(
                userId,
                audioFile,
                session.id()
        );

        assertEquals("CREATE_TRANSACTION", response.intent());
        assertNull(session.pendingProposal());
        assertNull(session.pendingConfirmationToken());

        assertThrows(
                PendingTransactionException.class,
                () -> controller.confirmTransaction(
                        userId,
                        token,
                        session.id()
                )
        );

        verify(persistUseCase, times(1)).execute(any());
    }

    @Test
    void shouldNotPersistAgainAfterConfirmationByToken() {
        controller.confirmTransaction(userId, token, session.id());

        var response = controller.processAudio(
                userId,
                audioFile,
                session.id()
        );

        assertEquals("CLARIFICATION_NEEDED", response.intent());
        verify(persistUseCase, times(1)).execute(any());
    }

    @Test
    void shouldRejectSessionConfirmationAfterTokenUsedWithoutSessionId() {
        controller.confirmTransaction(userId, token, null);

        assertThrows(
                PendingTransactionException.class,
                () -> controller.processAudio(
                        userId,
                        audioFile,
                        session.id()
                )
        );

        verify(persistUseCase, times(1)).execute(any());
    }
}
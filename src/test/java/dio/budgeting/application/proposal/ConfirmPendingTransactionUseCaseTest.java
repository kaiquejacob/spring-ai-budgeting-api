package dio.budgeting.application.proposal;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.transaction.PersistTransactionInput;
import dio.budgeting.application.transaction.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import dio.budgeting.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dio.budgeting.application.transaction.PersistTransactionUseCase;

class ConfirmPendingTransactionUseCaseTest {

    private PendingTransactionService pendingTransactionService;
    private PersistTransactionUseCase persistTransactionUseCase;
    private ConfirmPendingTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        pendingTransactionService = mock(PendingTransactionService.class);
        persistTransactionUseCase = mock(PersistTransactionUseCase.class);
        useCase = new ConfirmPendingTransactionUseCase(
                pendingTransactionService,
                persistTransactionUseCase
        );
    }

    @Test
    void shouldConsumeAndPersistWithCorrectUserAndAiSource() {
        var token = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var proposal = createProposal();
        var output = new TransactionOutput(
                "transaction-id",
                "Compra no mercado",
                "GROCERIES",
                25.90,
                TransactionType.EXPENSE,
                Instant.EPOCH
        );
        when(pendingTransactionService.consume(token, userId)).thenReturn(proposal);
        when(persistTransactionUseCase.execute(any(PersistTransactionInput.class)))
                .thenReturn(output);

        var result = useCase.execute(token, userId);

        assertSame(output, result);
        var inputCaptor = ArgumentCaptor.forClass(PersistTransactionInput.class);
        verify(persistTransactionUseCase).execute(inputCaptor.capture());
        assertEquals(userId, inputCaptor.getValue().userId());
        assertEquals("Compra no mercado", inputCaptor.getValue().description());
        assertEquals(2590L, inputCaptor.getValue().amountInCents());
        assertEquals(Category.GROCERIES, inputCaptor.getValue().category());
        assertEquals(TransactionSource.AI, inputCaptor.getValue().source());
    }

    @Test
    void shouldNotPersistWhenTokenDoesNotExist() {
        var token = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(pendingTransactionService.consume(token, userId))
                .thenThrow(new PendingTransactionException("Proposta não encontrada."));

        assertThrows(
                PendingTransactionException.class,
                () -> useCase.execute(token, userId)
        );

        verify(persistTransactionUseCase, never()).execute(any());
    }

    @Test
    void shouldNotPersistWhenTokenBelongsToAnotherUser() {
        var token = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        var otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(pendingTransactionService.consume(token, otherUserId))
                .thenThrow(new PendingTransactionException("Proposta indisponível."));

        assertThrows(
                PendingTransactionException.class,
                () -> useCase.execute(token, otherUserId)
        );

        verify(persistTransactionUseCase, never()).execute(any());
    }

    private TransactionAiResponse createProposal() {
        return new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Compra no mercado",
                2590L,
                Category.GROCERIES,
                null,
                null,
                null
        );
    }
}

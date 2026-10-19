package dio.budgeting.infrastructure.http;

import dio.budgeting.application.*;
import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionAiHttpResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import dio.budgeting.application.ai.AiProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ProcessTransactionAudioUseCase processTransactionAudioUseCase;
    private final PendingTransactionService pendingTransactionService;
    private final SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private final ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private final ConversationSessionService conversationSessionService;
    private static final Logger log =
            LoggerFactory.getLogger(TransactionController.class);

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ProcessTransactionAudioUseCase processTransactionAudioUseCase, PendingTransactionService pendingTransactionService, SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase, ListRecentTransactionsUseCase listRecentTransactionsUseCase, ConversationSessionService conversationSessionService
    ) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.processTransactionAudioUseCase = processTransactionAudioUseCase;
        this.pendingTransactionService = pendingTransactionService;
        this.sumTransactionsByCategoryUseCase = sumTransactionsByCategoryUseCase;
        this.listRecentTransactionsUseCase = listRecentTransactionsUseCase;
        this.conversationSessionService = conversationSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        var transaction = persistTransactionUseCase.execute(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(@PathVariable Category category) {
        return listTransactionsByCategoryUseCase
                .execute(category)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransactionAiHttpResponse processAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = conversationSessionService.getOrCreate(sessionId);

        var rawAiResponse = processAudioWithRetry(file.getResource());
        var aiResponse = applySessionContext(rawAiResponse, session);

        return switch (aiResponse.intent()) {
            case CREATE_TRANSACTION -> {
                var token = pendingTransactionService.save(aiResponse);

                session.setPendingProposal(aiResponse);

                yield TransactionAiHttpResponse.pending(token, aiResponse, session.id());
            }
            case CONFIRM_PENDING -> confirmPendingProposal(session);
            case LIST_TRANSACTIONS -> {
                session.setLastCategory(aiResponse.category());
                yield TransactionAiHttpResponse.listed(
                        listTransactionsByCategoryUseCase.execute(aiResponse.category()),
                        session.id()
                );
            }
            case SUM_TRANSACTIONS_BY_CATEGORY -> {
                session.setLastCategory(aiResponse.category());
                session.setLastPeriod(aiResponse.period());

                var total = sumTransactionsByCategoryUseCase.execute(
                        aiResponse.category(),
                        aiResponse.period()
                );

                yield TransactionAiHttpResponse.summarized(
                        aiResponse.category(),
                        aiResponse.period(),
                        total,
                        session.id()
                );
            }
            case LIST_RECENT_TRANSACTIONS -> TransactionAiHttpResponse.recent(
                    listRecentTransactionsUseCase.execute(),
                    session.id()
            );
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST ->
                    TransactionAiHttpResponse.clarification(aiResponse, session.id());
        };
    }

    private TransactionAiHttpResponse confirmPendingProposal(ConversationSession session) {
        var proposal = session.pendingProposal();

        if (proposal == null) {
            return TransactionAiHttpResponse.clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            "Não há uma transação pendente para confirmar."
                    ),
                    session.id()
            );
        }

        var output = persistTransactionUseCase.execute(
                new PersistTransactionInput(
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI
                )
        );

        session.clearPendingProposal();

        return TransactionAiHttpResponse.created(output, session.id());
    }

    @PostMapping("/{token}/confirm")
    public ResponseEntity<TransactionAiHttpResponse> confirmTransaction(
            @PathVariable UUID token,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var proposal = pendingTransactionService.consume(token);

        if (proposal == null) {
            return ResponseEntity.notFound().build();
        }

        var output = persistTransactionUseCase.execute(
                new PersistTransactionInput(
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI
                )
        );

        var response = TransactionAiHttpResponse.created(output, sessionId);

        return ResponseEntity.ok(response);
    }

    private TransactionAiResponse applySessionContext(
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        if (aiResponse.intent() != TransactionIntent.SUM_TRANSACTIONS_BY_CATEGORY
                && aiResponse.intent() != TransactionIntent.LIST_TRANSACTIONS) {
            return aiResponse;
        }

        var category = aiResponse.category() != null
                ? aiResponse.category()
                : session.lastCategory();

        var period = aiResponse.period() != null
                ? aiResponse.period()
                : session.lastPeriod();

        return new TransactionAiResponse(
                aiResponse.intent(),
                aiResponse.description(),
                aiResponse.amountInCents(),
                category,
                period,
                aiResponse.messageToUser()
        );
    }

    private TransactionAiHttpResponse handleSummary(
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var category = aiResponse.category();
        var period = aiResponse.period();

        if (category == null || period == null) {
            var clarification = new TransactionAiResponse(
                    TransactionIntent.CLARIFICATION_NEEDED,
                    null,
                    null,
                    null,
                    null,
                    "Informe a categoria e o período para calcular o total."
            );

            return TransactionAiHttpResponse.clarification(
                    clarification,
                    session.id()
            );
        }

        session.setLastCategory(category);
        session.setLastPeriod(period);

        var total = sumTransactionsByCategoryUseCase.execute(category, period);

        return TransactionAiHttpResponse.summarized(
                category,
                period,
                total,
                session.id()
        );
    }

    private TransactionAiHttpResponse handleListByCategory(
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var category = aiResponse.category();

        if (category == null) {
            var clarification = new TransactionAiResponse(
                    TransactionIntent.CLARIFICATION_NEEDED,
                    null,
                    null,
                    null,
                    null,
                    "Informe a categoria para listar as transações."
            );

            return TransactionAiHttpResponse.clarification(
                    clarification,
                    session.id()
            );
        }

        session.setLastCategory(category);

        var transactions = listTransactionsByCategoryUseCase.execute(category);

        return TransactionAiHttpResponse.listed(
                transactions,
                session.id()
        );
    }

    private TransactionAiResponse processAudioWithRetry(
            org.springframework.core.io.Resource audio
    ) {
        try {
            return processTransactionAudioUseCase.execute(audio);
        } catch (org.springframework.web.client.ResourceAccessException exception) {
            log.warn("Transient AI call failure. Retrying once.", exception);

            try {
                return processTransactionAudioUseCase.execute(audio);
            } catch (org.springframework.web.client.ResourceAccessException retryException) {
                throw new AiProcessingException(
                        "AI provider is unavailable after retry.",
                        retryException
                );
            }
        } catch (AiProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiProcessingException(
                    "Failed to process transaction audio.",
                    exception
            );
        }
    }

}
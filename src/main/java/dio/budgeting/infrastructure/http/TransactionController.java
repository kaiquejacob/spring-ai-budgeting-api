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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionController.class);

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ProcessTransactionAudioUseCase processTransactionAudioUseCase;
    private final dio.budgeting.application.PendingTransactionService pendingTransactionService;
    private final SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private final ListRecentTransactionsUseCase listRecentTransactionsUseCase;
    private final ConversationSessionService conversationSessionService;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ProcessTransactionAudioUseCase processTransactionAudioUseCase,
            PendingTransactionService pendingTransactionService,
            SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase,
            ListRecentTransactionsUseCase listRecentTransactionsUseCase,
            ConversationSessionService conversationSessionService
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
    public TransactionResponse createTransaction(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody TransactionRequest request
    ) {
        var transaction = persistTransactionUseCase.execute(
                request.toInput(userId)
        );

        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Category category
    ) {
        return listTransactionsByCategoryUseCase
                .execute(userId, category)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransactionAiHttpResponse processAudio(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = conversationSessionService.getOrCreate(sessionId, userId);

        var rawAiResponse = processAudioWithRetry(file.getResource());
        var aiResponse = applySessionContext(rawAiResponse, session);

        return switch (aiResponse.intent()) {
            case CREATE_TRANSACTION -> {
                var token = pendingTransactionService.save(aiResponse);

                session.setPendingProposal(aiResponse);
                conversationSessionService.save(session);

                yield TransactionAiHttpResponse.pending(
                        token,
                        aiResponse,
                        session.id()
                );
            }
            case CONFIRM_PENDING -> confirmPendingProposal(session);
            case LIST_TRANSACTIONS -> handleListByCategory(
                    userId,
                    aiResponse,
                    session
            );
            case SUM_TRANSACTIONS_BY_CATEGORY -> handleSummary(
                    userId,
                    aiResponse,
                    session
            );
            case LIST_RECENT_TRANSACTIONS -> {
                var transactions = listRecentTransactionsUseCase.execute(userId);

                yield TransactionAiHttpResponse.recent(
                        transactions,
                        session.id()
                );
            }
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST ->
                    TransactionAiHttpResponse.clarification(
                            aiResponse,
                            session.id()
                    );
        };
    }

    @PostMapping("/{token}/confirm")
    public ResponseEntity<TransactionAiHttpResponse> confirmTransaction(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID token,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var proposal = pendingTransactionService.consume(token);

        var output = persistTransactionUseCase.execute(
                new PersistTransactionInput(
                        userId,
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI
                )
        );

        if (sessionId != null) {
            var session = conversationSessionService.getOrCreate(
                    sessionId,
                    userId
            );

            if (isSameProposal(session.pendingProposal(), proposal)) {
                session.clearPendingProposal();
                conversationSessionService.save(session);
            }
        }

        return ResponseEntity.ok(
                TransactionAiHttpResponse.created(output, sessionId)
        );
    }

    private TransactionAiHttpResponse confirmPendingProposal(
            ConversationSession session
    ) {
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
                        session.userId(),
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI
                )
        );

        session.clearPendingProposal();
        conversationSessionService.save(session);

        return TransactionAiHttpResponse.created(output, session.id());
    }

    private TransactionAiHttpResponse handleListByCategory(
            UUID userId,
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var category = aiResponse.category();

        if (category == null) {
            return TransactionAiHttpResponse.clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            "Informe a categoria para listar as transações."
                    ),
                    session.id()
            );
        }

        session.setLastCategory(category);
        conversationSessionService.save(session);

        var transactions = listTransactionsByCategoryUseCase.execute(
                userId,
                category
        );

        return TransactionAiHttpResponse.listed(
                transactions,
                session.id()
        );
    }

    private TransactionAiHttpResponse handleSummary(
            UUID userId,
            TransactionAiResponse aiResponse,
            ConversationSession session
    ) {
        var category = aiResponse.category();
        var period = aiResponse.period();

        if (category == null || period == null) {
            return TransactionAiHttpResponse.clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            "Informe a categoria e o período para calcular o total."
                    ),
                    session.id()
            );
        }

        session.setLastCategory(category);
        session.setLastPeriod(period);
        conversationSessionService.save(session);

        var total = sumTransactionsByCategoryUseCase.execute(
                userId,
                category,
                period
        );

        return TransactionAiHttpResponse.summarized(
                category,
                period,
                total,
                session.id()
        );
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

    private TransactionAiResponse processAudioWithRetry(
            org.springframework.core.io.Resource audio
    ) {
        try {
            return processTransactionAudioUseCase.execute(audio);
        } catch (ResourceAccessException exception) {
            log.warn("Transient AI call failure. Retrying once.", exception);

            try {
                return processTransactionAudioUseCase.execute(audio);
            } catch (ResourceAccessException retryException) {
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

    private boolean isSameProposal(
            TransactionAiResponse pendingProposal,
            TransactionAiResponse confirmedProposal
    ) {
        if (pendingProposal == null) {
            return false;
        }

        return pendingProposal.description()
                .equals(confirmedProposal.description())
                && pendingProposal.amountInCents()
                .equals(confirmedProposal.amountInCents());
    }
}
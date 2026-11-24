package dio.budgeting.infrastructure.http;

import dio.budgeting.application.*;
import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import dio.budgeting.infrastructure.http.request.CategorySuggestionRequest;
import dio.budgeting.infrastructure.http.request.FinancialChatRequest;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.CategorySuggestionResponse;
import dio.budgeting.infrastructure.http.response.FinancialChatResponse;
import dio.budgeting.infrastructure.http.response.TransactionAiHttpResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import dio.budgeting.infrastructure.http.response.ErrorResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@Validated
@Tag(name = "Transações", description = "Operações para cadastro e consulta de transações.")
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
    private final org.springframework.ai.chat.client.ChatClient financialChatClient;
    private final org.springframework.ai.chat.client.ChatClient financialToolChatClient;
    private final SuggestCategoryUseCase suggestCategoryUseCase;
    private final dio.budgeting.application.ai.ProcessReceiptImageUseCase processReceiptImageUseCase;
    private final dio.budgeting.application.ai.AiProviderComparisonService aiProviderComparisonService;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ProcessTransactionAudioUseCase processTransactionAudioUseCase,
            PendingTransactionService pendingTransactionService,
            SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase,
            ListRecentTransactionsUseCase listRecentTransactionsUseCase,
            ConversationSessionService conversationSessionService,
            @Qualifier("financialChatClient")
            org.springframework.ai.chat.client.ChatClient financialChatClient,
            @Qualifier("financialToolChatClient")
            org.springframework.ai.chat.client.ChatClient financialToolChatClient,
            SuggestCategoryUseCase suggestCategoryUseCase,
            dio.budgeting.application.ai.ProcessReceiptImageUseCase processReceiptImageUseCase,
            dio.budgeting.application.ai.AiProviderComparisonService aiProviderComparisonService
    ) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.processTransactionAudioUseCase = processTransactionAudioUseCase;
        this.pendingTransactionService = pendingTransactionService;
        this.sumTransactionsByCategoryUseCase = sumTransactionsByCategoryUseCase;
        this.listRecentTransactionsUseCase = listRecentTransactionsUseCase;
        this.conversationSessionService = conversationSessionService;
        this.financialChatClient = financialChatClient;
        this.financialToolChatClient = financialToolChatClient;
        this.suggestCategoryUseCase = suggestCategoryUseCase;
        this.processReceiptImageUseCase = processReceiptImageUseCase;
        this.aiProviderComparisonService = aiProviderComparisonService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar uma nova transação")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transação criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionResponse createTransaction(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody TransactionRequest request
    ) {
        var transaction = persistTransactionUseCase.execute(
                request.toInput(userId)
        );

        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    @Operation(summary = "Listar transações por categoria")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transações encontradas"),
            @ApiResponse(responseCode = "400", description = "Categoria ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<TransactionResponse> readTransactions(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
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
    @Operation(summary = "Processar transação por áudio", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação processada"),
            @ApiResponse(responseCode = "400", description = "Arquivo ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionAiHttpResponse processAudio(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = conversationSessionService.getOrCreate(sessionId, userId);

        var rawAiResponse = processAudioWithRetry(file.getResource());
        var aiResponse = applySessionContext(rawAiResponse, session);

        return switch (aiResponse.intent()) {
            case CREATE_TRANSACTION -> {
                var token = pendingTransactionService.save(userId, aiResponse);

                session.setPendingProposal(aiResponse);
                session.setPendingConfirmationToken(token);
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
    @Operation(summary = "Confirmar uma proposta pendente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação confirmada"),
            @ApiResponse(responseCode = "400", description = "Token ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Proposta pendente não encontrada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransactionAiHttpResponse> confirmTransaction(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID token,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = sessionId == null
                ? null
                : conversationSessionService.getOrCreate(sessionId, userId);

        var proposal = pendingTransactionService.consume(token, userId);

        var output = persistTransactionUseCase.execute(
                new PersistTransactionInput(
                        userId,
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI
                )
        );

        if (session != null
                && token.equals(session.pendingConfirmationToken())) {

            session.clearPendingProposal();
            conversationSessionService.save(session);
        }

        return ResponseEntity.ok(
                TransactionAiHttpResponse.created(
                        output,
                        session == null ? null : session.id()
                )
        );
    }

    private TransactionAiHttpResponse confirmPendingProposal(
            ConversationSession session
    ) {
        var token = session.pendingConfirmationToken();

        if (session.pendingProposal() == null || token == null) {
            return TransactionAiHttpResponse.clarification(
                    new TransactionAiResponse(
                            TransactionIntent.CLARIFICATION_NEEDED,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Não há uma proposta válida para confirmar. Gere uma nova proposta."
                    ),
                    session.id()
            );
        }

        var proposal = pendingTransactionService.consume(
                token,
                session.userId()
        );

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

        return TransactionAiHttpResponse.created(
                output,
                session.id()
        );
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
                            "Informe a categoria para listar as transações.",
                            "Não há uma proposta válida para confirmar. Gere uma nova proposta."),
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
                            "Informe a categoria e o período para calcular o total.",
                            "Não há uma proposta válida para confirmar. Gere uma nova proposta."),
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
                aiResponse.messageToUser(),
                "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
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

    @PostMapping("/ai/chat")
    @Operation(summary = "Enviar mensagem ao assistente", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta do assistente"),
            @ApiResponse(responseCode = "400", description = "Mensagem ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public FinancialChatResponse chat(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @Valid @RequestBody FinancialChatRequest request
    ) {
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException(
                    "Informe uma mensagem para o assistente."
            );
        }

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
                .user(request.message())
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .tools(tools)
                .call()
                .content();

        return new FinancialChatResponse(answer, session.id());
    }

    @PostMapping("/suggest-category")
    @Operation(summary = "Sugerir categoria para uma descrição", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria sugerida"),
            @ApiResponse(responseCode = "400", description = "Descrição ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CategorySuggestionResponse suggestCategory(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CategorySuggestionRequest request
    ) {
        var category = suggestCategoryUseCase.execute(request.description());

        return new CategorySuggestionResponse(category);
    }

    @PostMapping(value = "/ai/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extrair transação de um recibo", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo processado"),
            @ApiResponse(responseCode = "400", description = "Arquivo ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionAiHttpResponse processReceipt(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = conversationSessionService.getOrCreate(sessionId, userId);

        var aiResponse = processReceiptImageUseCase.execute(
                file.getResource(),
                file.getContentType()
        );

        if (aiResponse.intent() != TransactionIntent.CREATE_TRANSACTION) {
            return TransactionAiHttpResponse.clarification(
                    aiResponse,
                    session.id()
            );
        }

        var token = pendingTransactionService.save(userId, aiResponse);

        session.setPendingProposal(aiResponse);
        session.setPendingConfirmationToken(token);
        conversationSessionService.save(session);

        return TransactionAiHttpResponse.pending(
                token,
                aiResponse,
                session.id()
        );
    }

    @PostMapping("/ai/compare")
    @Operation(summary = "Comparar respostas dos provedores de IA", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparação concluída"),
            @ApiResponse(responseCode = "400", description = "Mensagem ou usuário inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public dio.budgeting.application.ai.AiProviderComparisonService.ComparisonResult compareProviders(
            @Parameter(name = "X-User-Id", description = "Identificador UUID do usuário", required = true)
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody FinancialChatRequest request
    ) {
        return aiProviderComparisonService.compare(request.message());
    }
}
package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.CategorySuggestionRequest;
import dio.budgeting.infrastructure.http.request.FinancialChatRequest;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.CategorySuggestionResponse;
import dio.budgeting.infrastructure.http.response.FinancialChatResponse;
import dio.budgeting.infrastructure.http.response.TransactionAiHttpResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
import dio.budgeting.application.proposal.ConfirmPendingTransactionUseCase;
import dio.budgeting.application.conversation.ConversationSessionService;
import dio.budgeting.application.conversation.FinancialChatUseCase;
import dio.budgeting.application.assistant.HandleAiIntentUseCase;
import dio.budgeting.application.transaction.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.transaction.PersistTransactionUseCase;
import dio.budgeting.application.assistant.ProcessTransactionAudioWithRetryUseCase;
import dio.budgeting.application.assistant.SuggestCategoryUseCase;
import dio.budgeting.application.ai.AiProviderComparisonService;
import dio.budgeting.application.assistant.ProcessReceiptImageUseCase;

@RestController
@RequestMapping("/transactions")
@Validated
@Tag(name = "Transações", description = "Operações para cadastro e consulta de transações.")
public class TransactionController {

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ProcessTransactionAudioWithRetryUseCase processTransactionAudioWithRetryUseCase;
    private final HandleAiIntentUseCase handleAiIntentUseCase;
    private final ConversationSessionService conversationSessionService;
    private final FinancialChatUseCase financialChatUseCase;
    private final ConfirmPendingTransactionUseCase confirmPendingTransactionUseCase;
    private final SuggestCategoryUseCase suggestCategoryUseCase;
    private final dio.budgeting.application.assistant.ProcessReceiptImageUseCase processReceiptImageUseCase;
    private final dio.budgeting.application.ai.AiProviderComparisonService aiProviderComparisonService;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ProcessTransactionAudioWithRetryUseCase processTransactionAudioWithRetryUseCase,
            HandleAiIntentUseCase handleAiIntentUseCase,
            ConversationSessionService conversationSessionService,
            FinancialChatUseCase financialChatUseCase,
            ConfirmPendingTransactionUseCase confirmPendingTransactionUseCase,
            SuggestCategoryUseCase suggestCategoryUseCase,
            dio.budgeting.application.assistant.ProcessReceiptImageUseCase processReceiptImageUseCase,
            dio.budgeting.application.ai.AiProviderComparisonService aiProviderComparisonService
    ) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.processTransactionAudioWithRetryUseCase = processTransactionAudioWithRetryUseCase;
        this.handleAiIntentUseCase = handleAiIntentUseCase;
        this.conversationSessionService = conversationSessionService;
        this.financialChatUseCase = financialChatUseCase;
        this.confirmPendingTransactionUseCase = confirmPendingTransactionUseCase;
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
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID userId,
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
            @ApiResponse(responseCode = "400", description = "Categoria inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<TransactionResponse> readTransactions(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID userId,
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
            @ApiResponse(responseCode = "400", description = "Arquivo inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionAiHttpResponse processAudio(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = conversationSessionService.getOrCreate(sessionId, userId);

        var rawAiResponse = processTransactionAudioWithRetryUseCase.execute(
                file.getResource()
        );
        var result = handleAiIntentUseCase.execute(
                userId,
                rawAiResponse,
                session
        );
        return TransactionAiHttpResponse.from(result);
    }

    @PostMapping("/{token}/confirm")
    @Operation(summary = "Confirmar uma proposta pendente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação confirmada"),
            @ApiResponse(responseCode = "400", description = "Token inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Proposta pendente não encontrada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransactionAiHttpResponse> confirmTransaction(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID userId,
            @PathVariable UUID token,
            @RequestParam(value = "sessionId", required = false) UUID sessionId
    ) {
        var session = sessionId == null
                ? null
                : conversationSessionService.getOrCreate(sessionId, userId);

        var output = confirmPendingTransactionUseCase.execute(token, userId);

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

    @PostMapping("/ai/chat")
    @Operation(summary = "Enviar mensagem ao assistente", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta do assistente"),
            @ApiResponse(responseCode = "400", description = "Mensagem inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public FinancialChatResponse chat(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID userId,
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @Valid @RequestBody FinancialChatRequest request
    ) {
        var result = financialChatUseCase.execute(
                userId,
                sessionId,
                request.message()
        );
        return new FinancialChatResponse(result.answer(), result.sessionId());
    }

    @PostMapping("/suggest-category")
    @Operation(summary = "Sugerir categoria para uma descrição", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria sugerida"),
            @ApiResponse(responseCode = "400", description = "Descrição inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CategorySuggestionResponse suggestCategory(
            @Valid @RequestBody CategorySuggestionRequest request
    ) {
        var category = suggestCategoryUseCase.execute(request.description());

        return new CategorySuggestionResponse(category);
    }

    @PostMapping(value = "/ai/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extrair transação de um recibo", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo processado"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Serviço de IA indisponível", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TransactionAiHttpResponse processReceipt(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID userId,
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

        var result = handleAiIntentUseCase.execute(
                userId,
                aiResponse,
                session
        );
        return TransactionAiHttpResponse.from(result);
    }

    @PostMapping("/ai/compare")
    @Operation(summary = "Comparar respostas dos provedores de IA", tags = "Assistente IA")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comparação concluída"),
            @ApiResponse(responseCode = "400", description = "Mensagem inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public dio.budgeting.application.ai.AiProviderComparisonService.ComparisonResult compareProviders(
            @Valid @RequestBody FinancialChatRequest request
    ) {
        return aiProviderComparisonService.compare(request.message());
    }
}
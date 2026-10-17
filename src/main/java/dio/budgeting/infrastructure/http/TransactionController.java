package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PendingTransactionService;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.ProcessTransactionAudioUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionAiHttpResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ProcessTransactionAudioUseCase processTransactionAudioUseCase;
    private final PendingTransactionService pendingTransactionService;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ProcessTransactionAudioUseCase processTransactionAudioUseCase, PendingTransactionService pendingTransactionService
    ) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.processTransactionAudioUseCase = processTransactionAudioUseCase;
        this.pendingTransactionService = pendingTransactionService;
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
    public TransactionAiHttpResponse processAudio(@RequestParam("file") MultipartFile file) {
        var aiResponse = processTransactionAudioUseCase.execute(file.getResource());

        return switch (aiResponse.intent()) {
            case CREATE_TRANSACTION -> {
                var token = pendingTransactionService.save(aiResponse);
                yield TransactionAiHttpResponse.pending(token, aiResponse);
            }
            case LIST_TRANSACTIONS -> TransactionAiHttpResponse.listed(
                    listTransactionsByCategoryUseCase.execute(aiResponse.category())
            );
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST ->
                    TransactionAiHttpResponse.clarification(aiResponse);
        };
    }

    @PostMapping("/ai/{token}/confirm")
    public TransactionAiHttpResponse confirmTransaction(@PathVariable UUID token) {
        var proposal = pendingTransactionService.consume(token);

        var output = persistTransactionUseCase.execute(
                new PersistTransactionInput(
                        proposal.description(),
                        proposal.amountInCents(),
                        proposal.category(),
                        TransactionSource.AI
                )
        );

        return TransactionAiHttpResponse.created(output);
    }

}
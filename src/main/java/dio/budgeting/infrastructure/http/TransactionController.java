package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.ProcessTransactionAudioUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionAiHttpResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ProcessTransactionAudioUseCase processTransactionAudioUseCase;

    public TransactionController(
            PersistTransactionUseCase persistTransactionUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ProcessTransactionAudioUseCase processTransactionAudioUseCase
    ) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.processTransactionAudioUseCase = processTransactionAudioUseCase;
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
            case CREATE_TRANSACTION -> TransactionAiHttpResponse.created(
                    persistTransactionUseCase.execute(new PersistTransactionInput(
                            aiResponse.description(),
                            aiResponse.amountInCents(),
                            aiResponse.category()
                    ))
            );
            case LIST_TRANSACTIONS -> TransactionAiHttpResponse.listed(
                    listTransactionsByCategoryUseCase.execute(aiResponse.category())
            );
            case CLARIFICATION_NEEDED, UNSUPPORTED_REQUEST ->
                    TransactionAiHttpResponse.from(aiResponse);
        };
    }

}
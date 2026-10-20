package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionSource;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.UUID;

public record PersistTransactionInput(
        UUID userId,
        String description,
        Long amountInCents,
        Category category,
        TransactionSource source
) {
}
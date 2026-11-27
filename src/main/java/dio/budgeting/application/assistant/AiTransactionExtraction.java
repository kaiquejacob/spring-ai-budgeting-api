package dio.budgeting.application.assistant;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionType;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

record AiTransactionExtraction(
        TransactionIntent intent,
        String description,
        Long amountInCents,
        Category category,
        TransactionAiResponse.AiPeriod period,
        String messageToUser,
        String s,
        JsonNode type,
        JsonNode occurredOn
) {
    TransactionAiResponse toResponse(ZonedDateTime reference) {
        if (intent != TransactionIntent.CREATE_TRANSACTION) {
            return new TransactionAiResponse(intent, description, amountInCents, category, period, messageToUser, s);
        }

        TransactionType transactionType;
        try {
            transactionType = type == null ? null : TransactionType.valueOf(type.asString(""));
        } catch (IllegalArgumentException exception) {
            return clarification("Informe um tipo de transação válido: INCOME ou EXPENSE.");
        }

        LocalDate date = null;
        if (occurredOn != null) {
            var text = occurredOn.asString("");
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                return clarification("Informe uma data de ocorrência válida no formato yyyy-MM-dd.");
            }
            try {
                date = LocalDate.parse(text);
            } catch (DateTimeParseException exception) {
                return clarification("Informe uma data de ocorrência válida no formato yyyy-MM-dd.");
            }
            var today = reference.toLocalDate();
            if (date.isAfter(today) || date.isBefore(today.minusYears(10))) {
                return clarification("Informe uma data de ocorrência entre hoje e os últimos 10 anos.");
            }
        }

        var occurredAt = date == null ? null : date.atStartOfDay(reference.getZone()).toInstant();
        return new TransactionAiResponse(intent, description, amountInCents, category, period,
                messageToUser, s, transactionType, occurredAt);
    }

    private static TransactionAiResponse clarification(String message) {
        return new TransactionAiResponse(TransactionIntent.CLARIFICATION_NEEDED, null, null,
                null, null, message, "Não há uma proposta válida para confirmar. Gere uma nova proposta.");
    }
}

package dio.budgeting;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionAiValidationTest {

    @Test
    void should_return_clarification_when_intent_is_null() {
        var response = new TransactionAiResponse(
                null,
                "Mercado",
                8000L,
                null,
                null
        );

        assertThat(response.intent()).isNull();
    }

    @Test
    void should_use_amount_in_cents_for_creation() {
        var response = new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Mercado",
                8000L,
                null,
                null
        );

        assertThat(response.amountInCents()).isEqualTo(8000L);
    }

    @Test
    void should_not_fill_amount_for_listing() {
        var response = new TransactionAiResponse(
                TransactionIntent.LIST_TRANSACTIONS,
                null,
                null,
                null,
                null
        );

        assertThat(response.intent()).isEqualTo(TransactionIntent.LIST_TRANSACTIONS);
        assertThat(response.amountInCents()).isNull();
    }
}
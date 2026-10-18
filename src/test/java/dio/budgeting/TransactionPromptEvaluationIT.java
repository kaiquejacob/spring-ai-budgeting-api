package dio.budgeting;

import dio.budgeting.application.ProcessTransactionAudioUseCase;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("groq-evaluation")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class TransactionPromptEvaluationIT {

    @Autowired
    private ProcessTransactionAudioUseCase processTransactionAudioUseCase;

    @ParameterizedTest(name = "[{index}] {0} deve ser {1}")
    @CsvSource({
            "Gastei 80 reais no mercado, CREATE_TRANSACTION, GROCERIES, 8000, ",
            "Comprei remédio por quarenta reais, CREATE_TRANSACTION, PHARMA, 4000, ",
            "Paguei 120 reais de gasolina, CREATE_TRANSACTION, AUTO, 12000, ",
            "Quanto gastei com mercado neste mês?, SUM_TRANSACTIONS_BY_CATEGORY, GROCERIES, , CURRENT_MONTH",
            "Qual o total gasto com mercado até hoje?, SUM_TRANSACTIONS_BY_CATEGORY, GROCERIES, , ALL_TIME",
            "Quais foram meus gastos com farmácia?, LIST_TRANSACTIONS, PHARMA, , ",
            "Mostre minhas últimas transações, LIST_RECENT_TRANSACTIONS, , , ",
            "Gastei dinheiro, CLARIFICATION_NEEDED, , , ",
            "Transfira dinheiro para minha mãe, UNSUPPORTED_REQUEST, , , "
    })
    void should_interpret_financial_request(
            String userMessage,
            TransactionIntent expectedIntent,
            Category expectedCategory,
            Long expectedAmountInCents,
            TransactionAiResponse.AiPeriod expectedPeriod
    ) throws InterruptedException {

        Thread.sleep(5000);

        var response = processTransactionAudioUseCase.interpret(userMessage);

        assertThat(response.intent()).isEqualTo(expectedIntent);

        if (expectedCategory != null) {
            assertThat(response.category()).isEqualTo(expectedCategory);
        } else {
            assertThat(response.category()).isNull();
        }

        if (expectedAmountInCents != null) {
            assertThat(response.amountInCents()).isEqualTo(expectedAmountInCents);
        }

        if (expectedPeriod != null) {
            assertThat(response.period()).isEqualTo(expectedPeriod);
        }
    }
}
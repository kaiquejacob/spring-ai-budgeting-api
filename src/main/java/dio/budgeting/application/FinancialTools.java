package dio.budgeting.application;

import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.UUID;

public class FinancialTools {

    private final UUID userId;
    private final SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final ListRecentTransactionsUseCase listRecentTransactionsUseCase;

    public FinancialTools(
            UUID userId,
            SumTransactionsByCategoryUseCase sumTransactionsByCategoryUseCase,
            ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
            ListRecentTransactionsUseCase listRecentTransactionsUseCase
    ) {
        this.userId = userId;
        this.sumTransactionsByCategoryUseCase = sumTransactionsByCategoryUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.listRecentTransactionsUseCase = listRecentTransactionsUseCase;
    }

    @Tool(
            name = "sum-transactions-by-category",
            description = """
                    Calcula o total gasto em uma categoria financeira.
                    Use apenas para perguntas sobre total, soma ou valor acumulado.
                    """
    )
    public double sumByCategory(
            @ToolParam(description = "Categoria: GROCERIES, PHARMA ou AUTO")
            Category category,
            @ToolParam(description = """
                    Período: TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK,
                    CURRENT_MONTH, LAST_MONTH ou ALL_TIME
                    """)
            TransactionAiResponse.AiPeriod period
    ) {
        var totalInCents = sumTransactionsByCategoryUseCase.execute(
                userId,
                category,
                period
        );

        return totalInCents / 100.0;
    }

    @Tool(
            name = "list-transactions-by-category",
            description = """
                    Lista as transações de uma categoria financeira.
                    Use quando o usuário quiser ver quais foram as transações.
                    """
    )
    public List<TransactionOutput> listByCategory(
            @ToolParam(description = "Categoria: GROCERIES, PHARMA ou AUTO")
            Category category
    ) {
        return listTransactionsByCategoryUseCase.execute(userId, category);
    }

    @Tool(
            name = "list-recent-transactions",
            description = "Lista as últimas transações do usuário."
    )
    public List<TransactionOutput> listRecent() {
        return listRecentTransactionsUseCase.execute(userId);
    }
}
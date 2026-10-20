package dio.budgeting.application;

import dio.budgeting.application.ai.CategorySuggestion;
import dio.budgeting.domain.Category;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SuggestCategoryUseCase {

    private final ChatClient financialChatClient;

    public SuggestCategoryUseCase(
            @Qualifier("financialChatClient") ChatClient financialChatClient
    ) {
        this.financialChatClient = financialChatClient;
    }

    public Category execute(String description) {
        var suggestion = financialChatClient
                .prompt()
                .user("""
                        Sugira a categoria mais adequada para esta transação:
                        "%s"

                        Responda apenas com uma categoria válida:
                        GROCERIES, PHARMA ou AUTO.
                        """.formatted(description))
                .call()
                .entity(CategorySuggestion.class);

        if (suggestion == null || suggestion.category() == null) {
            return null;
        }

        return suggestion.category();
    }
}
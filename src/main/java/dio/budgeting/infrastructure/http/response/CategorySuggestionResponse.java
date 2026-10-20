package dio.budgeting.infrastructure.http.response;

import dio.budgeting.domain.Category;

public record CategorySuggestionResponse(
        Category suggestedCategory
) {
}
package dio.budgeting.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        String apiKey,
        String baseUrl,
        String model
) {
}
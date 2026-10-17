package dio.budgeting.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        Chat chat,
        Transcription transcription
) {

    public record Chat(
            String model,
            double temperature
    ) {
    }

    public record Transcription(
            String model
    ) {
    }
}
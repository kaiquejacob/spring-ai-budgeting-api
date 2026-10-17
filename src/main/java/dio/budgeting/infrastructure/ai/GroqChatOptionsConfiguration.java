package dio.budgeting.infrastructure.ai;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroqChatOptionsConfiguration {

    @Bean
    public OpenAiChatOptions groqChatOptions(AiProperties aiProperties) {
        return OpenAiChatOptions.builder()
                .model(aiProperties.chat().model())
                .temperature(aiProperties.chat().temperature())
                .build();
    }
}
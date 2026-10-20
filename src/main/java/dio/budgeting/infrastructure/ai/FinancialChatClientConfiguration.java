package dio.budgeting.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.Charset;

@Configuration
public class FinancialChatClientConfiguration {

    @Bean
    public ChatClient financialChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/system-message.st") Resource systemPrompt,
            OpenAiChatOptions groqChatOptions
    ) throws IOException {

        return chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultOptions(groqChatOptions)
                .build();
    }

    @Bean
    public ChatClient financialToolChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/chat-system-message.st") Resource chatSystemPrompt,
            OpenAiChatOptions groqChatOptions
    ) throws IOException {
        return chatClientBuilder
                .defaultSystem(
                        chatSystemPrompt.getContentAsString(Charset.defaultCharset())
                )
                .defaultOptions(groqChatOptions)
                .build();
    }
}
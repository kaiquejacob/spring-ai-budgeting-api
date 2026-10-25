package dio.budgeting.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class FinancialChatClientConfiguration {

    @Bean
    public ChatMemory financialChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient financialChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/system-message.st")
            Resource systemPrompt,
            OpenAiChatOptions groqChatOptions
    ) throws IOException {
        return chatClientBuilder.clone()
                .defaultSystem(
                        systemPrompt.getContentAsString(
                                StandardCharsets.UTF_8
                        )
                )
                .defaultOptions(groqChatOptions)
                .build();
    }

    @Bean
    public ChatClient financialToolChatClient(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/chat-system-message.st")
            Resource chatSystemPrompt,
            OpenAiChatOptions groqChatOptions,
            @Qualifier("financialChatMemory")
            ChatMemory chatMemory
    ) throws IOException {
        return chatClientBuilder.clone()
                .defaultSystem(
                        chatSystemPrompt.getContentAsString(
                                StandardCharsets.UTF_8
                        )
                )
                .defaultOptions(groqChatOptions)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build()
                )
                .build();
    }
}
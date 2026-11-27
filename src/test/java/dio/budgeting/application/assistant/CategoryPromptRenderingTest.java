package dio.budgeting.application.assistant;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.ai.FinancialChatClientConfiguration;
import dio.budgeting.infrastructure.ai.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CategoryPromptRenderingTest {

    @Test
    void rendersSystemPromptAndCategorySuggestionBeforeCallingChatModel() throws Exception {
        var resource = new ClassPathResource("prompts/system-message.st");
        assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).contains("{categories}");
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("""
                        {"category":"GROCERIES"}
                        """)))));
        var client = new FinancialChatClientConfiguration().financialChatClient(
                ChatClient.builder(model), resource, OpenAiChatOptions.builder().build());

        assertThat(new SuggestCategoryUseCase(client).execute("Compra no mercado"))
                .isEqualTo(Category.GROCERIES);
        var request = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(request.capture());
        assertThat(request.getValue().getSystemMessage().getText())
                .contains("Escolha apenas uma categoria existente: " + expectedCategories() + ".")
                .doesNotContain("{categories}");
        assertThat(request.getValue().getUserMessage().getText())
                .contains("\"Compra no mercado\"", "Responda apenas com uma categoria válida:\n" + expectedCategories() + ".")
                .doesNotContain("%s", "{categories}");
    }

    @Test
    void rendersReceiptPromptInTheGeminiRequestAndKeepsTheJsonExample() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gemini.example/models/test:generateContent"))
                .andExpect(jsonPath("$.contents[0].parts[0].text", allOf(
                        containsString("Use apenas " + expectedCategories() + "."),
                        containsString("\"category\": \"GROCERIES\""),
                        not(containsString("%s")),
                        not(containsString("{categories}")))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var useCase = new ProcessReceiptImageUseCase(
                new GeminiProperties("test", "https://gemini.example", "test"),
                builder, JsonMapper.builder().build());
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", image);

        useCase.execute(new ByteArrayResource(image.toByteArray()), "image/png");

        server.verify();
    }

    private static String expectedCategories() {
        var names = Arrays.stream(Category.values()).map(Enum::name).toList();
        return String.join(", ", names.subList(0, names.size() - 1)) + " ou " + names.getLast();
    }
}

package dio.budgeting.infrastructure.http;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.domain.TransactionType;
import dio.budgeting.infrastructure.persistence.repository.ConversationSessionJpaRepository;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dio.budgeting.infrastructure.http.AuthenticatedTestSession.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.gemini.base-url=https://gemini.example", "app.gemini.model=test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TransactionCharacterizationTest.MutableClockConfiguration.class, AiProposalDatesTest.GeminiStubConfig.class})
class AiProposalDatesTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String YESTERDAY = "2026-11-22T03:00:00Z";
    private static final String CREATE_FIELDS = """
            "intent":"CREATE_TRANSACTION","description":"Compra","amountInCents":2590,"category":"GROCERIES"
            """;

    @Autowired private MockMvc mvc;
    @Autowired private TransactionEntityRepository transactions;
    @Autowired private ConversationSessionJpaRepository sessions;
    @Autowired private TransactionCharacterizationTest.MutableClock clock;
    @Autowired private GeminiStub gemini;
    @MockitoBean private OpenAiChatModel groq;
    @MockitoBean private TranscriptionModel transcription;

    @BeforeEach
    void reset() {
        transactions.deleteAll();
        sessions.deleteAll();
        clock.reset();
        gemini.server().reset();
    }

    @ParameterizedTest(name = "confirm by audio = {0}")
    @ValueSource(booleans = {false, true})
    void yesterdaySurvivesMidnightAndSessionReload(boolean confirmByAudio) throws Exception {
        clock.advanceSeconds(43_170);
        when(transcription.transcribe(any(Resource.class))).thenReturn("Recebi 25,90 ontem", "confirma");
        when(groq.call(any(Prompt.class))).thenReturn(
                groqResponse("{" + CREATE_FIELDS + ",\"type\":\"INCOME\",\"occurredOn\":\"2026-11-22\"}"),
                groqResponse("{\"intent\":\"CONFIRM_PENDING\"}"));

        var proposal = mvc.perform(multipart("/transactions/ai").file(audio()).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedTransaction.type").value("INCOME"))
                .andExpect(jsonPath("$.proposedTransaction.occurredAt").value(YESTERDAY))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(proposal, "$.confirmationToken");
        String sessionId = JsonPath.read(proposal, "$.sessionId");
        assertThat(transactions.count()).isZero();
        assertThat(sessions.findById(UUID.fromString(sessionId)).orElseThrow().getPendingConfirmationToken())
                .isEqualTo(UUID.fromString(token));

        clock.advanceSeconds(60);
        var request = confirmByAudio
                ? multipart("/transactions/ai").file(audio()).param("sessionId", sessionId)
                : post("/transactions/{token}/confirm", token).param("sessionId", sessionId);
        var confirmed = mvc.perform(request.with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransaction.type").value("INCOME"))
                .andExpect(jsonPath("$.createdTransaction.occurredAt").value(YESTERDAY))
                .andReturn().getResponse().getContentAsString();

        assertThat(transactions.findAll()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(transaction.getOccurredAt()).isEqualTo(Instant.parse(YESTERDAY));
            assertThat(transaction.getCreatedAt()).isEqualTo(clock.instant());
        });
        assertThat(sessions.findById(UUID.fromString(sessionId)).orElseThrow().getPendingConfirmationToken()).isNull();
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(groq, times(confirmByAudio ? 2 : 1)).call(prompts.capture());
        assertThat(prompts.getAllValues().getFirst().getUserMessage().getText())
                .contains("2026-11-23", "Recebi 25,90 ontem");
        if (confirmByAudio) {
            assertThat(prompts.getValue().getUserMessage().getText()).contains("2026-11-24", "confirma");
        } else {
            System.out.println("Proposal JSON: " + proposal);
            System.out.println("Confirmed JSON: " + confirmed);
        }
    }

    @Test
    void extractsExplicitReceiptDateAndPreservesItThroughConfirmation() throws Exception {
        clock.advanceSeconds(43_230);
        geminiReply("{" + CREATE_FIELDS + ",\"type\":\"EXPENSE\",\"occurredOn\":\"2026-11-20\"}", "2026-11-24");
        var proposal = mvc.perform(multipart("/transactions/ai/receipt").file(receipt()).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedTransaction.type").value("EXPENSE"))
                .andExpect(jsonPath("$.proposedTransaction.occurredAt").value("2026-11-20T03:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        gemini.server().verify();
        assertThat(transactions.count()).isZero();
        String token = JsonPath.read(proposal, "$.confirmationToken");
        mvc.perform(post("/transactions/{token}/confirm", token).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransaction.occurredAt").value("2026-11-20T03:00:00Z"));
    }

    @Test
    void omittedFieldsUseExpenseAndTheConfirmationInstant() throws Exception {
        when(transcription.transcribe(any(Resource.class))).thenReturn("Gastei 25,90");
        when(groq.call(any(Prompt.class))).thenReturn(groqResponse("{" + CREATE_FIELDS + "}"));
        var proposal = mvc.perform(multipart("/transactions/ai").file(audio()).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedTransaction.type").value("EXPENSE"))
                .andExpect(jsonPath("$.proposedTransaction.occurredAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(transactions.count()).isZero();
        clock.advanceSeconds(60);
        String token = JsonPath.read(proposal, "$.confirmationToken");
        mvc.perform(post("/transactions/{token}/confirm", token).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransaction.type").value("EXPENSE"))
                .andExpect(jsonPath("$.createdTransaction.occurredAt").value(clock.instant().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"occurredOn\":\"2026-11-24\"", "\"occurredOn\":\"2016-11-22\"",
            "\"occurredOn\":\"2026-02-30\"", "\"occurredOn\":\"22/11/2026\"",
            "\"type\":\"TRANSFER\"", "\"type\":1", "\"type\":null", "\"occurredOn\":null"
    })
    void invalidGroqFieldsAskForClarificationWithoutToken(String invalidField) throws Exception {
        when(transcription.transcribe(any(Resource.class))).thenReturn("Compra");
        when(groq.call(any(Prompt.class))).thenReturn(groqResponse("{" + CREATE_FIELDS + "," + invalidField + "}"));
        mvc.perform(multipart("/transactions/ai").file(audio()).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CLARIFICATION_NEEDED"))
                .andExpect(jsonPath("$.confirmationToken").doesNotExist());
        assertThat(transactions.count()).isZero();
        assertThat(sessions.findAll()).singleElement()
                .satisfies(session -> assertThat(session.getPendingConfirmationToken()).isNull());
    }

    @Test
    void invalidGeminiFieldsAskForClarificationWithoutToken() throws Exception {
        geminiReply("{" + CREATE_FIELDS + ",\"occurredOn\":\"2026-11-24\"}", "2026-11-23");
        mvc.perform(multipart("/transactions/ai/receipt").file(receipt()).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CLARIFICATION_NEEDED"))
                .andExpect(jsonPath("$.confirmationToken").doesNotExist());
        gemini.server().verify();
        assertThat(transactions.count()).isZero();
    }

    @Test
    void ignoresProposalFieldsForQueries() throws Exception {
        when(transcription.transcribe(any(Resource.class))).thenReturn("Liste compras de mercado");
        when(groq.call(any(Prompt.class))).thenReturn(groqResponse("""
                {"intent":"LIST_TRANSACTIONS","category":"GROCERIES","type":"TRANSFER","occurredOn":{}}
                """));
        mvc.perform(multipart("/transactions/ai").file(audio()).with(authenticated(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LIST_TRANSACTIONS"))
                .andExpect(jsonPath("$.confirmationToken").doesNotExist());
    }

    @Test
    void providerComparisonKeepsItsOriginalResponseContract() throws Exception {
        var result = "{" + CREATE_FIELDS + ",\"type\":\"TRANSFER\",\"occurredOn\":\"2026-12-31\"}";
        when(groq.call(any(Prompt.class))).thenReturn(groqResponse(result));
        gemini.server().expect(requestTo("https://gemini.example/models/test:generateContent"))
                .andRespond(withSuccess(geminiBody(result), MediaType.APPLICATION_JSON));

        mvc.perform(post("/transactions/ai/compare").with(authenticated(USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Comprei no mercado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groqResponse.intent").value("CREATE_TRANSACTION"))
                .andExpect(jsonPath("$.geminiResponse.intent").value("CREATE_TRANSACTION"))
                .andExpect(jsonPath("$.groqResponse.type").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.groqResponse.occurredAt").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.geminiResponse.type").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.geminiResponse.occurredAt").doesNotHaveJsonPath());
        gemini.server().verify();
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(groq).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText())
                .contains("Comprei no mercado")
                .doesNotContain("Data local de referência", "occurredOn", "occurredAt");
    }

    private void geminiReply(String response, String referenceDate) {
        gemini.server().expect(requestTo("https://gemini.example/models/test:generateContent"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contents[0].parts[0].text", containsString(referenceDate)))
                .andRespond(withSuccess(geminiBody(response), MediaType.APPLICATION_JSON));
    }

    private static String geminiBody(String response) {
        var body = Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", response))))));
        return JsonMapper.builder().build().writeValueAsString(body);
    }

    private static ChatResponse groqResponse(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1});
    }

    private static MockMultipartFile receipt() throws Exception {
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", image);
        return new MockMultipartFile("file", "receipt.png", "image/png", image.toByteArray());
    }

    record GeminiStub(RestClient.Builder builder, MockRestServiceServer server) {}

    @TestConfiguration
    static class GeminiStubConfig {
        @Bean
        GeminiStub geminiStub() {
            var builder = RestClient.builder();
            return new GeminiStub(builder, MockRestServiceServer.bindTo(builder).build());
        }

        @Bean
        RestClient.Builder restClientBuilder(GeminiStub stub) {
            return stub.builder();
        }
    }
}

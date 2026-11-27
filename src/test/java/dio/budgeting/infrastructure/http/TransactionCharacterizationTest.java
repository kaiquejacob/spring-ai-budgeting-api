package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ai.AiProcessingException;
import dio.budgeting.application.assistant.ProcessReceiptImageUseCase;
import dio.budgeting.application.assistant.ProcessTransactionAudioUseCase;
import dio.budgeting.application.ai.GeminiTextService;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.transaction.ListRecentTransactionsUseCase;
import dio.budgeting.application.transaction.SumTransactionsByCategoryUseCase;
import dio.budgeting.application.transaction.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.repository.ConversationSessionJpaRepository;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.UUID;

import static dio.budgeting.infrastructure.http.AuthenticatedTestSession.authenticated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TransactionCharacterizationTest.MutableClockConfiguration.class)
class TransactionCharacterizationTest {

    private static final String USER_1 = "11111111-1111-1111-1111-111111111111";
    private static final String USER_2 = "22222222-2222-2222-2222-222222222222";
    private static final Instant START = Instant.parse("2026-11-23T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionEntityRepository transactionRepository;

    @Autowired
    private ConversationSessionJpaRepository sessionRepository;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private SumTransactionsByCategoryUseCase sumTransactions;

    @Autowired
    private ListRecentTransactionsUseCase recentTransactions;

    @MockitoBean
    private ProcessTransactionAudioUseCase audioUseCase;

    @MockitoBean
    private ProcessReceiptImageUseCase receiptUseCase;

    @MockitoBean
    private GeminiTextService geminiTextService;

    @MockitoBean(name = "financialChatClient")
    private ChatClient financialChatClient;

    @MockitoBean(name = "financialToolChatClient")
    private ChatClient financialToolChatClient;

    @BeforeEach
    void reset() {
        transactionRepository.deleteAll();
        sessionRepository.deleteAll();
        mutableClock.reset();
    }

    @Test
    void shouldDefaultManualTransactionToExpenseAtCreationTime() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Groceries","category":"GROCERIES","amount":12}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.occurredAt").value(START.toString()));
    }

    @Test
    void shouldExposeExplicitTypeAndOccurrence() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Refund","category":"GROCERIES","amount":12,
                                 "type":"INCOME","occurredAt":"2026-11-22T15:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.occurredAt").value("2026-11-22T15:00:00Z"));
    }

    @Test
    void shouldSumByOccurrenceAndKeepRecentTransactionsOrderedByCreation() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Today","category":"GROCERIES","amount":50}
                                """))
                .andExpect(status().isCreated());
        mutableClock.advanceSeconds(60);
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Yesterday","category":"GROCERIES","amount":12,
                                 "type":"INCOME","occurredAt":"2026-11-22T15:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        var user = UUID.fromString(USER_1);
        assertEquals(1200L, sumTransactions.execute(user, Category.GROCERIES, TransactionAiResponse.AiPeriod.YESTERDAY));
        assertEquals(List.of("Yesterday", "Today"),
                recentTransactions.execute(user).stream().map(TransactionOutput::description).toList());
    }

    @Test
    void shouldCreatePendingProposalFromAudioWithoutPersisting() throws Exception {
        when(audioUseCase.execute(any(), any())).thenReturn(createProposal());

        var result = mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CREATE_TRANSACTION"))
                .andExpect(jsonPath("$.confirmationToken").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.createdTransaction").doesNotExist())
                .andReturn();

        assertNotNull(tokenFrom(result.getResponse().getContentAsString()));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldPersistReceiptWithDefaultsOnlyAfterConfirmation() throws Exception {
        when(receiptUseCase.execute(any(), any())).thenReturn(createProposal());

        var proposal = mockMvc.perform(multipart("/transactions/ai/receipt")
                        .file(receiptFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CREATE_TRANSACTION"))
                .andExpect(jsonPath("$.confirmationToken").isNotEmpty())
                .andReturn();

        assertEquals(0, transactionRepository.count());

        mockMvc.perform(post("/transactions/{token}/confirm",
                        tokenFrom(proposal.getResponse().getContentAsString()))
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransaction.type").value("EXPENSE"))
                .andExpect(jsonPath("$.createdTransaction.occurredAt").value(START.toString()));
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void shouldPersistValidConfirmationAndRejectSecondConfirmation() throws Exception {
        when(audioUseCase.execute(any(), any())).thenReturn(createProposal());

        var proposal = mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andReturn();
        var token = tokenFrom(proposal.getResponse().getContentAsString());

        mockMvc.perform(post("/transactions/{token}/confirm", token)
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransaction.type").value("EXPENSE"))
                .andExpect(jsonPath("$.createdTransaction.occurredAt").value(START.toString()));

        assertEquals(1, transactionRepository.count());

        mockMvc.perform(post("/transactions/{token}/confirm", token)
                        .with(authenticated(USER_1)))
                .andExpect(status().isNotFound());

        assertEquals(1, transactionRepository.count());
    }

    @Test
    void shouldRejectMissingTokenAndKeepNoTransactions() throws Exception {
        mockMvc.perform(post("/transactions/{token}/confirm", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                        .with(authenticated(USER_1)))
                .andExpect(status().isNotFound());

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldRejectExpiredTokenAndKeepNoTransactions() throws Exception {
        when(audioUseCase.execute(any(), any())).thenReturn(createProposal());

        var proposal = mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andReturn();
        var token = tokenFrom(proposal.getResponse().getContentAsString());

        mutableClock.advanceSeconds(301);

        mockMvc.perform(post("/transactions/{token}/confirm", token)
                        .with(authenticated(USER_1)))
                .andExpect(status().isNotFound());

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldRejectTokenFromAnotherUserAndKeepNoTransactions() throws Exception {
        when(audioUseCase.execute(any(), any())).thenReturn(createProposal());

        var proposal = mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andReturn();
        var token = tokenFrom(proposal.getResponse().getContentAsString());

        mockMvc.perform(post("/transactions/{token}/confirm", token)
                        .with(authenticated(USER_2)))
                .andExpect(status().isNotFound());

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldReturnChatTextAndCreateSessionWithoutPersisting() throws Exception {
        var requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(financialToolChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(
                org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()
        )).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Resposta financeira mockada.");

        var result = mockMvc.perform(post("/transactions/ai/chat")
                        .with(authenticated(USER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Qual é meu saldo?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Resposta financeira mockada."))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn();

        assertNotNull(JsonPath.<String>read(
                result.getResponse().getContentAsString(),
                "$.sessionId"
        ));
        assertEquals(1, sessionRepository.count());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldRejectChatSessionFromAnotherUserAndKeepNoTransactions() throws Exception {
        var requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(financialToolChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(
                org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()
        )).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Resposta financeira mockada.");

        var created = mockMvc.perform(post("/transactions/ai/chat")
                        .with(authenticated(USER_1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Qual é meu saldo?\"}"))
                .andExpect(status().isOk())
                .andReturn();
        var sessionId = JsonPath.<String>read(
                created.getResponse().getContentAsString(),
                "$.sessionId"
        );

        mockMvc.perform(post("/transactions/ai/chat")
                        .param("sessionId", sessionId)
                        .with(authenticated(USER_2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Qual é meu saldo?\"}"))
                .andExpect(status().isForbidden());

        assertEquals(1, sessionRepository.count());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldNotPersistClarificationIntent() throws Exception {
        when(audioUseCase.execute(any(), any())).thenReturn(new TransactionAiResponse(
                TransactionIntent.CLARIFICATION_NEEDED,
                null,
                null,
                null,
                null,
                null,
                "Informe os dados da transação."
        ));

        mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CLARIFICATION_NEEDED"));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldNotPersistListTransactionsIntent() throws Exception {
        when(audioUseCase.execute(any(), any())).thenReturn(new TransactionAiResponse(
                TransactionIntent.LIST_TRANSACTIONS,
                null,
                null,
                Category.GROCERIES,
                null,
                null,
                null
        ));

        mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LIST_TRANSACTIONS"));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void shouldReturnAiProcessingFailureStatusWithoutPersisting() throws Exception {
        when(audioUseCase.execute(any(), any()))
                .thenThrow(new AiProcessingException("Falha simulada da IA."));

        mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(authenticated(USER_1)))
                .andExpect(status().isServiceUnavailable());

        assertEquals(0, transactionRepository.count());
    }

    private TransactionAiResponse createProposal() {
        return new TransactionAiResponse(
                TransactionIntent.CREATE_TRANSACTION,
                "Compra no mercado",
                2590L,
                Category.GROCERIES,
                null,
                "Proposta criada.",
                null
        );
    }

    private UUID tokenFrom(String body) throws Exception {
        var token = JsonPath.<String>read(body, "$.confirmationToken");
        return token == null ? null : java.util.UUID.fromString(token);
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "audio.m4a", "audio/mp4", "audio".getBytes());
    }

    private MockMultipartFile receiptFile() {
        return new MockMultipartFile("file", "receipt.jpg", "image/jpeg", "receipt".getBytes());
    }

    @TestConfiguration
    static class MutableClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(START);

        void reset() {
            instant.set(START);
        }

        void advanceSeconds(long seconds) {
            instant.updateAndGet(value -> value.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}

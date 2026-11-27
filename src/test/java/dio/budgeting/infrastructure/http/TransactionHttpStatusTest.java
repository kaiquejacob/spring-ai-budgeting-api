package dio.budgeting.infrastructure.http;

import dio.budgeting.application.assistant.ProcessTransactionAudioUseCase;
import dio.budgeting.application.ai.GeminiTextService;
import dio.budgeting.application.assistant.ProcessReceiptImageUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static dio.budgeting.infrastructure.http.AuthenticatedTestSession.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionHttpStatusTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessTransactionAudioUseCase processTransactionAudioUseCase;

    @MockitoBean
    private GeminiTextService geminiTextService;

    @MockitoBean
    private ProcessReceiptImageUseCase processReceiptImageUseCase;

    @MockitoBean(name = "financialChatClient")
    private ChatClient financialChatClient;

    @MockitoBean(name = "financialToolChatClient")
    private ChatClient financialToolChatClient;

    @Test
    void shouldReturnBadRequestForZeroAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void shouldReturnBadRequestForNegativeAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": -10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void shouldReturnUnauthorizedWhenSessionIsMissing() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": 10
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnBadRequestForInvalidCategoryPath() throws Exception {
        mockMvc.perform(get("/transactions/INVALID_CATEGORY")
                        .with(authenticated(USER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepCreatedStatusForValidTransaction() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": 10
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(put("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturnUnsupportedMediaTypeForPlainTextBody() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldExposeOpenApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(authenticated(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/transactions']").exists())
                .andExpect(jsonPath("$.paths['/transactions/{category}']").exists())
                .andExpect(jsonPath("$.paths['/transactions/ai/chat']").exists());
    }

    @Test
    void shouldExposeSwaggerUiRoute() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").with(authenticated(USER_ID)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void shouldListBlankDescriptionAsValidationFieldError() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "   ",
                                  "category": "GROCERIES",
                                  "amount": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("description")));
    }

    @Test
    void shouldListDescriptionOver255CharactersAsValidationFieldError() throws Exception {
        var description = "a".repeat(256);

        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\",\"category\":\"GROCERIES\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("description")));
    }

    @Test
    void shouldListMissingCategoryAsValidationFieldError() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "amount": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("category")));
    }

    @Test
    void shouldListBlankChatMessageAsValidationFieldError() throws Exception {
        mockMvc.perform(post("/transactions/ai/chat")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("message")));
    }

    @Test
    void shouldListChatMessageOver2000CharactersAsValidationFieldError() throws Exception {
        var message = "a".repeat(2001);

        mockMvc.perform(post("/transactions/ai/chat")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Campos inválidos")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("message")));
    }

    @Test
    void shouldIgnoreMalformedLegacyIdentityHeader() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", "not-a-uuid")
                        .with(authenticated(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Compra no mercado"));
    }
}

package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ProcessTransactionAudioUseCase;
import dio.budgeting.application.ai.GeminiTextService;
import dio.budgeting.application.ai.ProcessReceiptImageUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor deve ser maior que zero"));
    }

    @Test
    void shouldReturnBadRequestForNegativeAmount() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": -10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor deve ser maior que zero"));
    }

    @Test
    void shouldReturnBadRequestWhenUserHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Compra no mercado",
                                  "category": "GROCERIES",
                                  "amount": 10
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForInvalidCategoryPath() throws Exception {
        mockMvc.perform(get("/transactions/INVALID_CATEGORY")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepCreatedStatusForValidTransaction() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", USER_ID)
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
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturnUnsupportedMediaTypeForPlainTextBody() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid"))
                .andExpect(status().isUnsupportedMediaType());
    }
}

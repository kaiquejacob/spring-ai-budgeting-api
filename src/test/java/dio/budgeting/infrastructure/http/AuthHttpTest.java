package dio.budgeting.infrastructure.http;

import com.jayway.jsonpath.JsonPath;
import dio.budgeting.application.ai.TransactionAiResponse;
import dio.budgeting.application.ai.TransactionIntent;
import dio.budgeting.application.assistant.ProcessReceiptImageUseCase;
import dio.budgeting.application.assistant.ProcessTransactionAudioWithRetryUseCase;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthHttpTest {

    private static final String PASSWORD = "example-test-password";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwords;
    @LocalServerPort
    private int port;
    @MockitoBean
    private ProcessTransactionAudioWithRetryUseCase audio;
    @MockitoBean
    private ProcessReceiptImageUseCase receipt;

    @Test
    void rejectsAnonymousTransactionRequestEvenWithLegacyIdentityHeader() throws Exception {
        mvc.perform(get("/transactions/GROCERIES")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
        mvc.perform(post("/transactions/ai/chat")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyABcryptHash() throws Exception {
        var email = uniqueEmail();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        var csrf = csrf(null);
        mvc.perform(post("/auth/register").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("invalid", PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/register").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, "é".repeat(40))))
                .andExpect(status().isBadRequest());

        var id = register("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", csrf);
        assertThat(UUID.fromString(id)).isNotNull();
        var hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash != null && hash.startsWith("$2") && !hash.equals(PASSWORD)
                && passwords.matches(PASSWORD, hash)).isTrue();
        mvc.perform(post("/auth/register").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentRegistrationsHaveOneWinner() throws Exception {
        var email = uniqueEmail();
        var firstCsrf = csrf(null);
        var secondCsrf = csrf(null);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerConcurrently(email, firstCsrf, ready, start));
            var second = executor.submit(() -> registerConcurrently(email, secondCsrf, ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(new Integer[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email)).isEqualTo(1L);
    }

    @Test
    void loginRotatesSessionAndCsrfAndLogoutInvalidatesIt() throws Exception {
        var email = uniqueEmail();
        var beforeLogin = csrf(null);
        var id = register(email, beforeLogin);
        mvc.perform(post("/auth/login").session(beforeLogin.session()).param("email", email).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/login").session(beforeLogin.session()).header(beforeLogin.header(), beforeLogin.token())
                        .param("email", email).param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        var oldId = beforeLogin.session().getId();
        var session = login("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", beforeLogin);
        assertThat(session.getId().equals(oldId)).isFalse();
        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());
        mvc.perform(get("/transactions/GROCERIES").session(session)).andExpect(status().isOk());
        mvc.perform(post("/transactions").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(get("/v3/api-docs").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("X-User-Id"))));
        mvc.perform(post("/auth/logout").session(session).header(beforeLogin.header(), beforeLogin.token()))
                .andExpect(status().isForbidden());
        var afterLogin = csrf(session);
        mvc.perform(post("/auth/logout").session(session).header(afterLogin.header(), afterLogin.token()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        var afterLogout = csrf(null);
        mvc.perform(post("/auth/login").session(afterLogout.session()).header(afterLogin.header(), afterLogin.token())
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        assertThat(login(email, afterLogout)).isNotNull();
    }

    @Test
    void servletIssuesHttpOnlySessionCookie() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/csrf")).build();
            var result = client.send(request, HttpResponse.BodyHandlers.discarding());
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.headers().allValues("Set-Cookie")).anyMatch(cookie ->
                    cookie.startsWith("JSESSIONID=") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"));
        }
    }

    @Test
    void enforcesCsrfOnBothUploadsBeforeCallingProviders() throws Exception {
        var account = account();
        mvc.perform(multipart("/transactions/ai").file(audioFile()).session(account.csrf().session()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/transactions/ai/receipt").file(receiptFile()).session(account.csrf().session()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(audio, receipt);

        when(audio.execute(any())).thenReturn(proposal());
        when(receipt.execute(any(), anyString())).thenReturn(proposal());
        mvc.perform(multipart("/transactions/ai").file(audioFile()).session(account.csrf().session())
                        .header(account.csrf().header(), account.csrf().token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmationToken").isNotEmpty());
        mvc.perform(multipart("/transactions/ai/receipt").file(receiptFile()).session(account.csrf().session())
                        .header(account.csrf().header(), account.csrf().token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmationToken").isNotEmpty());
    }

    @Test
    void identityListsTotalsSessionsAndTokensBelongOnlyToAuthenticatedAccount() throws Exception {
        var alice = account();
        var bob = account();
        create(alice, bob.id(), "Compra Alice", 11);
        create(bob, alice.id(), "Compra Bob", 29);
        mvc.perform(get("/transactions/GROCERIES").session(alice.csrf().session()).header("X-User-Id", bob.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Compra Alice"));
        mvc.perform(get("/transactions/GROCERIES").session(bob.csrf().session()).header("X-User-Id", alice.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Compra Bob"));

        when(audio.execute(any())).thenReturn(new TransactionAiResponse(TransactionIntent.SUM_TRANSACTIONS_BY_CATEGORY,
                null, null, Category.GROCERIES, TransactionAiResponse.AiPeriod.ALL_TIME, null, null));
        mvc.perform(multipart("/transactions/ai").file(audioFile()).session(alice.csrf().session())
                        .header(alice.csrf().header(), alice.csrf().token()).header("X-User-Id", bob.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(11.0));
        mvc.perform(multipart("/transactions/ai").file(audioFile()).session(bob.csrf().session())
                        .header(bob.csrf().header(), bob.csrf().token()).header("X-User-Id", alice.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(29.0));

        when(audio.execute(any())).thenReturn(proposal());
        var result = mvc.perform(multipart("/transactions/ai").file(audioFile()).session(alice.csrf().session())
                        .header(alice.csrf().header(), alice.csrf().token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(result, "$.sessionId");
        String token = JsonPath.read(result, "$.confirmationToken");
        mvc.perform(multipart("/transactions/ai").file(audioFile()).session(bob.csrf().session())
                        .header(bob.csrf().header(), bob.csrf().token()).param("sessionId", sessionId)
                        .header("X-User-Id", alice.id()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("SESSION_ACCESS_DENIED"));
        mvc.perform(post("/transactions/{token}/confirm", token).session(bob.csrf().session())
                        .header(bob.csrf().header(), bob.csrf().token()).header("X-User-Id", alice.id()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("PENDING_TRANSACTION_NOT_FOUND"));
        mvc.perform(post("/transactions/{token}/confirm", token).session(alice.csrf().session())
                        .header(alice.csrf().header(), alice.csrf().token()).param("sessionId", sessionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdTransaction.description").value("Proposta isolada"));
    }

    private record Csrf(MockHttpSession session, String header, String token) {}
    private record AuthenticatedAccount(String id, Csrf csrf) {}

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var json = JsonPath.parse(result.getResponse().getContentAsString());
        return new Csrf((MockHttpSession) result.getRequest().getSession(false),
                json.read("$.headerName"), json.read("$.token"));
    }

    private String register(String email, Csrf csrf) throws Exception {
        var result = mvc.perform(post("/auth/register").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private int registerConcurrently(String email, Csrf csrf, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for the other registration");
        }
        return mvc.perform(post("/auth/register").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andReturn().getResponse().getStatus();
    }

    private MockHttpSession login(String email, Csrf csrf) throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/login").session(csrf.session()).header(csrf.header(), csrf.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private AuthenticatedAccount account() throws Exception {
        var email = uniqueEmail();
        var csrf = csrf(null);
        var id = register(email, csrf);
        return new AuthenticatedAccount(id, csrf(login(email, csrf)));
    }

    private void create(AuthenticatedAccount account, String otherId, String description, int amount) throws Exception {
        var csrf = account.csrf();
        mvc.perform(post("/transactions").session(csrf.session()).header(csrf.header(), csrf.token())
                        .header("X-User-Id", otherId).param("userId", otherId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"description":"%s","amount":%d,"category":"GROCERIES","userId":"%s"}
                                """.formatted(description, amount, otherId)))
                .andExpect(status().isCreated());
    }

    private static String credentials(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.invalid";
    }

    private static MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1});
    }

    private static MockMultipartFile receiptFile() {
        return new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1});
    }

    private static TransactionAiResponse proposal() {
        return new TransactionAiResponse(TransactionIntent.CREATE_TRANSACTION,
                "Proposta isolada", 1200L, Category.GROCERIES, null, null, null);
    }
}

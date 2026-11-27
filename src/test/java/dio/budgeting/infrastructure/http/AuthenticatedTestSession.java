package dio.budgeting.infrastructure.http;

import dio.budgeting.infrastructure.auth.AccountPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

final class AuthenticatedTestSession {

    private AuthenticatedTestSession() {
    }

    static RequestPostProcessor authenticated(String id) {
        return request -> {
            var principal = new AccountPrincipal(UUID.fromString(id), id + "@example.invalid", "");
            var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
            request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    new SecurityContextImpl(authentication));
            return csrf().asHeader().postProcessRequest(request);
        };
    }
}

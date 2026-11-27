package dio.budgeting.infrastructure.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final AccountService accounts;

    AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse register(@Valid @RequestBody RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter até 72 bytes em UTF-8.");
        }
        try {
            var account = accounts.register(request.email(), request.password());
            return new AccountResponse(account.id(), account.email());
        } catch (AccountService.DuplicateEmailException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }
    }

    @GetMapping("/me")
    AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new AccountResponse(principal.getId(), principal.getUsername());
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
        RegisterRequest {
            email = AccountService.normalizeEmail(email);
        }

        @Override
        public String toString() {
            return "RegisterRequest";
        }
    }

    record AccountResponse(UUID id, String email) {
    }

    record CsrfResponse(String headerName, String token) {
    }
}

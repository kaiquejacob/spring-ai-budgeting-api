package dio.budgeting.infrastructure.auth;

import org.springframework.security.core.userdetails.User;

import java.io.Serial;
import java.util.List;
import java.util.UUID;

public final class AccountPrincipal extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;

    public AccountPrincipal(UUID id, String email, String passwordHash) {
        super(email, passwordHash, List.of());
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}

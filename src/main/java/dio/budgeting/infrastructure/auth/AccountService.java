package dio.budgeting.infrastructure.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

@Service
class AccountService implements UserDetailsService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Clock clock;

    AccountService(AccountRepository accounts, PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.clock = clock;
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    Account register(String email, String password) {
        var account = new Account(email, passwords.encode(password), clock.instant());
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (accounts.existsByEmail(email)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    @Override
    public AccountPrincipal loadUserByUsername(String email) {
        return accounts.findByEmail(normalizeEmail(email))
                .map(account -> new AccountPrincipal(account.id(), account.email(), account.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }

    static final class DuplicateEmailException extends RuntimeException {
    }
}

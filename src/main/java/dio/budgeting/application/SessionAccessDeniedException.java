package dio.budgeting.application;

public class SessionAccessDeniedException extends RuntimeException {

    public SessionAccessDeniedException() {
        super("Não foi possível acessar esta sessão.");
    }
}
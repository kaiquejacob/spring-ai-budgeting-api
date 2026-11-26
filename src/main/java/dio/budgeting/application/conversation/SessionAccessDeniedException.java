package dio.budgeting.application.conversation;

public class SessionAccessDeniedException extends RuntimeException {

    public SessionAccessDeniedException() {
        super("Não foi possível acessar esta sessão.");
    }
}
package dio.budgeting.application.proposal;

public class PendingTransactionException extends RuntimeException {

    public PendingTransactionException(String message) {
        super(message);
    }
}
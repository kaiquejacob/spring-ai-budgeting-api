package dio.budgeting.application;

public class PendingTransactionException extends RuntimeException {

    public PendingTransactionException(String message) {
        super(message);
    }
}
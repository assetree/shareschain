
package shareschain;

public abstract class ShareschainException extends Exception {

    protected ShareschainException() {
        super();
    }

    protected ShareschainException(String message) {
        super(message);
    }

    protected ShareschainException(String message, Throwable cause) {
        super(message, cause);
    }

    protected ShareschainException(Throwable cause) {
        super(cause);
    }

    public static abstract class ValidationException extends ShareschainException {

        private ValidationException(String message) {
            super(message);
        }

        private ValidationException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static class NotCurrentlyValidException extends ValidationException {

        public NotCurrentlyValidException(String message) {
            super(message);
        }

    }

    public static class ExistingTransactionException extends NotCurrentlyValidException {

        public ExistingTransactionException(String message) {
            super(message);
        }

    }

    public static final class NotYetEnabledException extends NotCurrentlyValidException {

        public NotYetEnabledException(String message) {
            super(message);
        }

    }

    public static final class NotValidException extends ValidationException {

        public NotValidException(String message) {
            super(message);
        }

        public NotValidException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static class InsufficientBalanceException extends NotCurrentlyValidException {

        public InsufficientBalanceException(String message) {
            super(message);
        }


    }

    public static final class NotYetEncryptedException extends IllegalStateException {

    }

    public static final class StopException extends RuntimeException {

    }
}


package shareschain;

public abstract class ShareschainExceptions extends Exception {

    protected ShareschainExceptions() {
        super();
    }

    protected ShareschainExceptions(String message) {
        super(message);
    }

    protected ShareschainExceptions(String message, Throwable cause) {
        super(message, cause);
    }

    protected ShareschainExceptions(Throwable cause) {
        super(cause);
    }

    public static abstract class ValidationExceptions extends ShareschainExceptions {

        private ValidationExceptions(String message) {
            super(message);
        }

        private ValidationExceptions(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static class NotCurrentlyValidExceptions extends ValidationExceptions {

        public NotCurrentlyValidExceptions(String message) {
            super(message);
        }

    }

    public static class ExistingTransactionExceptions extends NotCurrentlyValidExceptions {

        public ExistingTransactionExceptions(String message) {
            super(message);
        }

    }

    public static final class NotYetEnabledExceptions extends NotCurrentlyValidExceptions {

        public NotYetEnabledExceptions(String message) {
            super(message);
        }

    }

    public static final class NotValidExceptions extends ValidationExceptions {

        public NotValidExceptions(String message) {
            super(message);
        }

        public NotValidExceptions(String message, Throwable cause) {
            super(message, cause);
        }

    }

    public static class InsufficientBalanceExceptions extends NotCurrentlyValidExceptions {

        public InsufficientBalanceExceptions(String message) {
            super(message);
        }


    }

    public static final class NotYetEncryptedException extends IllegalStateException {

    }

    public static final class StopException extends RuntimeException {

    }
}

package shareschain.node;

import java.io.IOException;

/**
 * Signals that a node network communication exception has occurred
 */
class NetworkException extends IOException {

    /**
     * Construct a NetworkException with the specified detail message.
     *
     * @param   message                 Detail message which is saved for later retrieval
     */
    NetworkException(String message) {
        super(message);
    }

    /**
     * Construct a NetworkException with the specified detail message and cause.
     *
     * @param   message                 Detail message which is saved for later retrieval
     * @param   cause                   Cause which is saved for later retrieval
     */
    NetworkException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a NetworkException with the specified cause and a
     * detail message of cause.toString().
     *
     * @param   cause                   Cause which is saved for later retrieval
     */
    NetworkException(Throwable cause) {
        super(cause);
    }
}

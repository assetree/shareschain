package shareschain.node;

/**
 * Signals that a node network protocol error has occurred
 */
class NetworkProtocolException extends NetworkException {

    /**
     * Construct a NetworkProtocolException with the specified detail message.
     *
     * @param   message                 Detail message which is saved for later retrieval
     */
    NetworkProtocolException(String message) {
        super(message);
    }

}

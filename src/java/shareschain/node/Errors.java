
package shareschain.node;

final class Errors {

    final static String BLACKLISTED = "Your node is blacklisted";
    final static String SEQUENCE_ERROR = "Node request received before 'getInfo' request";
    final static String TOO_MANY_BLOCKS_REQUESTED = "Too many blocks requested";
    final static String TOO_MANY_TRANSACTIONS_REQUESTED = "Too many transactions request";
    final static String DOWNLOADING = "Blockchain download in progress";
    final static String LIGHT_CLIENT = "Node is in light mode";

    private Errors() {} // never
}

package shareschain.permission;

import java.nio.ByteBuffer;

/**
 * Security token
 */
public interface SecurityToken {

    /**
     * Get node account identifier
     *
     * @return                  Node account identifier
     */
    long getNodeAccountId();

    /**
     * Get node public key
     *
     * @return                  Node public key
     */
    byte[] getNodePublicKey();

    /**
     * Get the session key
     *
     * @param   secretPhrase    Server credentials secret phrase
     * @param   nodePublicKey   Node public key
     * @return                  Session key or null if there is no session key
     */
    byte[] getSessionKey(String secretPhrase, byte[] nodePublicKey);

    /**
     * Set the session key
     *
     * @param   secretPhrase    Server credentials secret phrase
     * @param   nodePublicKey   Node public key
     * @param   sessionKey      Session key
     */
    void setSessionKey(String secretPhrase, byte[] nodePublicKey, byte[] sessionKey);

    /**
     * Get the serialized token length
     *
     * @return                  Serialized token length
     */
    int getLength();

    /**
     * Get the serialized token
     *
     * @return                  Serialized token
     */
    byte[] getBytes();

    /**
     * Add the serialized token to a buffer
     *
     * @param   buffer          Byte buffer
     * @return                  Byte buffer
     */
    ByteBuffer getBytes(ByteBuffer buffer);
}

package shareschain.permission;

import shareschain.Constants;

import java.nio.ByteBuffer;

/**
 * Generate an permission security token for a permissioned blockchain
 */
public abstract class SecurityTokenFactory {

    private static final SecurityTokenFactory securityTokenFactory;
    static {
        if (Constants.isPermissioned) {
            try {
                Class<?> factoryClass = Class.forName("com.jelurida.blockchain.permission.BlockchainSecurityTokenFactory");
                securityTokenFactory = (SecurityTokenFactory)factoryClass.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        } else {
            securityTokenFactory = null;
        }
    }

    /**
     * Get the security token factory
     *
     * @return                      Security token factory or null if no provider available
     */
    public static SecurityTokenFactory getSecurityTokenFactory() {
        return securityTokenFactory;
    }

    /**
     * Create a new security token
     *
     * @param   publicKey           Public key
     * @return                      Security token
     */
    public abstract SecurityToken getSecurityToken(byte[] publicKey);

    /**
     * Create a new security token
     *
     * @param   buffer              Byte buffer
     * @return                      Security token
     */
    public abstract SecurityToken getSecurityToken(ByteBuffer buffer);
}

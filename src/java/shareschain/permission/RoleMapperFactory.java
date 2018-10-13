package shareschain.permission;

import shareschain.Constants;

import java.util.EnumSet;

/**
 * Create a role mapper for a permissioned blockchain
 */
public class RoleMapperFactory {

    private RoleMapperFactory() {}

    private static final RoleMapper roleMapper;
    static {
        if (Constants.isPermissioned) {
            try {
                Class<?> roleMapperClass = Class.forName("com.jelurida.blockchain.permission.BlockchainRoleMapper");
                roleMapper = (RoleMapper)roleMapperClass.newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        } else {
            roleMapper = new NullRoleMapper();
        }
    }

    /**
     * Get the role mapper
     *
     * A null role mapper will be returned if the blockchain is not permissioned
     *
     * @return                      Role mapper
     */
    public static RoleMapper getRoleMapper() {
        return roleMapper;
    }

    /**
     * Dummy role mapper for a non-permissioned blockchain
     */
    public static class NullRoleMapper implements RoleMapper {

        @Override
        public EnumSet<Role> getUserRoles(String rsAccount) {
            return EnumSet.noneOf(Role.class);
        }

        @Override
        public boolean isValidRoleSetter(long setterId) {
            return false;
        }

        @Override
        public boolean isUserInRole(long accountId, Role role) {
            return false;
        }

        @Override
        public EnumSet<Role> parseRoles(String value) {
            return EnumSet.noneOf(Role.class);
        }
    }
}

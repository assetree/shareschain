package shareschain.permission;

import java.util.EnumSet;

/**
 * Map Shareschain account to permission role
 */
public interface RoleMapper {

    /**
     * Get the user roles for the supplied Shareschain account
     *
     * @param   rsAccount           Shareschain account
     * @return                      Set of user roles
     */
    EnumSet<Role> getUserRoles(String rsAccount);

    /**
     * Parse the account property string containing the user roles.
     * Multiple roles can be specified separated by commas.
     *
     * @param   value               User roles
     * @return                      Set of user roles
     */
    EnumSet<Role> parseRoles(String value);

    /**
     * Check if the supplied Shareschain account is allowed to set user roles.
     * An account must have the ADMIN role in order to set user roles.
     *
     * @param   setterId            Account setting the user roles
     * @return                      TRUE if the account is allowed to set user roles
     */
    boolean isValidRoleSetter(long setterId);

    /**
     * Check if the supplied Shareschain account has the specified user role
     *
     * @param   accountId           Account identifier
     * @param   role                User role
     * @return                      TRUE if the account has the specified user role
     */
    boolean isUserInRole(long accountId, Role role);
}

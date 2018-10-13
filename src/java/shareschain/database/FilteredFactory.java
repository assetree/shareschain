
package shareschain.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Create Statement and PrepareStatement for use with FilteredConnection
 */
public interface FilteredFactory {

    /**
     * Create a FilteredStatement for the supplied Statement
     *
     * @param   stmt                Statement
     * @param   con                 Connection
     * @return                      Wrapped statement
     * @throws  SQLException        SQLException
     */
    Statement createStatement(FilteredConnection con, Statement stmt) throws SQLException;

    /**
     * Create a FilteredPreparedStatement for the supplied PreparedStatement
     *
     * @param   stmt                Prepared statement
     * @param   sql                 SQL statement
     * @param   con                 Connection
     * @throws  SQLException        SQLException
     * @return                      Wrapped prepared statement
     */
    PreparedStatement createPreparedStatement(FilteredConnection con, PreparedStatement stmt, String sql) throws SQLException;
}

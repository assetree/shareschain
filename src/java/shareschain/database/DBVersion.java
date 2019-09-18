
package shareschain.database;

import shareschain.util.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class DBVersion {

    protected final BasicDB db;
    protected final String schema;

    protected DBVersion(BasicDB db, String schema) {
        this.db = db;
        this.schema = schema;
    }

    void createSchema() {
        Connection con = null;
        Statement stmt = null;
        try {
            con = db.getConnection("PUBLIC");
            stmt = con.createStatement();
            stmt.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + schema);
        } catch (SQLException e) {
            DBUtils.rollback(con);
            throw new RuntimeException(e.toString(), e);
        } finally {
            DBUtils.close(stmt, con);
        }
    }

    void init() {
        Connection con = null;
        Statement stmt = null;
        try {
            con = db.getConnection(schema);
            stmt = con.createStatement();
            int nextUpdate = 1;
            try {
                ResultSet rs = stmt.executeQuery("SELECT next_update FROM " + schema + ".version");
                if (! rs.next()) {
                    throw new RuntimeException("Invalid version table");
                }
                nextUpdate = rs.getInt("next_update");
                if (! rs.isLast()) {
                    throw new RuntimeException("Invalid version table");
                }
                rs.close();
                Logger.logMessageWithExcpt("Database update may take a while if needed, current database version " + (nextUpdate - 1) + "...");
            } catch (SQLException e) {
                Logger.logMessageWithExcpt("Initializing an empty database");
                stmt.executeUpdate("CREATE TABLE version (next_update INT NOT NULL)");
                stmt.executeUpdate("INSERT INTO version VALUES (1)");
                con.commit();
            }
            update(nextUpdate);
        } catch (SQLException e) {
            DBUtils.rollback(con);
            throw new RuntimeException(e.toString(), e);
        } finally {
            DBUtils.close(stmt, con);
        }
    }

    protected void apply(String sql) {
        Connection con = null;
        Statement stmt = null;
        try {
            con = db.getConnection(schema);
            stmt = con.createStatement();
            try {
                if (sql != null) {
                    Logger.logDebugMessage("Will apply sql:\n" + sql);
                    stmt.executeUpdate(sql);
                }
                stmt.executeUpdate("UPDATE version SET next_update = next_update + 1");
                con.commit();
            } catch (Exception e) {
                DBUtils.rollback(con);
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error executing " + sql, e);
        } finally {
            DBUtils.close(stmt, con);
        }
    }

    protected abstract void update(int nextUpdate);

}


package shareschain.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class Table {

    protected static final TransactionalDB db = DB.db;

    protected final String schema;
    protected final String table;
    protected final String schemaTable;

    public Table(String schemaTable) {
        schemaTable = schemaTable.toUpperCase(Locale.ROOT);
        String[] s = schemaTable.split("\\.");
        if (s.length != 2) {
            throw new IllegalArgumentException("Missing schema name " + schemaTable);
        }
        this.schema = s[0];
        this.table = s[1];
        this.schemaTable = schemaTable;
    }

    public final Connection getConnection() throws SQLException {
        return db.getConnection(schema);
    }

    public void truncate() {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + schemaTable);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final String getSchemaTable() {
        return schemaTable;
    }

    @Override
    public final String toString() {
        return schemaTable;
    }

    public int getCount() {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable)) {
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public int getCount(DBClause dbClause) {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable
                     + " WHERE " + dbClause.getClause())) {
            dbClause.set(pstmt, 1);
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getRowCount() {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable)) {
            return getCount(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final int getCount(PreparedStatement pstmt) throws SQLException {
        try (ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

}

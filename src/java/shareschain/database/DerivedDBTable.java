
package shareschain.database;

import shareschain.Shareschain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class DerivedDBTable extends Table {

    protected DerivedDBTable(String schemaTable) {
        super(schemaTable);
        Shareschain.getBlockchainProcessor().registerDerivedTable(this);
    }

    /**
     * 回滚数据库表
     * @param height
     */
    public void popOffTo(int height) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = getConnection();
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + schemaTable + " WHERE height > ?")) {
            pstmtDelete.setInt(1, height);
            pstmtDelete.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void rollback(int height) {
        popOffTo(height);
    }

    public void trim(int height) {
        //nothing to trim
    }

    public void createSearchIndex(Connection con) throws SQLException {
        //implemented in EntityDBTable only
    }

    public boolean isPersistent() {
        return false;
    }

}


package shareschain.database;

import shareschain.Shareschain;
import shareschain.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TransactionalDB extends BasicDB {

    private final DBFactory factory = new DBFactory();
    private static final long stmtThreshold;
    private static final long txThreshold;
    private static final long txInterval;
    static {
        long temp;
        stmtThreshold = (temp= Shareschain.getIntProperty("shareschain.statementLogThreshold")) != 0 ? temp : 1000;
        txThreshold = (temp= Shareschain.getIntProperty("shareschain.transactionLogThreshold")) != 0 ? temp : 5000;
        txInterval = (temp= Shareschain.getIntProperty("shareschain.transactionLogInterval")) != 0 ? temp*60*1000 : 15*60*1000;
    }

    private final ThreadLocal<DBConnection> localConnection = new ThreadLocal<>();
    private final ThreadLocal<Map<String,Map<DBKey,Object>>> transactionCaches = new ThreadLocal<>();
    private final ThreadLocal<Set<TransactionCallback>> transactionCallback = new ThreadLocal<>();
    private volatile long txTimes = 0;
    private volatile long txCount = 0;
    private volatile long statsTime = 0;

    public TransactionalDB(DBProperties dbProperties) {
        super(dbProperties);
    }

    public Connection getConnection(String schema) throws SQLException {
        Connection con = localConnection.get();
        if (con == null) {
            con = getPooledConnection();
            con.setAutoCommit(true);
            con = new DBConnection(con, schema);
        } else {
            con.setSchema(schema);
        }
        return con;
    }

    public boolean isInTransaction() {
        return localConnection.get() != null;
    }

    public Connection beginTransaction() {
        return beginTransaction("PUBLIC");
    }

    public Connection beginTransaction(String schema) {
        if (localConnection.get() != null) {
            throw new IllegalStateException("Transaction already in progress");
        }
        try {
            Connection con = getPooledConnection();
            con.setAutoCommit(false);
            con = new DBConnection(con, schema);
            ((DBConnection)con).txStart = System.currentTimeMillis();
            localConnection.set((DBConnection)con);
            transactionCaches.set(new HashMap<>());
            return con;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void commitTransaction() {
        DBConnection con = localConnection.get();
        if (con == null) {
            throw new IllegalStateException("Not in transaction");
        }
        try {
            con.doCommit();
            Set<TransactionCallback> callbacks = transactionCallback.get();
            if (callbacks != null) {
                callbacks.forEach(TransactionCallback::commit);
                transactionCallback.set(null);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public void rollbackTransaction() {
        DBConnection con = localConnection.get();
        if (con == null) {
            throw new IllegalStateException("Not in transaction");
        }
        try {
            con.doRollback();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            transactionCaches.get().clear();
            Set<TransactionCallback> callbacks = transactionCallback.get();
            if (callbacks != null) {
                callbacks.forEach(TransactionCallback::rollback);
                transactionCallback.set(null);
            }
        }
    }

    public void endTransaction() {
        Connection con = localConnection.get();
        if (con == null) {
            throw new IllegalStateException("Not in transaction");
        }
        localConnection.set(null);
        transactionCaches.set(null);
        long now = System.currentTimeMillis();
        long elapsed = now - ((DBConnection)con).txStart;
        if (elapsed >= txThreshold) {
            logThreshold(String.format("Database transaction required %.3f seconds at height %d",
                                       (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight()));
        } else {
            long count, times;
            boolean logStats = false;
            synchronized(this) {
                count = ++txCount;
                times = txTimes += elapsed;
                if (now - statsTime >= txInterval) {
                    logStats = true;
                    txCount = 0;
                    txTimes = 0;
                    statsTime = now;
                }
            }
            if (logStats)
                Logger.logDebugMessage(String.format("Average database transaction time is %.3f seconds",
                                                     (double)times/1000.0/(double)count));
        }
        DBUtils.close(con);
    }

    public void registerCallback(TransactionCallback callback) {
        Set<TransactionCallback> callbacks = transactionCallback.get();
        if (callbacks == null) {
            callbacks = new HashSet<>();
            transactionCallback.set(callbacks);
        }
        callbacks.add(callback);
    }

    Map<DBKey,Object> getCache(String schemaTable) {
        if (!isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        return transactionCaches.get().computeIfAbsent(schemaTable, k -> new HashMap<>());
    }

    void clearCache(String schemaTable) {
        Map<DBKey,Object> cacheMap = transactionCaches.get().get(schemaTable);
        if (cacheMap != null) {
            cacheMap.clear();
        }
    }

    public void clearCache() {
        transactionCaches.get().values().forEach(Map::clear);
    }

    private static void logThreshold(String msg) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(msg).append('\n');
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean firstLine = true;
        for (int i=3; i<stackTrace.length; i++) {
            String line = stackTrace[i].toString();
            if (!line.startsWith("shareschain."))
                break;
            if (firstLine)
                firstLine = false;
            else
                sb.append('\n');
            sb.append("  ").append(line);
        }
        Logger.logDebugMessage(sb.toString());
    }

    private final class DBConnection extends FilteredConnection {

        private long txStart = 0;
        private volatile String schema;

        private DBConnection(Connection con, String schema) throws SQLException {
            super(con, factory);
            setSchema(schema);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) {
            throw new UnsupportedOperationException("Use DB.beginTransaction() to start a new transaction");
        }

        @Override
        public void commit() throws SQLException {
            if (localConnection.get() == null) {
                super.commit();
            } else if (this != localConnection.get()) {
                throw new IllegalStateException("Previous connection not committed");
            } else {
                commitTransaction();
            }
        }

        private void doCommit() throws SQLException {
            super.commit();
        }

        @Override
        public void rollback() throws SQLException {
            if (localConnection.get() == null) {
                super.rollback();
            } else if (this != localConnection.get()) {
                throw new IllegalStateException("Previous connection not committed");
            } else {
                rollbackTransaction();
            }
        }

        private void doRollback() throws SQLException {
            super.rollback();
        }

        @Override
        public void close() throws SQLException {
            if (localConnection.get() == null) {
                super.close();
            } else if (this != localConnection.get()) {
                throw new IllegalStateException("Previous connection not committed");
            }
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            schema = schema.toUpperCase(Locale.ROOT);
            if (schema.equals(this.schema)) {
                return;
            }
            this.schema = schema;
            try (Statement stmt = createStatement()) {
                stmt.executeUpdate("SET SCHEMA " + schema);
                stmt.executeUpdate("SET SCHEMA_SEARCH_PATH " + schema + ", PUBLIC");
            }
        }

        @Override
        public String getSchema() {
            return schema;
        }

    }

    private final class DBStatement extends FilteredStatement {

        private final FilteredConnection con;
        private final String schema;

        private DBStatement(FilteredConnection con, Statement stmt) throws SQLException {
            super(stmt);
            this.con = con;
            this.schema = con.getSchema();
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            con.setSchema(schema);
            boolean b = super.execute(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > stmtThreshold)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                                           (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight(), sql));
            return b;
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            con.setSchema(schema);
            ResultSet r = super.executeQuery(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > stmtThreshold)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                                           (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight(), sql));
            return r;
        }

        @Override
        public int executeUpdate(String sql) throws SQLException {
            long start = System.currentTimeMillis();
            con.setSchema(schema);
            int c = super.executeUpdate(sql);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > stmtThreshold)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                                           (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight(), sql));
            return c;
        }

        @Override
        public Connection getConnection() {
            return con;
        }

    }

    private final class DBPreparedStatement extends FilteredPreparedStatement {

        private final FilteredConnection con;
        private final String schema;

        private DBPreparedStatement(FilteredConnection con, PreparedStatement stmt, String sql) throws SQLException {
            super(stmt, sql);
            this.con = con;
            this.schema = con.getSchema();
        }

        @Override
        public boolean execute() throws SQLException {
            long start = System.currentTimeMillis();
            con.setSchema(schema);
            boolean b = super.execute();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > stmtThreshold)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                                           (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight(), getSQL()));
            return b;
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            long start = System.currentTimeMillis();
            con.setSchema(schema);
            ResultSet r = super.executeQuery();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > stmtThreshold)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                                           (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight(), getSQL()));
            return r;
        }

        @Override
        public int executeUpdate() throws SQLException {
            long start = System.currentTimeMillis();
            con.setSchema(schema);
            int c = super.executeUpdate();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > stmtThreshold)
                logThreshold(String.format("SQL statement required %.3f seconds at height %d:\n%s",
                                           (double)elapsed/1000.0, Shareschain.getBlockchain().getHeight(), getSQL()));
            return c;
        }

        @Override
        public Connection getConnection() {
            return con;
        }

    }

    private final class DBFactory implements FilteredFactory {

        @Override
        public Statement createStatement(FilteredConnection con, Statement stmt) throws SQLException {
            return new DBStatement(con, stmt);
        }

        @Override
        public PreparedStatement createPreparedStatement(FilteredConnection con, PreparedStatement stmt, String sql) throws SQLException {
            return new DBPreparedStatement(con, stmt, sql);
        }
    }

    /**
     * Transaction callback interface
     */
    public interface TransactionCallback {

        /**
         * Transaction has been committed
         */
        void commit();

        /**
         * Transaction has been rolled back
         */
        void rollback();
    }
}

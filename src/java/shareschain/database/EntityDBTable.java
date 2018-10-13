
package shareschain.database;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class EntityDBTable<T> extends DerivedDBTable {

    protected static final DBClause LATEST = new DBClause.FixedClause(" latest = TRUE ");

    private final boolean multiversion;
    protected final DBKey.Factory<T> dbKeyFactory;
    private final String defaultSort;
    private final String fullTextSearchColumns;

    protected EntityDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory) {
        this(schemaTable, dbKeyFactory, false, null);
    }

    protected EntityDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory, String fullTextSearchColumns) {
        this(schemaTable, dbKeyFactory, false, fullTextSearchColumns);
    }

    EntityDBTable(String schemaTable, DBKey.Factory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(schemaTable);
        this.dbKeyFactory = dbKeyFactory;
        this.multiversion = multiversion;
        this.defaultSort = " ORDER BY " + (multiversion ? dbKeyFactory.getPKColumns() : " height DESC, db_id DESC ");
        if (fullTextSearchColumns != null) {
            fullTextSearchColumns = fullTextSearchColumns.toUpperCase(Locale.ROOT);
        }
        this.fullTextSearchColumns = fullTextSearchColumns;
    }

    protected abstract T load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException;

    protected abstract void save(Connection con, T t) throws SQLException;

    protected String defaultSort() {
        return defaultSort;
    }

    protected void clearCache() {
        db.clearCache(schemaTable);
    }

    public void checkAvailable(int height) {
        if (multiversion) {
            int minRollBackHeight = isPersistent() && Shareschain.getBlockchainProcessor().isScanning() ?
                    Math.max(Shareschain.getBlockchainProcessor().getInitialScanHeight() - Constants.MAX_ROLLBACK, 0)
                    : Shareschain.getBlockchainProcessor().getMinRollbackHeight();
            if (height < minRollBackHeight) {
                throw new IllegalArgumentException("Historical data as of height " + height + " not available.");
            }
        }
        if (height > Shareschain.getBlockchain().getHeight()) {
            throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + Shareschain.getBlockchain().getHeight());
        }
    }

    public final T newEntity(DBKey dbKey) {
        boolean cache = db.isInTransaction();
        if (cache) {
            T t = (T) db.getCache(schemaTable).get(dbKey);
            if (t != null) {
                return t;
            }
        }
        T t = dbKeyFactory.newEntity(dbKey);
        if (cache) {
            db.getCache(schemaTable).put(dbKey, t);
        }
        return t;
    }

    public final T get(DBKey dbKey) {
        return get(dbKey, true);
    }

    public final T get(DBKey dbKey, boolean cache) {
        if (cache && db.isInTransaction()) {
            T t = (T) db.getCache(schemaTable).get(dbKey);
            if (t != null) {
                return t;
            }
        }
        String sql = "SELECT * FROM " + schemaTable + dbKeyFactory.getPKClause()
                + (multiversion ? " AND latest = TRUE LIMIT 1" : "");
        Logger.logInfoMessage(sql);
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            //Logger.logDebugMessage("sql:\n" + sql);//这里新增加的
            //printRealSql(sql,null);
            dbKey.setPK(pstmt);
            return get(con, pstmt, cache);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T get(DBKey dbKey, int height) {
        if (height < 0 || doesNotExceed(height)) {
            return get(dbKey);
        }
        checkAvailable(height);
        String sql = "SELECT * FROM " + schemaTable + dbKeyFactory.getPKClause()
                + " AND height <= ?" + (multiversion ? " AND (latest = TRUE OR EXISTS ("
                + "SELECT 1 FROM " + schemaTable + dbKeyFactory.getPKClause() + " AND height > ?)) ORDER BY height DESC LIMIT 1" : "");
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + dbKeyFactory.getPKClause()
                     + " AND height <= ?" + (multiversion ? " AND (latest = TRUE OR EXISTS ("
                     + "SELECT 1 FROM " + schemaTable + dbKeyFactory.getPKClause() + " AND height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
            int i = dbKey.setPK(pstmt);
            pstmt.setInt(i, height);
            if (multiversion) {
                i = dbKey.setPK(pstmt, ++i);
                pstmt.setInt(i, height);
            }
            return get(con, pstmt, false);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T getBy(DBClause dbClause) {
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable
                     + " WHERE " + dbClause.getClause() + (multiversion ? " AND latest = TRUE LIMIT 1" : ""))) {
            dbClause.set(pstmt, 1);
            return get(con, pstmt, true);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final T getBy(DBClause dbClause, int height) {
        if (height < 0 || doesNotExceed(height)) {
            return getBy(dbClause);
        }
        checkAvailable(height);
        try (Connection con = getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + " AS a WHERE " + dbClause.getClause()
                     + " AND height <= ?" + (multiversion ? " AND (latest = TRUE OR EXISTS ("
                     + "SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                     + " AND b.height > ?)) ORDER BY height DESC LIMIT 1" : ""))) {
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
            }
            return get(con, pstmt, false);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private T get(Connection con, PreparedStatement pstmt, boolean cache) throws SQLException {
        final boolean doCache = cache && db.isInTransaction();
        try (ResultSet rs = pstmt.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            T t = null;
            DBKey dbKey = null;
            if (doCache) {
                dbKey = dbKeyFactory.newKey(rs);
                t = (T) db.getCache(schemaTable).get(dbKey);
            }
            if (t == null) {
                t = load(con, rs, dbKey);
                if (doCache) {
                    db.getCache(schemaTable).put(dbKey, t);
                }
            }
            if (rs.next()) {
                throw new RuntimeException("Multiple records found");
            }
            return t;
        }
    }

    public final DBIterator<T> getManyBy(DBClause dbClause, int from, int to) {
        return getManyBy(dbClause, from, to, defaultSort());
    }

    public final DBIterator<T> getManyBy(DBClause dbClause, int from, int to, String sort) {
        Connection con = null;
        String sql ="SELECT * FROM " + schemaTable
                + " WHERE " + dbClause.getClause() + (multiversion ? " AND latest = TRUE " : " ") + sort
                + DBUtils.limitsClause(from, to);
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable
                    + " WHERE " + dbClause.getClause() + (multiversion ? " AND latest = TRUE " : " ") + sort
                    + DBUtils.limitsClause(from, to));
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            i = DBUtils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DBIterator<T> getManyBy(DBClause dbClause, int height, int from, int to) {
        return getManyBy(dbClause, height, from, to, defaultSort());
    }

    public final DBIterator<T> getManyBy(DBClause dbClause, int height, int from, int to, String sort) {
        if (height < 0 || doesNotExceed(height)) {
            return getManyBy(dbClause, from, to, sort);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + " AS a WHERE " + dbClause.getClause()
                    + "AND a.height <= ?" + (multiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height <= ? AND b.height > a.height))) "
                    : " ") + sort
                    + DBUtils.limitsClause(from, to));
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            i = DBUtils.setLimits(++i, pstmt, from, to);
            return getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DBIterator<T> getManyBy(Connection con, PreparedStatement pstmt, boolean cache) {
        final boolean doCache = cache && db.isInTransaction();
        return new DBIterator<>(con, pstmt, (connection, rs) -> {
            T t = null;
            DBKey dbKey = null;
            if (doCache) {
                dbKey = dbKeyFactory.newKey(rs);
                t = (T) db.getCache(schemaTable).get(dbKey);
            }
            if (t == null) {
                t = load(connection, rs, dbKey);
                if (doCache) {
                    db.getCache(schemaTable).put(dbKey, t);
                }
            }
            return t;
        });
    }

    public final DBIterator<T> search(String query, DBClause dbClause, int from, int to) {
        return search(query, dbClause, from, to, " ORDER BY ft.score DESC ");
    }

    public final DBIterator<T> search(String query, DBClause dbClause, int from, int to, String sort) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT " + schemaTable + ".*, ft.score FROM " + schemaTable
                    + ", ftl_search('" + schema + "', '" + table + "', ?, 2147483647, 0) ft "
                    + " WHERE " + schemaTable + ".db_id = ft.keys[0] "
                    + (multiversion ? " AND " + schemaTable + ".latest = TRUE " : " ")
                    + " AND " + dbClause.getClause() + sort
                    + DBUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setString(++i, query);
            i = dbClause.set(pstmt, ++i);
            i = DBUtils.setLimits(i, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DBIterator<T> getAll(int from, int to) {
        return getAll(from, to, defaultSort());
    }

    public final DBIterator<T> getAll(int from, int to, String sort) {
        Connection con = null;
        try {
            con = getConnection();
            String sql = "SELECT * FROM " + schemaTable
                    + (multiversion ? " WHERE latest = TRUE " : " ") + sort
                    + DBUtils.limitsClause(from, to);
            PreparedStatement pstmt = con.prepareStatement(sql);
            DBUtils.setLimits(1, pstmt, from, to);
            return getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public final DBIterator<T> getAll(int height, int from, int to) {
        return getAll(height, from, to, defaultSort());
    }

    public final DBIterator<T> getAll(int height, int from, int to, String sort) {
        if (height < 0 || doesNotExceed(height)) {
            return getAll(from, to, sort);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + schemaTable + " AS a WHERE height <= ?"
                    + (multiversion ? " AND (latest = TRUE OR (latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE b.height > ? AND " + dbKeyFactory.getSelfJoinClause()
                    + ") AND NOT EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE b.height <= ? AND " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height > a.height))) " : " ") + sort
                    + DBUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setInt(++i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            i = DBUtils.setLimits(++i, pstmt, from, to);
            return getManyBy(con, pstmt, false);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public final int getCount() {
        return multiversion ? super.getCount(LATEST) : super.getCount();
    }

    @Override
    public final int getCount(DBClause dbClause) {
        return multiversion ? super.getCount(dbClause.and(LATEST)) : super.getCount(dbClause);
    }

    public final int getCount(DBClause dbClause, int height) {
        if (height < 0 || doesNotExceed(height)) {
            return getCount(dbClause);
        }
        checkAvailable(height);
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM " + schemaTable + " AS a WHERE " + dbClause.getClause()
                    + "AND a.height <= ?" + (multiversion ? " AND (a.latest = TRUE OR (a.latest = FALSE "
                    + "AND EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause() + " AND b.height > ?) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + schemaTable + " AS b WHERE " + dbKeyFactory.getSelfJoinClause()
                    + " AND b.height <= ? AND b.height > a.height))) "
                    : " "));
            int i = 0;
            i = dbClause.set(pstmt, ++i);
            pstmt.setInt(i, height);
            if (multiversion) {
                pstmt.setInt(++i, height);
                pstmt.setInt(++i, height);
            }
            return getCount(pstmt);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    /**
     * 保存交易，并存储到缓存池中
     * @param t
     */
    public final void insert(T t) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        DBKey dbKey = dbKeyFactory.newKey(t);
        if (dbKey == null) {
            throw new RuntimeException("DBKey not set");
        }
        T cachedT = (T) db.getCache(schemaTable).get(dbKey);
        if (cachedT == null) {
            db.getCache(schemaTable).put(dbKey, t);
        } else if (t != cachedT) { // not a bug
            Logger.logDebugMessage("In cache : " + cachedT.toString() + ", inserting " + t.toString());
            throw new IllegalStateException("Different instance found in DB cache, perhaps trying to save an object "
                    + "that was read outside the current transaction");
        }
        try (Connection con = getConnection()) {
            if (multiversion) {
                Logger.logInfoMessage("UPDATE " + schemaTable
                        + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1");
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + schemaTable
                        + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
                    dbKey.setPK(pstmt);
                    pstmt.executeUpdate();
                }
            }
            save(con, t);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void popOffTo(int height) {
        if (multiversion) {
            VersionedEntityDBTable.popOff(db, schema, schemaTable, height, dbKeyFactory);
        } else {
            super.popOffTo(height);
        }
    }

    @Override
    public void trim(int height) {
        if (multiversion) {
            VersionedEntityDBTable.trim(db, schema, schemaTable, height, dbKeyFactory);
        } else {
            super.trim(height);
        }
    }

    @Override
    public final void createSearchIndex(Connection con) throws SQLException {
        if (fullTextSearchColumns != null) {
            Logger.logDebugMessage("Creating search index on " + schemaTable + " (" + fullTextSearchColumns + ")");
            FullTextTrigger.createIndex(con, schema, table, fullTextSearchColumns);
        }
    }

    private boolean doesNotExceed(int height) {
        return Shareschain.getBlockchain().getHeight() <= height && ! (isPersistent() && Shareschain.getBlockchainProcessor().isScanning());
    }

    /**
     * 在开发过程，SQL语句有可能写错，如果能把运行时出错的 SQL 语句直接打印出来，那对排错非常方便，因为其可以直接拷贝到数据库客户端进行调试。
     *
     * @param sql
     *            SQL 语句，可以带有 ? 的占位符
     * @param params
     *            插入到 SQL 中的参数，可单个可多个可不填
     * @return 实际 sql 语句
     */
    public static String printRealSql(String sql, Object[] params) {
        if(params == null || params.length == 0) {
            Logger.logDebugMessage("The SQL is------------>\n" + sql);
            return null;
        }

        if (!match(sql, params)) {
            Logger.logDebugMessage("SQL 语句中的占位符与参数个数不匹配。SQL：" + sql);
            return null;
        }

        int cols = params.length;
        Object[] values = new Object[cols];
        System.arraycopy(params, 0, values, 0, cols);

        for (int i = 0; i < cols; i++) {
            Object value = values[i];
            if (value instanceof Date) {
                values[i] = "'" + value + "'";
            } else if (value instanceof String) {
                values[i] = "'" + value + "'";
            } else if (value instanceof Boolean) {
                values[i] = (Boolean) value ? 1 : 0;
            }
        }

        String statement = String.format(sql.replaceAll("\\?", "%s"), values);

        Logger.logDebugMessage("The SQL is------------>\n" + statement);
        return statement;
    }

    /**
     * ? 和参数的实际个数是否匹配
     *
     * @param sql
     *            SQL 语句，可以带有 ? 的占位符
     * @param params
     *            插入到 SQL 中的参数，可单个可多个可不填
     * @return true 表示为 ? 和参数的实际个数匹配
     */
    private static boolean match(String sql, Object[] params) {
        if(params == null || params.length == 0) return true; // 没有参数，完整输出

        Matcher m = Pattern.compile("(\\?)").matcher(sql);
        int count = 0;
        while (m.find()) {
            count++;
        }

        return count == params.length;
    }

}

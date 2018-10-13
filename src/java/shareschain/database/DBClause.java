
package shareschain.database;

import shareschain.util.Convert;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class DBClause {

    public enum Op {

        LT("<"), LTE("<="), GT(">"), GTE(">="), NE("<>");

        private final String operator;

        Op(String operator) {
            this.operator = operator;
        }

        public String operator() {
            return operator;
        }
    }

    private final String clause;

    protected DBClause(String clause) {
        this.clause = clause;
    }

    final String getClause() {
        return clause;
    }

    protected abstract int set(PreparedStatement pstmt, int index) throws SQLException;

    public DBClause and(final DBClause other) {
        return new DBClause(this.clause + " AND " + other.clause) {
            @Override
            protected int set(PreparedStatement pstmt, int index) throws SQLException {
                index = DBClause.this.set(pstmt, index);
                index = other.set(pstmt, index);
                return index;
            }
        };
    }

    public static final DBClause EMPTY_CLAUSE = new FixedClause(" TRUE ");

    public static final class FixedClause extends DBClause {

        public FixedClause(String clause) {
            super(clause);
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) {
            return index;
        }

    }

    public static final class NullClause extends DBClause {

        public NullClause(String columnName) {
            super(" " + columnName + " IS NULL ");
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) {
            return index;
        }

    }

    public static final class NotNullClause extends DBClause {

        public NotNullClause(String columnName) {
            super(" " + columnName + " IS NOT NULL ");
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) {
            return index;
        }

    }

    public static final class StringClause extends DBClause {

        private final String value;

        public StringClause(String columnName, String value) {
            super(" " + columnName + " = ? ");
            this.value = value;
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setString(index, value);
            return index + 1;
        }

    }

    public static final class LikeClause extends DBClause {

        private final String prefix;

        public LikeClause(String columnName, String prefix) {
            super(" " + columnName + " LIKE ? ");
            this.prefix = prefix.replace("%", "\\%").replace("_", "\\_") + '%';
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setString(index, prefix);
            return index + 1;
        }
    }

    public static final class LongClause extends DBClause {

        private final long value;

        public LongClause(String columnName, long value) {
            super(" " + columnName + " = ? ");
            this.value = value;
        }

        public LongClause(String columnName, Op operator, long value) {
            super(" " + columnName + operator.operator() + "? ");
            this.value = value;
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setLong(index, value);
            return index + 1;
        }
    }

    public static final class IntClause extends DBClause {

        private final int value;

        public IntClause(String columnName, int value) {
            super(" " + columnName + " = ? ");
            this.value = value;
        }

        public IntClause(String columnName, Op operator, int value) {
            super(" " + columnName + operator.operator() + "? ");
            this.value = value;
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setInt(index, value);
            return index + 1;
        }

    }

    public static final class ByteClause extends DBClause {

        private final byte value;

        public ByteClause(String columnName, byte value) {
            super(" " + columnName + " = ? ");
            this.value = value;
        }

        public ByteClause(String columnName, Op operator, byte value) {
            super(" " + columnName + operator.operator() + "? ");
            this.value = value;
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setByte(index, value);
            return index + 1;
        }

    }

    public static final class BytesClause extends DBClause {

        private final byte[] value;

        public BytesClause(String columnName, byte[] value) {
            super(" " + columnName + " = ? ");
            this.value = value;
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setBytes(index, value);
            return index + 1;
        }

    }

    public static final class HashClause extends DBClause {

        private final byte[] hashValue;
        private final long idValue;

        public HashClause(String hashColumnName, String idColumnName, byte[] hashValue) {
            super(" " + idColumnName + " = ? AND " + hashColumnName + " = ? ");
            this.hashValue = hashValue;
            this.idValue = Convert.fullHashToId(hashValue);
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setLong(index, idValue);
            pstmt.setBytes(index + 1, hashValue);
            return index + 2;
        }
    }

    public static final class BooleanClause extends DBClause {

        private final boolean value;

        public BooleanClause(String columnName, boolean value) {
            super(" " + columnName + " = ? ");
            this.value = value;
        }

        @Override
        protected int set(PreparedStatement pstmt, int index) throws SQLException {
            pstmt.setBoolean(index, value);
            return index + 1;
        }

    }

}

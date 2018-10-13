
package shareschain.blockchain;

import shareschain.ShareschainException;
import shareschain.database.Table;
import shareschain.database.DB;
import shareschain.util.Convert;
import shareschain.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TransactionHome {

    public static TransactionHome forChain(Chain chain) {
        if (chain.getTransactionHome() != null) {
            throw new IllegalStateException("already set");
        }
        return new TransactionHome(chain);
    }

    private final Chain chain;
    private final Table transactionTable;

    private TransactionHome(Chain chain) {
        this.chain = chain;
        transactionTable = new Table(chain.getSchemaTable(chain instanceof Mainchain ? "transaction_sctk" : "transaction"));
    }

    static SmcTransactionImpl findSmcTransaction(long transactionId) {
        try (Connection con = DB.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction_sctk WHERE id = ? ORDER BY height DESC")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return (SmcTransactionImpl)TransactionImpl.loadTransaction(Mainchain.mainchain, rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (ShareschainException.ValidationException e) {
            throw new RuntimeException("Transaction already in database, id = " + transactionId + ", does not pass validation!", e);
        }
    }

    public TransactionImpl findTransaction(byte[] fullHash) {
        return findTransaction(fullHash, Integer.MAX_VALUE);
    }

    public TransactionImpl findTransaction(byte[] fullHash, int height) {
        long transactionId = Convert.fullHashToId(fullHash);
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                        return TransactionImpl.loadTransaction(chain, rs);
                    }
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (ShareschainException.ValidationException e) {
            throw new RuntimeException("Transaction already in database, full_hash = " + Convert.toHexString(fullHash)
                    + ", does not pass validation!", e);
        }
    }

    static boolean hasSmcTransaction(long transactionId) {
        return hasSmcTransaction(transactionId, Integer.MAX_VALUE);
    }

    /**
     * 判断交易是否存在；
     * 1.通过交易id判断交易是否存在
     * 2.如果存在该交易，将通过高度判断，此交易是否已经发生
     * @param transactionId
     * @param height
     * @return
     */
    static boolean hasSmcTransaction(long transactionId, int height) {
        try (Connection con = DB.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT height FROM transaction_sctk WHERE id = ? ORDER BY height DESC")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("height") <= height) {
                        return true;
                    }
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    //判断交易是否已经存在该链上(通过交易id和交易的hash值做判断)
    boolean hasTransaction(Transaction transaction) {
        return hasTransaction(transaction.getFullHash(), transaction.getId(), Integer.MAX_VALUE);
    }


    boolean hasTransaction(byte[] fullHash) {
        return hasTransaction(fullHash, Convert.fullHashToId(fullHash), Integer.MAX_VALUE);
    }

    //判断交易是否已经存在该链上(通过交易id和交易的hash值做判断)
    public boolean hasTransaction(byte[] fullHash, long transactionId, int height) {

        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash, height FROM " + transactionTable.getSchemaTable() + " WHERE id = ?")) {
            pstmt.setLong(1, transactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (Arrays.equals(rs.getBytes("full_hash"), fullHash) && rs.getInt("height") <= height) {
                        return true;
                    }
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    List<byte[]> findChildTransactionFullHashes(long smcTransactionId) {
        if (chain == Mainchain.mainchain) {
            throw new RuntimeException("Invalid chain");
        }
        List<byte[]> list = new ArrayList<>();
        try (Connection con = transactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT full_hash FROM " + transactionTable.getSchemaTable()
                     + " WHERE smc_transaction_id = ? ORDER BY smc_transaction_id, transaction_index")) {
            pstmt.setLong(1, smcTransactionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getBytes("full_hash"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return list;
    }


    /**
     * 根据区块id，到交易确认表transaction_sctk 中查询交易数据
     * @param blockId
     * @return
     */
    static List<SmcTransactionImpl> findBlockTransactions(long blockId) {
        try (Connection con = DB.getConnection()) {
            return findBlockTransactions(con, blockId);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    /**
     * 根据区块id，到交易确认表transaction_sctk 中查询交易数据
     * @param con
     * @param blockId
     * @return
     */
    static List<SmcTransactionImpl> findBlockTransactions(Connection con, long blockId) {
        List<SmcTransactionImpl> list = new ArrayList<>();
        try (PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction_sctk"
                + " WHERE block_id = ? ORDER BY transaction_index")) {
            pstmt.setLong(1, blockId);
            pstmt.setFetchSize(50);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add((SmcTransactionImpl)TransactionImpl.loadTransaction(Mainchain.mainchain, rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } catch (ShareschainException.ValidationException e) {
            throw new RuntimeException("Transaction already in database for block_id = " + Long.toUnsignedString(blockId)
                    + " does not pass validation!", e);
        }
        return list;
    }

    List<PrunableTransaction> findPrunableTransactions(Connection con, int minTimestamp, int maxTimestamp) {
        List<PrunableTransaction> result = new ArrayList<>();
        try (PreparedStatement pstmt = con.prepareStatement("SELECT full_hash, type, subtype, "
                + "has_prunable_attachment AS prunable_attachment, "
                + "has_prunable_message AS prunable_plain_message, "
                + "has_prunable_encrypted_message AS prunable_encrypted_message "
                + "FROM " + transactionTable.getSchemaTable() + " WHERE (timestamp BETWEEN ? AND ?) AND "
                + "(has_prunable_attachment = TRUE OR has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE)")) {
            pstmt.setInt(1, minTimestamp);
            pstmt.setInt(2, maxTimestamp);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    byte[] fullHash = rs.getBytes("full_hash");
                    byte type = rs.getByte("type");
                    byte subtype = rs.getByte("subtype");
                    TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
                    result.add(new PrunableTransaction(fullHash, transactionType,
                            rs.getBoolean("prunable_attachment"),
                            rs.getBoolean("prunable_plain_message"),
                            rs.getBoolean("prunable_encrypted_message")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    static void saveTransactions(Connection con, List<SmcTransactionImpl> transactions) {
        try {
            for (SmcTransactionImpl transaction : transactions) {
                transaction.save(con, "transaction_sctk");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static class PrunableTransaction {
        private final byte[] fullHash;
        private final TransactionType transactionType;
        private final boolean prunableAttachment;
        private final boolean prunablePlainMessage;
        private final boolean prunableEncryptedMessage;

        private PrunableTransaction(byte[] fullHash, TransactionType transactionType, boolean prunableAttachment,
                                    boolean prunablePlainMessage, boolean prunableEncryptedMessage) {
            this.fullHash = fullHash;
            this.transactionType = transactionType;
            this.prunableAttachment = prunableAttachment;
            this.prunablePlainMessage = prunablePlainMessage;
            this.prunableEncryptedMessage = prunableEncryptedMessage;
        }

        public byte[] getFullHash() {
            return fullHash;
        }

        public TransactionType getTransactionType() {
            return transactionType;
        }

        public boolean hasPrunableAttachment() {
            return prunableAttachment;
        }

        public boolean hasPrunablePlainMessage() {
            return prunablePlainMessage;
        }

        public boolean hasPrunableEncryptedMessage() {
            return prunableEncryptedMessage;
        }
    }

    int getTransactionCount() {
        return transactionTable.getCount();
    }

}

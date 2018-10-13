
package shareschain.blockchain;

import shareschain.database.DBIterator;
import shareschain.database.DBUtils;
import shareschain.database.DB;
import shareschain.util.Filter;
import shareschain.util.ReadWriteUpdateLock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class BlockchainImpl implements Blockchain {

    private static final BlockchainImpl instance = new BlockchainImpl();

    public static BlockchainImpl getInstance() {
        return instance;
    }

    private BlockchainImpl() {}

    private final ReadWriteUpdateLock lock = new ReadWriteUpdateLock();
    private final AtomicReference<BlockImpl> lastBlock = new AtomicReference<>();

    @Override
    public void readLock() {
        lock.readLock().lock();
    }

    @Override
    public void readUnlock() {
        lock.readLock().unlock();
    }

    @Override
    public void updateLock() {
        lock.updateLock().lock();
    }

    @Override
    public void updateUnlock() {
        lock.updateLock().unlock();
    }

    public void writeLock() {
        lock.writeLock().lock();
    }

    public void writeUnlock() {
        lock.writeLock().unlock();
    }

    @Override
    public BlockImpl getLastBlock() {
        return lastBlock.get();
    }

    void setLastBlock(BlockImpl block) {
        lastBlock.set(block);
    }

    //获取最后一个区块的height
    @Override
    public int getHeight() {
        BlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getHeight();
    }

    @Override
    public int getLastBlockTimestamp() {
        BlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getTimestamp();
    }

    @Override
    public BlockImpl getLastBlock(int timestamp) {
        BlockImpl block = lastBlock.get();
        if (timestamp >= block.getTimestamp()) {
            return block;
        }
        return BlockDB.findLastBlock(timestamp);
    }

    @Override
    public BlockImpl getBlock(long blockId) {
        return getBlock(blockId, false);
    }

    @Override
    public BlockImpl getBlock(long blockId, boolean loadTransactions) {
        BlockImpl block = lastBlock.get();
        if (block.getId() == blockId) {
            return block;
        }
        return BlockDB.findBlock(blockId, loadTransactions);
    }

    @Override
    public boolean hasBlock(long blockId) {
        return lastBlock.get().getId() == blockId || BlockDB.hasBlock(blockId);
    }

    @Override
    public DBIterator<BlockImpl> getAllBlocks() {
        Connection con = null;
        try {
            con = BlockDB.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block ORDER BY db_id ASC");
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DBIterator<BlockImpl> getBlocks(int from, int to) {
        Connection con = null;
        try {
            con = BlockDB.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height <= ? AND height >= ? ORDER BY height DESC");
            int blockchainHeight = getHeight();
            pstmt.setInt(1, blockchainHeight - from);
            pstmt.setInt(2, blockchainHeight - to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DBIterator<BlockImpl> getBlocks(long accountId, int timestamp) {
        return getBlocks(accountId, timestamp, 0, -1);
    }

    @Override
    public DBIterator<BlockImpl> getBlocks(long accountId, int timestamp, int from, int to) {
        Connection con = null;
        try {
            con = BlockDB.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE generator_id = ? "
                    + (timestamp > 0 ? " AND timestamp >= ? " : " ") + "ORDER BY height DESC"
                    + DBUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            if (timestamp > 0) {
                pstmt.setInt(++i, timestamp);
            }
            DBUtils.setLimits(++i, pstmt, from, to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public int getBlockCount(long accountId) {
        try (Connection con = BlockDB.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM block WHERE generator_id = ?")) {
            pstmt.setLong(1, accountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DBIterator<BlockImpl> getBlocks(Connection con, PreparedStatement pstmt) {
        return new DBIterator<>(con, pstmt, BlockDB::loadBlock);
    }

    @Override
    public List<Long> getBlockIdsAfter(long blockId, int limit) {
        List<Long> result = new ArrayList<>();
        try (Connection con = BlockDB.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block "
                            + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                            + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<BlockImpl> getBlocksAfter(long blockId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<BlockImpl> result = new ArrayList<>();
        try (Connection con = BlockDB.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                        + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                        + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(BlockDB.loadBlock(con, rs, true));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<BlockImpl> getBlocksAfter(long blockId, List<Long> blockList) {
        if (blockList.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockImpl> result = new ArrayList<>();
        try (Connection con = BlockDB.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                        + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                        + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, blockList.size());
            try (ResultSet rs = pstmt.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    BlockImpl block = BlockDB.loadBlock(con, rs, true);
                    if (block.getId() != blockList.get(index++)) {
                        break;
                    }
                    result.add(block);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public long getBlockIdAtHeight(int height) {
        Block block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block.getId();
        }
        return BlockDB.findBlockIdAtHeight(height);
    }

    @Override
    public BlockImpl getBlockAtHeight(int height) {
        BlockImpl block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block;
        }
        return BlockDB.findBlockAtHeight(height);
    }

    @Override
    public BlockImpl getECBlock(int timestamp) {
        Block block = getLastBlock(timestamp);
        if (block == null) {
            return getBlockAtHeight(0);
        }
        return BlockDB.findBlockAtHeight(Math.max(block.getHeight() - 720, 0));
    }

    @Override
    public TransactionImpl getTransaction(Chain chain, byte[] fullHash) {
        return chain.getTransactionHome().findTransaction(fullHash);
    }

    @Override
    public boolean hasTransaction(Chain chain, byte[] fullHash) {
        return chain.getTransactionHome().hasTransaction(fullHash);
    }

    @Override
    public SmcTransactionImpl getSmcTransaction(long transactionId) {
        return TransactionHome.findSmcTransaction(transactionId);
    }

    @Override
    public boolean hasSmcTransaction(long transactionId) {
        return TransactionHome.hasSmcTransaction(transactionId);
    }

    @Override
    public int getTransactionCount(Chain chain) {
        return chain.getTransactionHome().getTransactionCount();
    }



    @Override
    public DBIterator<? extends SmcTransaction> getTransactions(Mainchain chain, long accountId,
                                                                int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to) {
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current blockchain height " + getHeight());
        }
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT * FROM transaction_sctk WHERE recipient_id = ? AND sender_id <> ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type < 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND height <= ? ");
            }
            buf.append("UNION ALL SELECT * FROM transaction_sctk WHERE sender_id = ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type < 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND height <= ? ");
            }

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(DBUtils.limitsClause(from, to));
            con = DB.db.getConnection(Mainchain.mainchain.getDBSchema());
            PreparedStatement pstmt;
            int i = 0;
            pstmt = con.prepareStatement(buf.toString());
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type < 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type < 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            DBUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(chain, con, pstmt);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DBIterator<SmcTransactionImpl> getTransactions(Mainchain chain, Connection con, PreparedStatement pstmt) {
        return new DBIterator<>(con, pstmt, new DBIterator.ResultSetReader<SmcTransactionImpl>() {
            @Override
            public SmcTransactionImpl get(Connection con, ResultSet rs) throws Exception {
                return (SmcTransactionImpl)TransactionImpl.loadTransaction(chain, rs);
            }
        });
    }

    @Override
    public List<TransactionImpl> getExpectedTransactions(Filter<Transaction> filter) {
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        BlockchainProcessorImpl blockchainProcessor = BlockchainProcessorImpl.getInstance();
        List<TransactionImpl> result = new ArrayList<>();
        readLock();
        try {
            blockchainProcessor.selectUnconfirmedSmcTransactions(duplicates, getLastBlock(), -1).forEach(
                    unconfirmedTransaction -> {
                        SmcTransactionImpl transaction = unconfirmedTransaction.getTransaction();
                        if (filter.ok(transaction)) {
                            result.add(transaction);
                        }
                    }
            );
        } finally {
            readUnlock();
        }
        return result;
    }

    public DBIterator<? extends Transaction> getExecutedTransactions(Chain chain, long senderId, long recipientId,
                                                              byte type, byte subtype,
                                                              int height, int numberOfConfirmations,
                                                              int from, int to) {
        Connection con = null;
        try {
            boolean isChildChain = false;

            String heightFilter;
            String phasingResultHeightFilter;
            if (height > 0) {
                //use the block_timestamp index because there is no index on transaction.height
                heightFilter = " transaction.block_timestamp = (SELECT timestamp FROM block WHERE height = ? LIMIT 1) ";
                phasingResultHeightFilter = " phasing_poll_result.height = ? ";
            } else {
                if (senderId == 0 && recipientId == 0) {
                    throw new IllegalArgumentException("Sender or recipient expected");
                }
                if (numberOfConfirmations > 0) {
                    height = getHeight() - numberOfConfirmations;
                    if (height < 0) {
                        throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                                + " exceeds current blockchain height " + getHeight());
                    }
                    heightFilter = " transaction.height <= ? ";
                    phasingResultHeightFilter = " phasing_poll_result.height <= ? ";
                } else {
                    heightFilter = null;
                    phasingResultHeightFilter = null;
                }
            }

            boolean hasTypeFilter = isChildChain && type >= 0 || !isChildChain && type < 0;
            StringBuilder accountAndTypeFilter = new StringBuilder();
            if (senderId != 0) {
                accountAndTypeFilter.append(" transaction.sender_id = ? ");
            }
            if (recipientId != 0) {
                accountAndTypeFilter.append(" transaction.recipient_id = ? ");
            }

            if (hasTypeFilter) {
                if (accountAndTypeFilter.length() > 0) {
                    accountAndTypeFilter.append(" AND ");
                }
                accountAndTypeFilter.append(" transaction.type = ? ");
                if (subtype >= 0) {
                    accountAndTypeFilter.append(" AND transaction.subtype = ? ");
                }
            }

            StringBuilder buf = new StringBuilder();
            if (isChildChain) {
                buf.append("SELECT transaction.*, transaction.height AS execution_height FROM transaction WHERE transaction.phased = FALSE AND ").append(accountAndTypeFilter);
            } else {
                buf.append("SELECT * FROM transaction_sctk AS transaction WHERE ").append(accountAndTypeFilter);
            }

            if (heightFilter != null) {
                if (accountAndTypeFilter.length() > 0) {
                    buf.append(" AND ");
                }
                buf.append(heightFilter);
            }

            if (isChildChain) {
                buf.append("UNION ALL SELECT transaction.*, phasing_poll_result.height AS execution_height FROM transaction ");
                buf.append(" JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
                buf.append("  AND transaction.full_hash = phasing_poll_result.full_hash ");
                buf.append(" WHERE transaction.phased = TRUE AND phasing_poll_result.approved = TRUE ");
                buf.append("  AND ").append(accountAndTypeFilter);

                if (heightFilter != null) {
                    if (accountAndTypeFilter.length() > 0) {
                        buf.append(" AND ");
                    }
                    buf.append(phasingResultHeightFilter);
                }
                buf.append("ORDER BY execution_height DESC, transaction_index DESC");
            } else {
                buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            }

            buf.append(DBUtils.limitsClause(from, to));

            con = DB.db.getConnection(chain.getDBSchema());
            PreparedStatement pstmt;

            int i = 0;
            pstmt = con.prepareStatement(buf.toString());

            boolean setPhasedTransactionsParameters = false;
            do {
                //loop is executed twice for child chain and once for SCTK chain

                if (senderId != 0) {
                    pstmt.setLong(++i, senderId);
                }
                if (recipientId != 0) {
                    pstmt.setLong(++i, recipientId);
                }

                if (hasTypeFilter) {
                    pstmt.setByte(++i, type);
                    if (subtype >= 0) {
                        pstmt.setByte(++i, subtype);
                    }
                }

                if (heightFilter != null) {
                    pstmt.setInt(++i, height);
                }

                if (isChildChain) {
                    setPhasedTransactionsParameters = !setPhasedTransactionsParameters;
                }
            } while (setPhasedTransactionsParameters);

            DBUtils.setLimits(++i, pstmt, from, to);

            return getTransactions((Mainchain)chain, con, pstmt);
        } catch (SQLException e) {
            DBUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }
}

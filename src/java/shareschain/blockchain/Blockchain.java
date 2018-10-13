
package shareschain.blockchain;

import shareschain.database.DBIterator;
import shareschain.util.Filter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public interface Blockchain {

    void readLock();

    void readUnlock();

    void updateLock();

    void updateUnlock();

    Block getLastBlock();

    Block getLastBlock(int timestamp);

    int getHeight();

    int getLastBlockTimestamp();

    Block getBlock(long blockId);

    Block getBlock(long blockId, boolean loadTransactions);

    Block getBlockAtHeight(int height);

    boolean hasBlock(long blockId);

    DBIterator<? extends Block> getAllBlocks();

    DBIterator<? extends Block> getBlocks(int from, int to);

    DBIterator<? extends Block> getBlocks(long accountId, int timestamp);

    DBIterator<? extends Block> getBlocks(long accountId, int timestamp, int from, int to);

    int getBlockCount(long accountId);

    DBIterator<? extends Block> getBlocks(Connection con, PreparedStatement pstmt);

    List<Long> getBlockIdsAfter(long blockId, int limit);

    List<? extends Block> getBlocksAfter(long blockId, int limit);

    List<? extends Block> getBlocksAfter(long blockId, List<Long> blockList);

    long getBlockIdAtHeight(int height);

    Block getECBlock(int timestamp);

    Transaction getTransaction(Chain chain, byte[] fullHash);

    boolean hasTransaction(Chain chain, byte[] fullHash);

    SmcTransaction getSmcTransaction(long transactionId);

    boolean hasSmcTransaction(long transactionId);

    int getTransactionCount(Chain chain);

    DBIterator<? extends SmcTransaction> getTransactions(Mainchain chain, long accountId, int numberOfConfirmations,
                                                         byte type, byte subtype, int blockTimestamp, int from, int to);

    DBIterator<? extends SmcTransaction> getTransactions(Mainchain chain, Connection con, PreparedStatement pstmt);

    List<? extends Transaction> getExpectedTransactions(Filter<Transaction> filter);


    DBIterator<? extends Transaction> getExecutedTransactions(Chain chain, long senderId, long recipientId,
                                                              byte type, byte subtype,
                                                              int height, int numberOfConfirmations,
                                                              int from, int to);

}

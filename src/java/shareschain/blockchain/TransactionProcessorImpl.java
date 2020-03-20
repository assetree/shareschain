
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import shareschain.database.DBClause;
import shareschain.database.DBIterator;
import shareschain.database.DBKey;
import shareschain.database.EntityDBTable;
import shareschain.database.DB;
import shareschain.node.NetworkHandler;
import shareschain.node.NetworkMessage;
import shareschain.node.TransactionsInventory;
import shareschain.util.Convert;
import shareschain.util.Listener;
import shareschain.util.Listeners;
import shareschain.util.Logger;
import shareschain.util.ThreadPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class TransactionProcessorImpl implements TransactionProcessor {

    private static final boolean enableTransactionRebroadcasting = Shareschain.getBooleanProperty("shareschain.enableTransactionRebroadcasting");
    private static final boolean testUnconfirmedTransactions = Shareschain.getBooleanProperty("shareschain.testUnconfirmedTransactions");
    private static final int maxUnconfirmedTransactions;
    static {
        int n = Shareschain.getIntProperty("shareschain.maxUnconfirmedTransactions");
        maxUnconfirmedTransactions = n <= 0 ? Integer.MAX_VALUE : n;
    }

    private static final TransactionProcessorImpl instance = new TransactionProcessorImpl();

    public static TransactionProcessorImpl getInstance() {
        return instance;
    }

    private final Map<DBKey, UnconfirmedTransaction> transactionCache = new HashMap<>();
    private volatile boolean cacheInitialized = false;

    final DBKey.LongKeyFactory<UnconfirmedTransaction> unconfirmedTransactionDBKeyFactory = new DBKey.LongKeyFactory<UnconfirmedTransaction>("id") {

        @Override
        public DBKey newKey(UnconfirmedTransaction unconfirmedTransaction) {
            return unconfirmedTransaction.getDBKey();
        }

    };

    final EntityDBTable<UnconfirmedTransaction> unconfirmedTransactionTable = new EntityDBTable<UnconfirmedTransaction>("public.unconfirmed_transaction", unconfirmedTransactionDBKeyFactory) {

        @Override
        protected UnconfirmedTransaction load(Connection con, ResultSet rs, DBKey dbKey) throws SQLException {
            return UnconfirmedTransaction.load(rs);
        }

        @Override
        protected void save(Connection con, UnconfirmedTransaction unconfirmedTransaction) throws SQLException {
            unconfirmedTransaction.save(con);
            if (transactionCache.size() < maxUnconfirmedTransactions) {
                transactionCache.put(unconfirmedTransaction.getDBKey(), unconfirmedTransaction);
            }
        }

        @Override
        public void popOffTo(int height) {
            try (Connection con = unconfirmedTransactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM unconfirmed_transaction WHERE height > ?")) {
                pstmt.setInt(1, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        UnconfirmedTransaction unconfirmedTransaction = load(con, rs, null);
                        waitingTransactions.add(unconfirmedTransaction);
                        transactionCache.remove(unconfirmedTransaction.getDBKey());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            super.popOffTo(height);
            unconfirmedDuplicates.clear();
        }

        @Override
        public void truncate() {
            super.truncate();
            clearCache();
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC, id ASC ";
        }

    };

    private final Set<TransactionImpl> broadcastedTransactions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Listeners<List<? extends Transaction>,Event> transactionListeners = new Listeners<>();

    // 将正在未处理的交易放在一个优先级队列中
    private final PriorityQueue<UnconfirmedTransaction> waitingTransactions = new PriorityQueue<UnconfirmedTransaction>(
            // 传进去了一个确定交易的优先级的比较器
            (UnconfirmedTransaction o1, UnconfirmedTransaction o2) -> {
                int result;
                if ((result = Boolean.compare(o2.getChain() != Mainchain.mainchain, o1.getChain() != Mainchain.mainchain)) != 0) {
                    return result;
                }
                if ((result = Integer.compare(o2.getHeight(), o1.getHeight())) != 0) {
                    return result;
                }
                if (o1.getChain() == Mainchain.mainchain && o2.getChain() == Mainchain.mainchain) {
                    if ((result = Long.compare(o1.getFee(), o2.getFee())) != 0) {
                        return result;
                    }
                }
                if ((result = Boolean.compare(o1.isBundled(), o2.isBundled())) != 0) {
                    return result;
                }
                if ((result = Boolean.compare(o2.getReferencedTransactionId() != null,
                        o1.getReferencedTransactionId() != null)) != 0) {
                    return result;
                }
                if ((result = Long.compare(o2.getArrivalTimestamp(), o1.getArrivalTimestamp())) != 0) {
                    return result;
                }
                return Long.compare(o2.getId(), o1.getId());
            })
    {
        // 重写了添加的方法，因为需要定义如何处理未确认交易超过最大值的情形
        //如果队列的值的大小超过了 设置的 maxUnconfirmedTransactions 的最大值，则删除第一个队列中首元素，
        //始终保持队列大小为系统设置 maxUnconfirmedTransactions值的大小
        @Override
        public boolean add(UnconfirmedTransaction unconfirmedTransaction) {
            if (!super.add(unconfirmedTransaction)) {
                return false;
            }
            if (size() > maxUnconfirmedTransactions) {
                UnconfirmedTransaction removed = remove();
                Logger.logDebugMessage("Dropped unconfirmed transaction " + removed.getStringId());
            }
            return true;
        }

    };

    private final Map<TransactionType, Map<String, Integer>> unconfirmedDuplicates = new HashMap<>();


    private final Runnable removeUnconfirmedTransactionsThread = () -> {

        try {
            try {
                if (Shareschain.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                List<UnconfirmedTransaction> expiredTransactions = new ArrayList<>();
                try (DBIterator<UnconfirmedTransaction> iterator = unconfirmedTransactionTable.getManyBy(
                        new DBClause.IntClause("expiration", DBClause.Op.LT, Shareschain.getEpochTime()), 0, -1, "")) {
                    while (iterator.hasNext()) {
                        expiredTransactions.add(iterator.next());
                    }
                }
                if (expiredTransactions.size() > 0) {
                    BlockchainImpl.getInstance().writeLock();
                    try {
                        try {
                            DB.db.beginTransaction();
                            for (UnconfirmedTransaction unconfirmedTransaction : expiredTransactions) {
                                removeUnconfirmedTransaction(unconfirmedTransaction.getTransaction());
                            }
                            DB.db.commitTransaction();
                        } catch (Exception e) {
                            Logger.logErrorMessage(e.toString(), e);
                            DB.db.rollbackTransaction();
                            throw e;
                        } finally {
                            DB.db.endTransaction();
                        }
                    } finally {
                        BlockchainImpl.getInstance().writeUnlock();
                    }
                }
            } catch (Exception e) {
                Logger.logMessageWithExcpt("Error removing unconfirmed transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private final Runnable rebroadcastTransactionsThread = () -> {

        try {
            try {
                if (Shareschain.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                List<Transaction> transactionList = new ArrayList<>();
                int curTime = Shareschain.getEpochTime();
                for (TransactionImpl transaction : broadcastedTransactions) {
                    if (transaction.getExpiration() < curTime || transaction.getChain().getTransactionHome().hasTransaction(transaction)) {
                        broadcastedTransactions.remove(transaction);
                    } else if (transaction.getTimestamp() < curTime - 30) {
                        transactionList.add(transaction);
                        if (transactionList.size() >= 10) {
                            TransactionsInventory.cacheTransactions(transactionList);
                            NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(transactionList));
                            transactionList.clear();
                        }
                    }
                }

                if (transactionList.size() > 0) {
                    TransactionsInventory.cacheTransactions(transactionList);
                    NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(transactionList));
                }

            } catch (Exception e) {
                Logger.logMessageWithExcpt("Error in transaction re-broadcasting thread", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private final Runnable processWaitingTransactionsThread = () -> {

        try {
            try {
                if (Shareschain.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                processWaitingTransactions();
            } catch (Exception e) {
                Logger.logMessageWithExcpt("Error processing waiting transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private TransactionProcessorImpl() {
        if (!Constants.isLightClient) {//如果不是轻客户端
            if (!Constants.isOffline) {//是否离线
                //如果在线就创建一个广播的交易线程，线程延迟时间23秒
                ThreadPool.scheduleThread("RebroadcastTransactions", rebroadcastTransactionsThread, 23);
            }
            //启动一个移除未确认的交易线程
            ThreadPool.scheduleThread("RemoveUnconfirmedTransactions", removeUnconfirmedTransactionsThread, 20);
            //启动一个处理等待交易的线程
            ThreadPool.scheduleThread("ProcessWaitingTransactions", processWaitingTransactionsThread, 1);
        }
    }

    @Override
    public boolean addListener(Listener<List<? extends Transaction>> listener, Event eventType) {
        return transactionListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<List<? extends Transaction>> listener, Event eventType) {
        return transactionListeners.removeListener(listener, eventType);
    }

    public void notifyListeners(List<? extends Transaction> transactions, Event eventType) {
        transactionListeners.notify(transactions, eventType);
    }

    @Override
    public DBIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions() {
        return unconfirmedTransactionTable.getAll(0, -1);
    }

    @Override
    public DBIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to) {
        return unconfirmedTransactionTable.getAll(from, to);
    }

    @Override
    public DBIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(String sort) {
        return unconfirmedTransactionTable.getAll(0, -1, sort);
    }

    @Override
    public DBIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to, String sort) {
        return unconfirmedTransactionTable.getAll(from, to, sort);
    }

    @Override
    public DBIterator<UnconfirmedTransaction> getUnconfirmedSmcTransactions() {
        return unconfirmedTransactionTable.getManyBy(new DBClause.IntClause("chain_id", Mainchain.mainchain.getId()), 0, -1,
                " ORDER BY transaction_height ASC, fee DESC, arrival_timestamp ASC, id ASC "); // order by fee
    }


    @Override
    public UnconfirmedTransaction getUnconfirmedTransaction(long transactionId) {
        DBKey dbKey = unconfirmedTransactionDBKeyFactory.newKey(transactionId);
        return getUnconfirmedTransaction(dbKey);
    }

    //通过交易id在交易缓存池中获取交易,如果缓存中没有该交易就从 PUBLIC.UNCONFIRMED_TRANSACTION 表中根据id获取
    private UnconfirmedTransaction getUnconfirmedTransaction(DBKey dbKey) {
        Shareschain.getBlockchain().readLock();
        try {
            UnconfirmedTransaction transaction = transactionCache.get(dbKey);
            if (transaction != null) {
                return transaction;
            }
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
        return unconfirmedTransactionTable.get(dbKey);
    }

    @Override
    public List<Long> getAllUnconfirmedTransactionIds() {
        List<Long> result = new ArrayList<>();
        try (Connection con = unconfirmedTransactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM unconfirmed_transaction");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public UnconfirmedTransaction[] getAllWaitingTransactions() {
        UnconfirmedTransaction[] transactions;
        BlockchainImpl.getInstance().readLock();
        try {
            transactions = waitingTransactions.toArray(new UnconfirmedTransaction[waitingTransactions.size()]);
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
        Arrays.sort(transactions, waitingTransactions.comparator());
        return transactions;
    }

    public Collection<UnconfirmedTransaction> getWaitingTransactions() {
        return Collections.unmodifiableCollection(waitingTransactions);
    }

    @Override
    public TransactionImpl[] getAllBroadcastedTransactions() {
        BlockchainImpl.getInstance().readLock();
        try {
            return broadcastedTransactions.toArray(new TransactionImpl[broadcastedTransactions.size()]);
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
    }

    /**
     * 广播交易
     * @param transaction
     * @throws ShareschainExceptions.ValidationExceptions
     */
    @Override
    public void broadcast(Transaction transaction) throws ShareschainExceptions.ValidationExceptions {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (transaction.getChain().getTransactionHome().hasTransaction(transaction)) {//该交易是否在链的表中已经存在
                Logger.logMessageWithExcpt("Transaction " + transaction.getStringId() + " already in blockchain, will not broadcast again");
                return;
            }
            //通过交易id在交易缓存池中获取交易,如果缓存中没有该交易就从 PUBLIC.UNCONFIRMED_TRANSACTION 表中根据id获取
            //如果获取到该交易并且系统启用了广播，就把交易放广播的交易池中并返回
            DBKey dbKey = unconfirmedTransactionDBKeyFactory.newKey(transaction.getId());
            if (getUnconfirmedTransaction(dbKey) != null) {
                //如果已经启用交易广播，在配置文件中配置,将该交易添加到广播交易的set集合中
                if (enableTransactionRebroadcasting) {
                    broadcastedTransactions.add((TransactionImpl) transaction);
                    Logger.logMessageWithExcpt("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will re-broadcast");
                } else {
                    Logger.logMessageWithExcpt("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will not broadcast again");
                }
                return;
            }
            //交易验证
            transaction.validate();
            //创建新的未确认交易，是否bundled设置false,主链是true，子链是false
            UnconfirmedTransaction unconfirmedTransaction = ((TransactionImpl) transaction).newUnconfirmedTransaction(System.currentTimeMillis(), false);
            //区块是否正在处理中，如果处理中就加入到队列稍后广播
            boolean broadcastLater = BlockchainProcessorImpl.getInstance().isProcessingBlock();
            if (broadcastLater) {//如果需要加工区块，就把当前交易添加到java中的PriorityQueue等待的优先级队列里面
                waitingTransactions.add(unconfirmedTransaction);
                //把交易添加到广播交易池中(set集合)
                broadcastedTransactions.add((TransactionImpl) transaction);
                Logger.logDebugMessage("Will broadcast new transaction later " + transaction.getStringId());
            } else {//如果区块没有处理中
                //处理未确认的交易，保存确认的交易到 PUBLIC.balance_sctk 表中，并在主链上存取交易快照
                // 并保存交易到未确认的交易表（unconfirmed_transaction），并更新到缓存中
                processTransaction(unconfirmedTransaction);
                Logger.logDebugMessage(String.format("Accepted new transaction %s on chain %s", Convert.toHexString(transaction.getFullHash()), transaction.getChain().getName()));

                // 返回只有一个对象的不能改变的列表
                List<Transaction> acceptedTransactions = Collections.singletonList(transaction);
                //缓存接受的交易
                //TransactionsInventory.cacheTransactions(acceptedTransactions);
                //广播交易到其它节点
                NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(acceptedTransactions));
                // 通知所有监听了TransactionProcessor.Event.ADDED_UNCONFIRMED_TRANSACTIONS 事件的观察者
                transactionListeners.notify(acceptedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
                if (enableTransactionRebroadcasting) {
                    broadcastedTransactions.add((TransactionImpl) transaction);
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void broadcastLater(Transaction transaction) {
        broadcastedTransactions.add((TransactionImpl)transaction);
    }

    @Override
    public void clearUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            List<Transaction> removed = new ArrayList<>();
            try {
                DB.db.beginTransaction();
                try (DBIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions()) {
                    for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                        unconfirmedTransaction.getTransaction().undoUnconfirmed();
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                }
                unconfirmedTransactionTable.truncate();
                DB.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                DB.db.rollbackTransaction();
                throw e;
            } finally {
                DB.db.endTransaction();
            }
            unconfirmedDuplicates.clear();
            waitingTransactions.clear();
            broadcastedTransactions.clear();
            transactionCache.clear();
            if (!removed.isEmpty()) {
                transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    /**
     * 1.撤销原来已经处理的未确认的交易
     * 2.清空交易缓存池
     * 3.清空未确认的交易表
     */
    @Override
    public void requeueAllUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (!DB.db.isInTransaction()) {
                try {
                    DB.db.beginTransaction();
                    requeueAllUnconfirmedTransactions();
                    DB.db.commitTransaction();
                } catch (Exception e) {
                    Logger.logErrorMessage(e.toString(), e);
                    DB.db.rollbackTransaction();
                    throw e;
                } finally {
                    DB.db.endTransaction();
                }
                return;
            }
            List<Transaction> removed = new ArrayList<>();
            try (DBIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions()) {
                for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                    //撤销原来已经处理的未确认的交易
                    unconfirmedTransaction.getTransaction().undoUnconfirmed();
                    if (removed.size() < maxUnconfirmedTransactions) {
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                    //waitingTransactions是一个PriorityQueue优先级队列；如果队列的值的大小超过了 设置的 maxUnconfirmedTransactions 的最大值，则删除队列中首元素，
                    //始终保持队列大小为系统设置 maxUnconfirmedTransactions值的大小
                    waitingTransactions.add(unconfirmedTransaction);
                }
            }
            //清空未确认交易表
            unconfirmedTransactionTable.truncate();
            unconfirmedDuplicates.clear();
            transactionCache.clear();
            if (!removed.isEmpty()) {
                transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void rebroadcastAllUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            try (DBIterator<UnconfirmedTransaction> oldNonBroadcastedTransactions = getAllUnconfirmedTransactions()) {
                for (UnconfirmedTransaction unconfirmedTransaction : oldNonBroadcastedTransactions) {
                    if (unconfirmedTransaction.getTransaction().isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                        Logger.logDebugMessage("Skipping duplicate unconfirmed transaction " + unconfirmedTransaction.getTransaction().getJSONObject().toString());
                    } else if (enableTransactionRebroadcasting) {
                        broadcastedTransactions.add(unconfirmedTransaction.getTransaction());
                    }
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    //从未确认交易表中删除已接受的交易
    private void removeUnconfirmedTransactions(Collection<? extends TransactionImpl> transactions) {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (!DB.db.isInTransaction()) {
                try {
                    DB.db.beginTransaction();
                    removeUnconfirmedTransactions(transactions);
                    DB.db.commitTransaction();
                } catch (Exception e) {
                    Logger.logErrorMessage(e.toString(), e);
                    DB.db.rollbackTransaction();
                    throw e;
                } finally {
                    DB.db.endTransaction();
                }
                return;
            }
            transactions.forEach(this::removeUnconfirmedTransaction);
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    /**
     * 从未确认交易表中删除已接受的交易
     * @param transaction
     */
    void removeUnconfirmedTransaction(TransactionImpl transaction) {
        if (!DB.db.isInTransaction()) {
            try {
                DB.db.beginTransaction();
                removeUnconfirmedTransaction(transaction);
                DB.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                DB.db.rollbackTransaction();
                throw e;
            } finally {
                DB.db.endTransaction();
            }
            return;
        }
        try (Connection con = unconfirmedTransactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("DELETE FROM unconfirmed_transaction WHERE id = ?")) {
            pstmt.setLong(1, transaction.getId());
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                //撤销未确认的余额表
                transaction.undoUnconfirmed();
                DBKey dbKey = unconfirmedTransactionDBKeyFactory.newKey(transaction.getId());
                transactionCache.remove(dbKey);
                transactionListeners.notify(Collections.singletonList(transaction), Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
                if (transaction.getChain() != Mainchain.mainchain) {
                    try (DBIterator<UnconfirmedTransaction> iterator = getUnconfirmedSmcTransactions()) {
                        while (iterator.hasNext()) {
                            Transaction smcTransaction = iterator.next().getTransaction();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage(e.toString(), e);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void processLater(Collection<? extends SmcTransaction> transactions) {
        long currentTime = System.currentTimeMillis();
        BlockchainImpl.getInstance().writeLock();
        try {
            transactions.forEach(smc -> {
                SmcTransactionImpl smcTransaction = (SmcTransactionImpl)smc;
                if (! TransactionHome.hasSmcTransaction(smcTransaction.getId(), Integer.MAX_VALUE)) {
                    boolean keep = true;
                    if (keep) {
                        smcTransaction.unsetBlock();
                        waitingTransactions.add(smcTransaction.newUnconfirmedTransaction(Math.min(currentTime, Convert.fromEpochTime(smcTransaction.getTimestamp())), true));
                    }
                }
            });
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    /**
     * 处理等待中的未确认交易：
     * 1.如果交易超过系统设置 maxUnconfirmedTransactions最大值的2倍，将按照队列排序来决定哪些交易被包含到当前区块中
     * 2.重新处理经过排序的交易
     */
    void processWaitingTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            //maxUnconfirmedTransactions 常量值默认为2000
            //如果未确认的交易数量超过设置的 maxUnconfirmedTransactions（默认2000）值的两倍，将按照队列排序来决定哪些交易被包含到当前区块中
            if (unconfirmedTransactionTable.getCount() / 2 > maxUnconfirmedTransactions) {
                Logger.logDebugMessage("Unconfirmed transaction table size exceeded twice the maximum allowed, re-queueing");
                requeueAllUnconfirmedTransactions();
            }
            if (waitingTransactions.size() > 0) {
                int currentTime = Shareschain.getEpochTime();
                List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
                boolean processedChildTransactions = false;
                while (true) {
                    Iterator<UnconfirmedTransaction> iterator = waitingTransactions.iterator();
                    while (iterator.hasNext()) {
                        UnconfirmedTransaction unconfirmedTransaction = iterator.next();
                        try {
                            unconfirmedTransaction.validate();
                            processTransaction(unconfirmedTransaction);
                            iterator.remove();
                            addedUnconfirmedTransactions.add(unconfirmedTransaction.getTransaction());
                        } catch (ShareschainExceptions.ExistingTransactionExceptions e) {
                            iterator.remove();
                        } catch (ShareschainExceptions.NotCurrentlyValidExceptions e) {
                            if (unconfirmedTransaction.getExpiration() < currentTime
                                    || currentTime - Convert.toEpochTime(unconfirmedTransaction.getArrivalTimestamp()) > 3600) {
                                iterator.remove();
                            }
                        } catch (ShareschainExceptions.ValidationExceptions | RuntimeException e) {
                            iterator.remove();
                        }
                    }
                    if (!processedChildTransactions) {
                        processedChildTransactions = true;
                    } else {
                        break;
                    }
                }
                if (addedUnconfirmedTransactions.size() > 0) {
                    transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    /**
     * 对节点的交易进行处理，更新数据库，并将广播池中不存在的交易再次像其它节点广播
     * @param transactions
     * @return
     * @throws ShareschainExceptions.NotValidExceptions
     */
    @Override
    public List<TransactionImpl> processNodeTransactions(List<Transaction> transactions) throws ShareschainExceptions.NotValidExceptions {
        if (Shareschain.getBlockchain().getHeight() <= Constants.LAST_KNOWN_BLOCK && !testUnconfirmedTransactions) {
            return Collections.emptyList();
        }
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }
        long arrivalTimestamp = System.currentTimeMillis();
        List<TransactionImpl> receivedTransactions = new ArrayList<>();
        List<TransactionImpl> sendToNodesTransactions = new ArrayList<>();
        List<TransactionImpl> addedUnconfirmedTransactions = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        for (Transaction inputTransaction : transactions) {
            try {
                TransactionImpl transaction = (TransactionImpl)inputTransaction;
                receivedTransactions.add(transaction);//添加到交易到接收的交易池
                //构建一个未确认的交易类型，包含交易对象、到达本节点的时间、字节的费用(交易的费用/交易的大小)、是否bundled
                UnconfirmedTransaction unconfirmedTransaction = transaction.newUnconfirmedTransaction(arrivalTimestamp, false);
                unconfirmedTransaction.validate();//验证交易
                //处理交易更新余额，添加一条未确认的交易到unconfirmed_transaction中，并将已处理的交易集合封装到可删除的交易集合（displaced）中，后续会根据这个集合做交易的后续删除交易处理
                processTransaction(unconfirmedTransaction);
                if (broadcastedTransactions.contains(transaction)) {//如果广播池中已经存在该交易，说明该交易已经被广播过，将不在往其它节点广播
                    Logger.logDebugMessage("Received back transaction " + transaction.getStringId()
                            + " that we broadcasted, will not forward again to nodes");
                } else {//如果该交易不存在广播池中，将构建一个往其它节点广播的交易集合sendToNodesTransactions
                    sendToNodesTransactions.add(transaction);
                }
                addedUnconfirmedTransactions.add(transaction);

            } catch (ShareschainExceptions.NotCurrentlyValidExceptions ignore) {
            } catch (ShareschainExceptions.ValidationExceptions | RuntimeException e) {
                Logger.logDebugMessage(String.format("Invalid transaction from node: %s", inputTransaction.getJSONObject()), e);
                exceptions.add(e);
            }
        }
        if (!sendToNodesTransactions.isEmpty()) {//如果要广播的交易池不为空，要将该交易广播给其它节点
            NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(sendToNodesTransactions));
        }
        if (!addedUnconfirmedTransactions.isEmpty()) {//如果未确认的交易不为空，添加一个交易监听的通知
            transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
        }
        //移除接收到的所有交易
        broadcastedTransactions.removeAll(receivedTransactions);
        if (!exceptions.isEmpty()) {
            throw new ShareschainExceptions.NotValidExceptions("Node sends invalid transactions: " + exceptions.toString());
        }
        return addedUnconfirmedTransactions;
    }

    /**
     * 处理未确认的交易
     * @param unconfirmedTransaction
     * @return
     * @throws ShareschainExceptions.ValidationExceptions
     */
    private void processTransaction(UnconfirmedTransaction unconfirmedTransaction) throws ShareschainExceptions.ValidationExceptions {
        TransactionImpl transaction = unconfirmedTransaction.getTransaction();
        int curTime = Shareschain.getEpochTime();//获取当前距离2018年1月1日的时间差，单位s
        //交易的到期时间（交易创建时间 + 15分钟）小于curTime，交易过期
        if (transaction.getExpiration() < curTime) {
            throw new ShareschainExceptions.NotCurrentlyValidExceptions("Expired transaction");
        }
        int maxTimestamp = curTime + Constants.MAX_TIMEDRIFT;
        //交易的时间戳是否合法,允许15s的误差
        if (transaction.getTimestamp() > maxTimestamp) {
            throw new ShareschainExceptions.NotCurrentlyValidExceptions("Transaction timestamp from the future");
        }
        //验证交易版本号，默认值是1，猜测通过前面区块的高度获取区块交易的版本号，与当前交易版本号是否一致，不过此功能没有实现，都是写死的值1
        if (transaction.getVersion() < 1) {
            throw new ShareschainExceptions.NotValidExceptions("Invalid transaction version");
        }
        BlockchainImpl.getInstance().writeLock();
        try {
            try {
                DB.db.beginTransaction();
                //判断区块高度是否合法，区块高度必须小于系统已知的区块高度，并且不是测试未确认的交易（testUnconfirmedTransactions 配置文件中配置）
                if (Shareschain.getBlockchain().getHeight() < Constants.LAST_KNOWN_BLOCK && !testUnconfirmedTransactions) {
                    throw new ShareschainExceptions.NotCurrentlyValidExceptions("Blockchain not ready to accept transactions");
                }
                //1.如果交易在未确认交易表(unconfirmed_transaction)中已经存在，抛出异常
                //2.交易如果已经在已完成表(transaction_sctk主链或transaction子链)中存在，抛出异常
                if (getUnconfirmedTransaction(unconfirmedTransaction.getDBKey()) != null || transaction.getChain().getTransactionHome().hasTransaction(transaction)) {
                    throw new ShareschainExceptions.ExistingTransactionExceptions("Transaction already processed");
                }
                //交易id不能为空
                transaction.validateId();
                //签名是否合法
                if (! transaction.verifySignature()) {
                    if (Account.getAccount(transaction.getSenderId()) != null) {
                        throw new ShareschainExceptions.NotValidExceptions("Transaction signature verification failed");
                    } else {
                        throw new ShareschainExceptions.NotCurrentlyValidExceptions("Unknown transaction sender");
                    }
                }


                /**
                 * 更新发送者的未确认余额表
                 * 交易发送者的未确认的余额小于(当前交易的花费+转账金额)返回false
                 * 更新余额表（PUBLIC.BALANCE_SCTK）中发送者的未确认余额unconfirmed_balance字段值
                 */
                if (! transaction.applyUnconfirmed()) {
                    throw new ShareschainExceptions.InsufficientBalanceExceptions("Insufficient balance");
                }


                if (transaction.isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                    throw new ShareschainExceptions.NotCurrentlyValidExceptions("Duplicate unconfirmed transaction");
                }

                //保存交易到未确认的交易表（unconfirmed_transaction）
                unconfirmedTransactionTable.insert(unconfirmedTransaction);

                DB.db.commitTransaction();
            } catch (Exception e) {
                DB.db.rollbackTransaction();
                throw e;
            } finally {
                DB.db.endTransaction();
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    /**
     * 为ChildBlockSmcTransaction 类型的交易获取更高的交易费用
     * @param transaction
     * @return
     * @throws ShareschainExceptions.NotCurrentlyValidExceptions
     */

    private static final Comparator<UnconfirmedTransaction> cachedUnconfirmedTransactionComparator = (UnconfirmedTransaction t1, UnconfirmedTransaction t2) -> {
        int compare;
        // Sort by transaction_height ASC
        compare = Integer.compare(t1.getHeight(), t2.getHeight());
        if (compare != 0)
            return compare;
        // Sort by is_bundled DESC
        compare = Boolean.compare(t1.isBundled(), t2.isBundled());
        if (compare != 0)
            return -compare;
        // Sort by arrival_timestamp ASC
        compare = Long.compare(t1.getArrivalTimestamp(), t2.getArrivalTimestamp());
        if (compare != 0)
            return compare;
        // Sort by transaction ID ASC
        return Long.compare(t1.getId(), t2.getId());
    };

    /**
     * Get the cached unconfirmed transactions
     *
     * @param   exclude                 List of transaction identifiers to exclude
     */
    @Override
    public SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(List<Long> exclude) {
        SortedSet<UnconfirmedTransaction> transactionSet = new TreeSet<>(cachedUnconfirmedTransactionComparator);
        Shareschain.getBlockchain().readLock();
        try {
            //
            // Initialize the unconfirmed transaction cache if it hasn't been done yet
            //
            synchronized(transactionCache) {
                if (!cacheInitialized) {
                    DBIterator<UnconfirmedTransaction> it = getAllUnconfirmedTransactions();
                    while (it.hasNext()) {
                        UnconfirmedTransaction unconfirmedTransaction = it.next();
                        transactionCache.put(unconfirmedTransaction.getDBKey(), unconfirmedTransaction);
                    }
                    cacheInitialized = true;
                }
            }
            //
            // Build the result set
            //
            transactionCache.values().forEach(transaction -> {
                if (Collections.binarySearch(exclude, transaction.getId()) < 0) {
                    transactionSet.add(transaction);
                }
            });
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
        return transactionSet;
    }

}

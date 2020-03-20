package shareschain.node;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.BlockchainProcessor;
import shareschain.blockchain.ChainTransactionId;
import shareschain.blockchain.Transaction;
import shareschain.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TransactionsInventory {

    /** Transaction cache */
    private static final ConcurrentHashMap<ChainTransactionId, Transaction> transactionCache = new ConcurrentHashMap<>();

    /** Pending transactions */
    private static final Set<ChainTransactionId> pendingTransactions = Collections.synchronizedSet(new HashSet<>());

    /** Currently not valid transactions */
    private static final ConcurrentHashMap<ChainTransactionId, Transaction> notCurrentlyValidTransactions = new ConcurrentHashMap<>();

    private TransactionsInventory() {}

    /**
     * Process a TransactionsInventory message (there is no response message)
     * 处理交易列表的消息
     *
     * @param   Node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl Node, NetworkMessage.TransactionsInventoryMessage request) {
        //获取请求交易id、交易hash、链的id集合，
        List<ChainTransactionId> transactionIds = request.getTransactionIds();
        //
        // Request transactions that are not already in our cache
        //构造ChainTransactionId类型的集合requestIds(大小最大100)，
        // 这个集合是用来构造后续请求其它节点获取交易信息的GetTransactionsMessage对象
        List<ChainTransactionId> requestIds = new ArrayList<>(Math.min(100, transactionIds.size()));
        for (ChainTransactionId transactionId : transactionIds) {
            if (transactionCache.get(transactionId) == null
                    && notCurrentlyValidTransactions.get(transactionId) == null
                    && !pendingTransactions.contains(transactionId)) {
                requestIds.add(transactionId);
                pendingTransactions.add(transactionId);
                if (Nodes.isLogLevelEnabled(Nodes.LOG_LEVEL_DETAILS)) {
                    Logger.logDebugMessage("Requesting transaction " + transactionId.getStringId());
                }
                if (requestIds.size() >= 100) {
                    break;
                }
            }
        }
        if (requestIds.isEmpty()) {
            return null;
        }
        //启动一个节点的队列线程池QueuedThreadPool(coreSize:2,maxSize=15)，来处理交易信息
        Nodes.nodesService.execute(() -> {
            //
            // Request the transactions, starting with the node that sent the TransactionsInventory
            // message.  We will update the transaction cache with transactions that have
            // been successfully processed.  We will keep contacting Nodes until
            // we have received all of the transactions or we run out of Nodes.
            //从节点发送交易列表的请求开始，我们将已经成功处理的交易，更新这个交易缓存池
            //我们将保持节点的连接直到我们已经接收了所有的交易或者失去了节点的链接
            //
            try {
                //获取所有能连接的节点列表
                List<Node> connectedNodes = Nodes.getConnectedNodes();
                if (connectedNodes.isEmpty()) {
                    return;
                }
                int startIndex = connectedNodes.indexOf(Node);
                if (startIndex < 0) {
                    startIndex = 0;
                }
                int index = startIndex;
                Set<Transaction> notAcceptedTransactions = new HashSet<>();
                while (true) {
                    //循环遍历可连接节点进行请求获取交易消息
                    Node feederNode = connectedNodes.get(index);
                    // 因为在处理当前节点发出的保存交易的消息，因此其它节点在接收到该消息后需要
                    // 构造一个获取交易消息的对象 GetTransactionsMessage，在这个消息中包含
                    //交易id、交易hash、所在链的id、消息id
                    NetworkMessage.GetTransactionsMessage transactionsRequest =
                            new NetworkMessage.GetTransactionsMessage(requestIds);
                    //向发送保存交易消息的节点发送获取交易消息的请求
                    NetworkMessage.TransactionsMessage response =
                            (NetworkMessage.TransactionsMessage)feederNode.sendRequest(transactionsRequest);
                    if (response != null && response.getTransactionCount() > 0) {
                        try {
                            //获取请求后的交易列表，并封装到未接收的交易池notAcceptedTransactions中，方便后面处理
                            List<Transaction> transactions = response.getTransactions();
                            notAcceptedTransactions.addAll(transactions);
                            //循环从请求的交易列表和未完成的交易列表中删除请求节点返回中包含的交易
                            transactions.forEach(tx -> {
                                ChainTransactionId transactionId = ChainTransactionId.getChainTransactionId(tx);
                                requestIds.remove(transactionId);
                                pendingTransactions.remove(transactionId);
                                if (Nodes.isLogLevelEnabled(Nodes.LOG_LEVEL_DETAILS)) {
                                    Logger.logDebugMessage("Received transaction " + tx.getStringId());
                                }
                            });
                            //获取一个当前节点已经处理之后的交易列表
                            List<? extends Transaction> addedTransactions = Shareschain.getTransactionProcessor().processNodeTransactions(transactions);
                            cacheTransactions(addedTransactions);//缓存已经处理过的交易列表
                            notAcceptedTransactions.removeAll(addedTransactions);//从未接受的交易列表中移除已经广播的交易
                        } catch (RuntimeException | ShareschainExceptions.ValidationExceptions e) {
                            //将该节点加入黑名单中,断开链接，并通知其它节点
                            feederNode.blacklist(e);
                        }
                    }
                    if (requestIds.isEmpty()) {
                        break;
                    }
                    //方法循环
                    index = (index < connectedNodes.size()-1 ? index + 1 : 0);
                    if (index == startIndex) {
                        break;
                    }
                }
                try {
                    //如果未接受的交易池中还存在未接受的交易，在进行循环处理一遍，因为交易都是从其它节点异步获取的，一些原来无效的交易可能变得有效了
                    notAcceptedTransactions.forEach(transaction -> notCurrentlyValidTransactions.put(ChainTransactionId.getChainTransactionId(transaction), transaction));
                    //some not currently valid transactions may have become valid as others were fetched from Nodes, try processing them again
                    List<? extends Transaction> addedTransactions = Shareschain.getTransactionProcessor().processNodeTransactions(new ArrayList<>(notCurrentlyValidTransactions.values()));
                    addedTransactions.forEach(transaction -> notCurrentlyValidTransactions.remove(ChainTransactionId.getChainTransactionId(transaction)));
                } catch (ShareschainExceptions.NotValidExceptions e) {
                    Logger.logErrorMessage(e.getMessage(), e); //should not happen
                }
            } finally {
                //循环请求的交易列表集合requestIds，并将该集合中的交易从未完成交易表中移除
                requestIds.forEach(pendingTransactions::remove);
            }
        });
        return null;
    }

    /**
     * Get a cached transaction
     *
     * @param   transactionId           The transaction identifier
     * @return                          Cached transaction or null
     */
    static Transaction getCachedTransaction(ChainTransactionId transactionId) {
        return transactionCache.get(transactionId);
    }

    /**
     * Add local transactions to the transaction cache
     *
     * @param   transactions            Local transactions
     */
    public static void cacheTransactions(List<? extends Transaction> transactions) {
        transactions.forEach(transaction -> transactionCache.put(ChainTransactionId.getChainTransactionId(transaction), transaction));
    }

    /*
      Purge the transaction cache when a block is pushed
     */
    static {
        Shareschain.getBlockchainProcessor().addListener((block) -> {
            final int now = Shareschain.getEpochTime();
            //当当前时间戳单位s，与交易的时间戳之差大于600s(10分钟，可能和区块的产生时间有关系)，将从交易缓存池中移除
            transactionCache.values().removeIf(transaction -> now - transaction.getTimestamp() > 10 * 60);
            //当当前时间戳单位s，与交易的时间戳之差大于600s(10分钟，可能和区块的产生时间有关系)，或交易的时间戳大于当前时间（交易不合法）将从没有验证的交易缓存池中移除
            notCurrentlyValidTransactions.values().removeIf(transaction -> now - transaction.getTimestamp() > 10 * 60
                    || transaction.getTimestamp() > now + Constants.MAX_TIMEDRIFT);
        }, BlockchainProcessor.Event.BLOCK_PUSHED);
    }
}

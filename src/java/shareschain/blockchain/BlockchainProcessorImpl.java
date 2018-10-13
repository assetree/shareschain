
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainException;
import shareschain.account.Account;
import shareschain.account.AccountLedger;
import shareschain.util.crypto.Crypto;
import shareschain.database.DBIterator;
import shareschain.database.DerivedDBTable;
import shareschain.database.FilteringIterator;
import shareschain.database.FullTextTrigger;
import shareschain.database.DB;
import shareschain.node.NetworkHandler;
import shareschain.node.NetworkMessage;
import shareschain.node.Node;
import shareschain.node.Nodes;
import shareschain.util.JSON;
import shareschain.util.Listener;
import shareschain.util.Listeners;
import shareschain.util.Logger;
import shareschain.util.ThreadPool;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockchainProcessorImpl implements BlockchainProcessor {

    private static final NavigableMap<Integer, byte[]> checksums;
    static {
        NavigableMap<Integer, byte[]> map = new TreeMap<>();
        map.put(0, null);
        map.put(Constants.CHECKSUM_BLOCK_1, Constants.isTestnet ?
                new byte[] {
                        91, 30, -58, 23, 95, -59, -78, 22, -13, 31, -16, 102, 79, -87, 83, 64, 27,
                        97, -67, -32, -96, 109, 103, 35, -87, 35, -16, -119, -25, 72, -128, 18
                }
                :
                new byte[] {
                        58, -59, 105, -15, 37, -75, 102, 83, -11, 89, 67, 44, 92, -70, -82, 123,
                        83, 76, 44, 39, -41, 14, -17, 85, -80, 2, -67, -19, 28, -66, -2, -7
                });
        checksums = Collections.unmodifiableNavigableMap(map);
    }

    private static final BlockchainProcessorImpl instance = new BlockchainProcessorImpl();

    public static BlockchainProcessorImpl getInstance() {
        return instance;
    }

    private final BlockchainImpl blockchain = BlockchainImpl.getInstance();

    private final ExecutorService networkService = Executors.newCachedThreadPool();
    private final List<DerivedDBTable> derivedTables = new CopyOnWriteArrayList<>();
    private final boolean trimDerivedTables = Shareschain.getBooleanProperty("shareschain.trimDerivedTables");
    private final int defaultNumberOfForkConfirmations = Shareschain.getIntProperty(Constants.isTestnet
            ? "shareschain.testnetNumberOfForkConfirmations" : "shareschain.numberOfForkConfirmations");
    private final boolean simulateEndlessDownload = Shareschain.getBooleanProperty("shareschain.simulateEndlessDownload");

    private int initialScanHeight;
    private volatile int lastTrimHeight;
    private volatile int lastRestoreTime = 0;
    private final Set<ChainTransactionId> prunableTransactions = new HashSet<>();

    private final Listeners<Block, Event> blockListeners = new Listeners<>();
    private volatile Node lastBlockchainFeeder;
    private volatile int lastBlockchainFeederHeight;
    private volatile boolean getMoreBlocks = true;

    private volatile boolean isDownloadSuspended = false;
    private volatile boolean isTrimming;
    private volatile boolean isScanning;
    private volatile boolean isDownloading;
    private volatile boolean isProcessingBlock;
    private volatile boolean isRestoring;
    private volatile boolean alreadyInitialized = false;
    private volatile long genesisBlockId;

    /**
     * Download blocks from random nodes
     *
     * Forks of 1-2 blocks are handled by the node block processor.  The block download processor
     * is responsible for blockchain synchronization during server start and for forks larger than
     * 2 blocks.  It runs at scheduled intervals to verify the current blockchain and switch to
     * a node fork if a better fork is found.
     */
    private final Runnable getMoreBlocksThread = new Runnable() {

        private final NetworkMessage getCumulativeDifficultyRequest = new NetworkMessage.GetCumulativeDifficultyMessage();
        private boolean nodeHasMore;
        private List<Node> connectedPublicNodes;
        private List<Long> chainBlockIds;
        private long totalTime = 1;
        private int totalBlocks;

        @Override
        public void run() {
            try {
                if (isDownloadSuspended) {
                    return;
                }
                //
                // Download blocks until we are up-to-date
                //
                while (true) {
                    if (!getMoreBlocks) {
                        return;
                    }
                    int chainHeight = blockchain.getHeight();
                    downloadNode();
                    if (blockchain.getHeight() == chainHeight) {
                        if (isDownloading && !simulateEndlessDownload) {
                            Logger.logMessage("Finished blockchain download");
                            isDownloading = false;
                        }
                        break;
                    }
                }
                //
                // Restore prunable data
                //
                int now = Shareschain.getEpochTime();
                if (!isRestoring && !prunableTransactions.isEmpty() && now - lastRestoreTime > 60 * 60) {
                    isRestoring = true;
                    lastRestoreTime = now;
                    networkService.submit(new RestorePrunableDataTask());
                }
            } catch (InterruptedException e) {
                Logger.logDebugMessage("Blockchain download thread interrupted");
            } catch (Throwable t) {
                Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString(), t);
                System.exit(1);
            }
        }

        /**
         * 下载节点信息
         * @throws InterruptedException
         */
        private void downloadNode() throws InterruptedException {
            try {
                long startTime = System.currentTimeMillis();
                /**
                 * 至少需要2个节点才可以下载
                 */
                int numberOfForkConfirmations = blockchain.getHeight() > Constants.LAST_CHECKSUM_BLOCK - 720 ?
                        defaultNumberOfForkConfirmations : Math.min(2, defaultNumberOfForkConfirmations);
                /**
                 * 从networkHandler中获取已连接的节点列表
                 */
                connectedPublicNodes = Nodes.getConnectedNodes();
                /**
                 *获取节点数目小于等于numberOfForkConfirmations ，无需下载，直接返回
                 */
                if (connectedPublicNodes.size() <= numberOfForkConfirmations) {
                    return;
                }
                nodeHasMore = true;
                /**
                 * 随机获取一个节点，如果节点为空，返回
                 */
                final Node node = Nodes.getAnyNode(connectedPublicNodes);
                if (node == null) {
                    return;
                }

                NetworkMessage.CumulativeDifficultyMessage response =
                        (NetworkMessage.CumulativeDifficultyMessage)node.sendRequest(getCumulativeDifficultyRequest);
                if (response == null) {
                    return;
                }
                //获取当前节点区块链的累积难度
                BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
                //从其它节点中获取的累积难度
                BigInteger betterCumulativeDifficulty = response.getCumulativeDifficulty();
                /**
                 * 如果通信节点累计高度小于当前节点的累计高度，说明当前节点区块比较新，返回
                 */
                if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                    return;
                }
                // 发现了一个比本地链的长节点
                lastBlockchainFeeder = node;

                // 记录发现的这个节点的区块高度
                lastBlockchainFeederHeight = response.getBlockHeight();
                /**
                 * 如果获取的累积难度值与当前节点一致，说明区块一致，直接返回
                 */
                if (betterCumulativeDifficulty.equals(curCumulativeDifficulty)) {
                    return;
                }

                long commonMilestoneBlockId = genesisBlockId;

                /**
                 *获取里程碑节点
                 */
                if (blockchain.getHeight() > 0) {
                    commonMilestoneBlockId = getCommonMilestoneBlockId(node);
                }

                if (commonMilestoneBlockId == 0 || !nodeHasMore) {
                    return;
                }

                blockchain.updateLock();
                try {
                    chainBlockIds = getBlockIdsAfterCommon(node, commonMilestoneBlockId, false);

                    if (chainBlockIds.size() < 2 || !nodeHasMore) {
                        return;
                    }

                    final long commonBlockId = chainBlockIds.get(0);
                    final Block commonBlock = blockchain.getBlock(commonBlockId);
                    if (commonBlock == null || blockchain.getHeight() - commonBlock.getHeight() >= 720) {
                        if (commonBlock != null) {
                            Logger.logDebugMessage(node + " advertised chain with better difficulty, but the last common block is at height " + commonBlock.getHeight());
                        }
                        return;
                    }
                    if (simulateEndlessDownload) {
                        isDownloading = true;
                        return;
                    }
                    /*
                     *里程碑区块与最终区块高度之差大于10 ，说明需要同步的区块大于10个，设置当前节点为正在下载区块状态
                     */
                    if (!isDownloading && lastBlockchainFeederHeight - commonBlock.getHeight() > 10) {
                        Logger.logMessage("Blockchain download in progress");
                        isDownloading = true;
                    }

                    if (betterCumulativeDifficulty.compareTo(blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
                        return;
                    }

                    long lastBlockId = blockchain.getLastBlock().getId();
                    /**
                     * 下载区块
                     */
                    downloadBlockchain(node, commonBlock, commonBlock.getHeight());

                    if (blockchain.getHeight() - commonBlock.getHeight() <= 10) {
                        return;
                    }

                    int confirmations = 0;
                    for (Node otherNode : connectedPublicNodes) {
                        if (confirmations >= numberOfForkConfirmations) {
                            break;
                        }
                        if (node.getHost().equals(otherNode.getHost())) {
                            continue;
                        }
                        chainBlockIds = getBlockIdsAfterCommon(otherNode, commonBlockId, true);
                        if (chainBlockIds.isEmpty()) {
                            continue;
                        }
                        long otherNodeCommonBlockId = chainBlockIds.get(0);
                        if (otherNodeCommonBlockId == blockchain.getLastBlock().getId()) {
                            confirmations++;
                            continue;
                        }
                        Block otherNodeCommonBlock = blockchain.getBlock(otherNodeCommonBlockId);
                        if (blockchain.getHeight() - otherNodeCommonBlock.getHeight() >= 720) {
                            continue;
                        }
                        NetworkMessage.CumulativeDifficultyMessage otherNodeResponse =
                                (NetworkMessage.CumulativeDifficultyMessage)node.sendRequest(getCumulativeDifficultyRequest);
                        if (otherNodeResponse == null) {
                            continue;
                        }
                        /**
                         *
                         */
                        if (otherNodeResponse.getCumulativeDifficulty().compareTo(blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
                            continue;
                        }
                        Logger.logDebugMessage("Found a node with better difficulty");

                        downloadBlockchain(otherNode, otherNodeCommonBlock, commonBlock.getHeight());
                    }
                    Logger.logDebugMessage("Got " + confirmations + " confirmations");

                    if (blockchain.getLastBlock().getId() != lastBlockId) {
                        long time = System.currentTimeMillis() - startTime;
                        totalTime += time;
                        int numBlocks = blockchain.getHeight() - commonBlock.getHeight();
                        totalBlocks += numBlocks;
                        Logger.logMessage("Downloaded " + numBlocks + " blocks in "
                                + time / 1000 + " s, " + (totalBlocks * 1000) / totalTime + " per s, "
                                + totalTime * (lastBlockchainFeederHeight - blockchain.getHeight()) / ((long) totalBlocks * 1000 * 60) + " min left");
                    } else {
                        Logger.logDebugMessage("Did not accept node's blocks, back to our own fork");
                    }
                } finally {
                    blockchain.updateUnlock();
                }

            } catch (ShareschainException.StopException e) {
                Logger.logMessage("Blockchain download stopped: " + e.getMessage());
                throw new InterruptedException("Blockchain download stopped");
            } catch (Exception e) {
                Logger.logMessage("Error in blockchain download thread", e);
            }
        }

        /**
         * 从指定节点中获取一个里程碑的区块ID
         *
         * @param node
         * @return
         */
        private long getCommonMilestoneBlockId(Node node) {

            long lastMilestoneBlockId = 0;

            while (true) {
                long lastBlockId = lastMilestoneBlockId == 0 ? blockchain.getLastBlock().getId() : 0;

                NetworkMessage.MilestoneBlockIdsMessage response =
                        (NetworkMessage.MilestoneBlockIdsMessage)node.sendRequest(
                                new NetworkMessage.GetMilestoneBlockIdsMessage(lastBlockId, lastMilestoneBlockId));
                if (response == null) {
                    return 0;
                }
                List<Long> milestoneBlockIds = response.getBlockIds();
                if (milestoneBlockIds.isEmpty()) {
                    return genesisBlockId;
                }
                // prevent overloading with blockIds
                if (milestoneBlockIds.size() > 20) {
                    Logger.logDebugMessage("Obsolete or rogue node " + node.getHost() + " sends too many milestoneBlockIds, blacklisting");
                    node.blacklist("Too many milestoneBlockIds");
                    return 0;
                }
                if (response.isLastBlock()) {
                    nodeHasMore = false;
                }
                for (long blockId : milestoneBlockIds) {
                    if (BlockDB.hasBlock(blockId)) {
                        if (lastMilestoneBlockId == 0 && milestoneBlockIds.size() > 1) {
                            nodeHasMore = false;
                        }
                        return blockId;
                    }
                    lastMilestoneBlockId = blockId;
                }
            }

        }

        private List<Long> getBlockIdsAfterCommon(final Node node, final long startBlockId, final boolean countFromStart) {
            long matchId = startBlockId;
            List<Long> blockList = new ArrayList<>(720);
            boolean matched = false;
            int limit = countFromStart ? 720 : 1440;
            while (true) {
                /**
                 * 从node节点的数据库中按顺序获取区块ID列表，blockId:从那个ID开始，limit：一次获取个数，
                 */
                NetworkMessage.BlockIdsMessage response = (NetworkMessage.BlockIdsMessage)node.sendRequest(
                        new NetworkMessage.GetNextBlockIdsMessage(matchId, limit));
                //如果返回消息对象为null ，直接返回一个空的List
                if (response == null) {
                    return Collections.emptyList();
                }
                //如果返回消息对象中的blockID集合为空，跳出循环，返回一个空的List
                List<Long> nextBlockIds = response.getBlockIds();
                if (nextBlockIds.isEmpty()) {
                    break;
                }
                // prevent overloading with blockIds
                //返回blockIds集合长度大于需要查询的长度，说明node节点中存在重复的区块，断开node节点链接，并返回一个空的List
                if (nextBlockIds.size() > limit) {
                    Logger.logDebugMessage("Obsolete or rogue node " + node.getHost() + " sends too many nextBlockIds, blacklisting");
                    node.blacklist("Too many nextBlockIds");
                    return Collections.emptyList();
                }
                boolean matching = true;
                int count = 0;
                for (long blockId : nextBlockIds) {
                    //将当前节点数据库中不存在的blockid，添加到blockList中，注意，存在的最后一个区块同时加入blocklist中
                    if (matching) {
                        if (BlockDB.hasBlock(blockId)) {
                            matchId = blockId;
                            matched = true;
                        } else {
                            blockList.add(matchId);
                            blockList.add(blockId);
                            matching = false;
                        }
                    } else {
                        blockList.add(blockId);
                        //blocklist中最多添加720个区块
                        if (blockList.size() >= 720) {
                            break;
                        }
                    }
                    if (countFromStart && ++count >= 720) {
                        break;
                    }
                }
                if (!matching || countFromStart) {
                    break;
                }
            }
            //如果blockList为空，将开始blockId添加到blocklist中
            if (blockList.isEmpty() && matched) {
                blockList.add(matchId);
            }
            return blockList;
        }

        /**
         * Download the block chain
         *
         * @param   feederNode              Node supplying the blocks list
         * @param   commonBlock             Common block
         * @throws  InterruptedException    Download interrupted
         */
        private void downloadBlockchain(final Node feederNode, final Block commonBlock, final int startHeight) throws InterruptedException {
            Map<Long, NodeBlock> blockMap = new HashMap<>();
            //
            // Break the download into multiple segments.  The first block in each segment
            // is the common block for that segment.
            //
            List<GetNextBlocks> getList = new ArrayList<>();
            int segSize = 36;
            int stop = chainBlockIds.size() - 1;
            for (int start = 0; start < stop; start += segSize) {
                getList.add(new GetNextBlocks(chainBlockIds, start, Math.min(start + segSize, stop)));
            }
            int nextNodeIndex = ThreadLocalRandom.current().nextInt(connectedPublicNodes.size());
            long maxResponseTime = 100;
            Node slowestNode = null;
            //
            // Issue the getNextBlocks requests and get the results.  We will repeat
            // a request if the node didn't respond or returned a partial block list.
            // The download will be aborted if we are unable to get a segment after
            // retrying with different nodes.
            //
            download: while (!getList.isEmpty() && !connectedPublicNodes.isEmpty()) {
                //
                // Submit threads to issue 'getNextBlocks' requests.  The first segment
                // will always be sent to the feeder node.  Subsequent segments will
                // be sent to the feeder node if we failed trying to download the blocks
                // from another node.  We will stop the download and process any pending
                // blocks if we are unable to download a segment from the feeder node.
                //
                for (GetNextBlocks nextBlocks : getList) {
                    Node node;
                    //判断是否有节点在处理
                    if (nextBlocks.getRequestCount() > 1) {
                        break download;
                    }
                    //第一组区块由eederNode进行处理
                    if (nextBlocks.getStart() == 0 || nextBlocks.getRequestCount() != 0) {
                        node = feederNode;
                    } else {
                        while (true) {
                            if (connectedPublicNodes.isEmpty()) {
                                break download;
                            }
                            if (nextNodeIndex >= connectedPublicNodes.size()) {
                                nextNodeIndex = 0;
                            }
                            node = connectedPublicNodes.get(nextNodeIndex++);
                            if (node.getState() != Node.State.CONNECTED) {
                                connectedPublicNodes.remove(node);
                                continue;
                            }
                            break;
                        }
                    }
                    if (nextBlocks.getNode() == node) {
                        break download;
                    }
                    nextBlocks.setNode(node);
                    Future<List<Block>> future = networkService.submit(nextBlocks);
                    nextBlocks.setFuture(future);
                }
                //
                // Get the results.  A node is on a different fork if a returned
                // block is not in the block identifier list.
                //
                Iterator<GetNextBlocks> it = getList.iterator();
                while (it.hasNext()) {
                    GetNextBlocks nextBlocks = it.next();
                    List<Block> blockList;
                    try {
                        blockList = nextBlocks.getFuture().get();
                    } catch (ExecutionException exc) {
                        throw new RuntimeException(exc.getMessage(), exc);
                    }
                    if (blockList == null) {
                        connectedPublicNodes.remove(nextBlocks.getNode());
                        continue;
                    }
                    Node node = nextBlocks.getNode();
                    int index = nextBlocks.getStart() + 1;
                    for (Block block : blockList) {
                        if (block.getId() != chainBlockIds.get(index)) {
                            break;
                        }
                        blockMap.put(block.getId(), new NodeBlock(node, (BlockImpl)block));
                        index++;
                    }
                    if (index > nextBlocks.getStop()) {
                        it.remove();
                    } else {
                        nextBlocks.setStart(index - 1);
                    }
                    //找出同步最慢的节点
                    if (nextBlocks.getResponseTime() > maxResponseTime) {
                        maxResponseTime = nextBlocks.getResponseTime();
                        slowestNode = nextBlocks.getNode();
                    }
                }
            }
            //节点多，同步区块数量大的情况下，删除同步最慢的节点
            if (slowestNode != null &&
                    slowestNode != feederNode &&
                    NetworkHandler.getConnectionCount() >= segSize &&
                    NetworkHandler.getConnectionCount() >= NetworkHandler.getMaxOutboundConnections() &&
                    chainBlockIds.size() > 360) {
                Logger.logDebugMessage(slowestNode.getHost() + " took " + maxResponseTime + " ms, disconnecting");
                connectedPublicNodes.remove(slowestNode);
                slowestNode.disconnectNode();
            }
            //
            // Add the new blocks to the blockchain.  We will stop if we encounter
            // a missing block (this will happen if an invalid block is encountered
            // when downloading the blocks)
            //
            blockchain.writeLock();
            try {
                List<Block> forkBlocks = new ArrayList<>();
                for (int index = 1; index < chainBlockIds.size() && blockchain.getHeight() - startHeight < 720; index++) {
                    NodeBlock nodeBlock = blockMap.get(chainBlockIds.get(index));
                    if (nodeBlock == null) {
                        break;
                    }
                    BlockImpl block = nodeBlock.getBlock();
                    if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                        try {
                            pushBlock(block);
                        } catch (BlockNotAcceptedException e) {
                            nodeBlock.getNode().blacklist(e);
                        }
                    } else {
                        forkBlocks.add(block);
                    }
                }
                //
                // Process a fork
                //
                int myForkSize = blockchain.getHeight() - startHeight;
                if (!forkBlocks.isEmpty() && myForkSize < 720) {
                    Logger.logDebugMessage("Will process a fork of " + forkBlocks.size() + " blocks, mine is " + myForkSize);
                    try {
                        processFork(forkBlocks, commonBlock);
                    } catch (BlockNotAcceptedException e) {
                        feederNode.blacklist(e);
                    }
                }
            } finally {
                blockchain.writeUnlock();
            }
        }

    };

    /**
     * 处理节点分叉
     * @param forkBlocks
     * @param commonBlock
     * @throws BlockNotAcceptedException
     */
    private void processFork(final List<Block> forkBlocks, final Block commonBlock) throws BlockNotAcceptedException {
        BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
        List<BlockImpl> myPoppedOffBlocks = popOffTo(commonBlock);
        BlockImpl lowerCumulativeDifficultyBlock = null;
        int pushedForkBlocks = 0;
        try {
            try {
                if (blockchain.getLastBlock().getId() == commonBlock.getId()) {
                    for (Block block : forkBlocks) {
                        if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                            pushBlock((BlockImpl)block);
                            pushedForkBlocks += 1;
                        }
                    }
                }
            } finally {
                if (pushedForkBlocks > 0 && blockchain.getLastBlock().getCumulativeDifficulty().compareTo(curCumulativeDifficulty) <= 0) {
                    lowerCumulativeDifficultyBlock = blockchain.getLastBlock();
                    List<BlockImpl> nodePoppedOffBlocks = popOffTo(commonBlock);
                    pushedForkBlocks = 0;
                    for (BlockImpl block : nodePoppedOffBlocks) {
                        TransactionProcessorImpl.getInstance().processLater(block.getSmcTransactions());
                    }
                }
            }
            if (lowerCumulativeDifficultyBlock != null) {
                throw new BlockOfLowerDifficultyException(lowerCumulativeDifficultyBlock);
            }
        } finally {
            if (pushedForkBlocks == 0) {
                Logger.logDebugMessage("Didn't accept any blocks, pushing back my previous blocks");
                for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                    BlockImpl block = myPoppedOffBlocks.remove(i);
                    try {
                        pushBlock(block);
                    } catch (BlockNotAcceptedException e) {
                        Logger.logErrorMessage("Popped off block no longer acceptable: " + block.toString(), e);
                        break;
                    }
                }
            } else {
                Logger.logDebugMessage("Switched to node's fork");
                for (BlockImpl block : myPoppedOffBlocks) {
                    TransactionProcessorImpl.getInstance().processLater(block.getSmcTransactions());
                }
            }
        }
    }

    /**
     * Callable method to get the next block segment from the selected node
     */
    private static class GetNextBlocks implements Callable<List<Block>> {

        /** Callable future */
        private Future<List<Block>> future;

        /** Node */
        private Node node;

        /** Block identifier list */
        private final List<Long> blockIds;

        /** Start index */
        private int start;

        /** Stop index */
        private final int stop;

        /** Request count */
        private int requestCount;

        /** Time it took to return getNextBlocks */
        private long responseTime;

        /**
         * Create the callable future
         *
         * @param   blockIds            Block identifier list
         * @param   start               Start index within the list
         * @param   stop                Stop index within the list
         */
        GetNextBlocks(List<Long> blockIds, int start, int stop) {
            this.blockIds = blockIds;
            this.start = start;
            this.stop = stop;
            this.requestCount = 0;
        }

        /**
         * Return the result
         *
         * @return                      List of blocks or null if an error occurred
         */
        @Override
        public List<Block> call() {
            requestCount++;
            List<Long> idList = new ArrayList<>(stop - start);
            for (int i = start + 1; i <= stop; i++) {
                idList.add(blockIds.get(i));
            }
            long startTime = System.currentTimeMillis();
            NetworkMessage.BlocksMessage response = (NetworkMessage.BlocksMessage)node.sendRequest(
                    new NetworkMessage.GetNextBlocksMessage(blockIds.get(start), idList.size(), idList));
            responseTime = System.currentTimeMillis() - startTime;
            if (response == null) {
                return null;
            }
            if (response.getBlockCount() == 0) {
                return null;
            }
            if (response.getBlockCount() > idList.size()) {
                Logger.logDebugMessage("Obsolete or rogue node " + node.getHost() + " sends too many nextBlocks, blacklisting");
                node.blacklist("Too many nextBlocks");
                return null;
            }
            List<Block> blockList;
            try {
                blockList = response.getBlocks();
            } catch (RuntimeException | ShareschainException.NotValidException e) {
                Logger.logDebugMessage("Failed to parse block: " + e.toString(), e);
                node.blacklist(e);
                blockList = null;
            }
            return blockList;
        }

        /**
         * Return the callable future
         *
         * @return                      Callable future
         */
        public Future<List<Block>> getFuture() {
            return future;
        }

        /**
         * Set the callable future
         *
         * @param   future              Callable future
         */
        void setFuture(Future<List<Block>> future) {
            this.future = future;
        }

        /**
         * Return the node
         *
         * @return                      Node
         */
        public Node getNode() {
            return node;
        }

        /**
         * Set the node
         *
         * @param   node                Node
         */
        void setNode(Node node) {
            this.node = node;
        }

        /**
         * Return the start index
         *
         * @return                      Start index
         */
        public int getStart() {
            return start;
        }

        /**
         * Set the start index
         *
         * @param   start               Start index
         */
        void setStart(int start) {
            this.start = start;
        }

        /**
         * Return the stop index
         *
         * @return                      Stop index
         */
        public int getStop() {
            return stop;
        }

        /**
         * Return the request count
         *
         * @return                      Request count
         */
        public int getRequestCount() {
            return requestCount;
        }

        /**
         * Return the response time
         *
         * @return                      Response time
         */
        public long getResponseTime() {
            return responseTime;
        }
    }

    /**
     * Block returned by a node
     */
    private static class NodeBlock {

        /** Node */
        private final Node node;

        /** Block */
        private final BlockImpl block;

        /**
         * Create the node block
         *
         * @param   node                Node
         * @param   block               Block
         */
        NodeBlock(Node node, BlockImpl block) {
            this.node = node;
            this.block = block;
        }

        /**
         * Return the node
         *
         * @return                      Node
         */
        public Node getNode() {
            return node;
        }

        /**
         * Return the block
         *
         * @return                      Block
         */
        public BlockImpl getBlock() {
            return block;
        }
    }

    /**
     * Task to restore prunable data for downloaded blocks
     */
    private class RestorePrunableDataTask implements Runnable {

        @Override
        public void run() {
            Node node = null;
            try {
                //
                // Locate an archive node
                //
                List<Node> nodes = Nodes.getNodes(chkNode -> chkNode.providesService(Node.Service.PRUNABLE) &&
                        !chkNode.isBlacklisted() &&
                        (chkNode.getState() == Node.State.CONNECTED ||
                            (chkNode.getAnnouncedAddress() != null && chkNode.shareAddress())));
                while (!nodes.isEmpty()) {
                    int index = ThreadLocalRandom.current().nextInt(nodes.size());
                    Node chkNode = nodes.get(index);
                    if (chkNode.getState() != Node.State.CONNECTED) {
                        chkNode.connectNode();
                    }
                    if (chkNode.getState() == Node.State.CONNECTED) {
                        node = chkNode;
                        break;
                    }
                    nodes.remove(index);
                }
                if (node == null) {
                    Logger.logDebugMessage("Cannot find any archive nodes");
                    return;
                }
                Logger.logDebugMessage("Connected to archive node " + node.getHost());
                //
                // Make a copy of the prunable transaction list so we can remove entries
                // as we process them while still retaining the entry if we need to
                // retry later using a different archive node
                //
                Set<ChainTransactionId> processing;
                synchronized (prunableTransactions) {
                    processing = new HashSet<>(prunableTransactions.size());
                    processing.addAll(prunableTransactions);
                }
                Logger.logDebugMessage("Need to restore " + processing.size() + " pruned data");
                //
                // Request transactions in batches of 100 until all transactions have been processed
                //
                while (!processing.isEmpty()) {
                    //
                    // Get the pruned transactions from the archive node
                    //
                    List<ChainTransactionId>requestList = new ArrayList<>(100);
                    synchronized (prunableTransactions) {
                        Iterator<ChainTransactionId> it = processing.iterator();
                        while (it.hasNext()) {
                            requestList.add(it.next());
                            it.remove();
                            if (requestList.size() == 100)
                                break;
                        }
                    }
                    NetworkMessage.TransactionsMessage response = (NetworkMessage.TransactionsMessage)node.sendRequest(
                            new NetworkMessage.GetTransactionsMessage(requestList));
                    if (response == null) {
                        return;
                    }
                    //
                    // Restore the prunable data
                    //
                    List<Transaction> transactions = response.getTransactions();
                    if (transactions.isEmpty()) {
                        return;
                    }
                }
                Logger.logDebugMessage("Done retrieving prunable transactions from " + node.getHost());
            } catch (ShareschainException.NotValidException e) {
                Logger.logErrorMessage("Node " + node.getHost() + " returned invalid prunable transaction", e);
                node.blacklist(e);
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Unable to restore prunable data", e);
            } finally {
                isRestoring = false;
                Logger.logDebugMessage("Remaining " + prunableTransactions.size() + " pruned transactions");
            }
        }
    }

    private final Listener<Block> checksumListener = block -> {
        byte[] validChecksum = checksums.get(block.getHeight());
        if (validChecksum == null) {
            return;
        }
        int height = block.getHeight();
        int fromHeight = checksums.lowerKey(height);
        MessageDigest digest = Crypto.sha256();
        try (Connection con = DB.getConnection();
             PreparedStatement pstmt = con.prepareStatement(
                     "SELECT * FROM transaction_sctk WHERE height > ? AND height <= ? ORDER BY id ASC, timestamp ASC")) {
            pstmt.setInt(1, fromHeight);
            pstmt.setInt(2, height);
            try (DBIterator<SmcTransactionImpl> iterator = blockchain.getTransactions(Mainchain.mainchain, con, pstmt)) {
                while (iterator.hasNext()) {
                    digest.update(iterator.next().bytes());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        byte[] checksum = digest.digest();
        if (validChecksum.length == 0) {
            Logger.logMessage("Checksum calculated:\n" + Arrays.toString(checksum));
        } else if (!Arrays.equals(checksum, validChecksum)) {
            Logger.logErrorMessage("Checksum failed at block " + height + ": " + Arrays.toString(checksum));
            if (isScanning) {
                throw new RuntimeException("Invalid checksum, interrupting rescan");
            } else {
                popOffTo(fromHeight);
            }
        } else {
            Logger.logMessage("Checksum passed at block " + height);
        }
    };

    private BlockchainProcessorImpl() {
        final int trimFrequency = Shareschain.getIntProperty("shareschain.trimFrequency");
        //添加区块扫描事件
        blockListeners.addListener(block -> {
            if (block.getHeight() % 5000 == 0) {
                Logger.logMessage("processed block " + block.getHeight());
            }
            if (trimDerivedTables && block.getHeight() % trimFrequency == 0) {
                doTrimDerivedTables();
            }
        }, Event.BLOCK_SCANNED);

        //区块提交事件
        blockListeners.addListener(block -> {
            if (trimDerivedTables && block.getHeight() % trimFrequency == 0 && !isTrimming) {
                isTrimming = true;
                networkService.submit(() -> {
                    trimDerivedTables();
                    isTrimming = false;
                });
            }
            if (block.getHeight() % 5000 == 0) {
                Logger.logMessage("received block " + block.getHeight());
                if (!isDownloading || block.getHeight() % 50000 == 0) {
                    networkService.submit(DB.db::analyzeTables);
                }
            }
        }, Event.BLOCK_PUSHED);

        blockListeners.addListener(checksumListener, Event.BLOCK_PUSHED);

        blockListeners.addListener(block -> DB.db.analyzeTables(), Event.RESCAN_END);

        ThreadPool.runBeforeStart(() -> {
            alreadyInitialized = true;
            //添加创世区块
            addGenesisBlock();

            //是否强制回滚区块,默认设置为否
            if (Shareschain.getBooleanProperty("shareschain.forceScan")) {
                scan(0, Shareschain.getBooleanProperty("shareschain.forceValidate"));
            } else {
                boolean rescan;
                boolean validate;
                int height;
                try (Connection con = DB.getConnection();
                     Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM scan")) {
                    rs.next();
                    rescan = rs.getBoolean("rescan");
                    validate = rs.getBoolean("validate");
                    height = rs.getInt("height");
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
                if (rescan) {
                    scan(height, validate);
                }
            }
        }, false);

        //
        // Note: Nodes broadcast new blocks to all connected nodes.  So the only
        //       need to get blocks is during server startup and when a fork
        //       needs to be resolved.  The BlocksInventory processor will
        //       suspend and resume the block download thread as needed.
        //
        if (!Constants.isLightClient && !Constants.isOffline) {
            ThreadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread, 5);
        }

    }

    @Override
    public boolean addListener(Listener<Block> listener, BlockchainProcessor.Event eventType) {
        return blockListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<Block> listener, Event eventType) {
        return blockListeners.removeListener(listener, eventType);
    }

    @Override
    public void registerDerivedTable(DerivedDBTable table) {
        if (alreadyInitialized) {
            throw new IllegalStateException("Too late to register table " + table + ", must have done it in Shareschain.Init");
        }
        derivedTables.add(table);
    }

    @Override
    public void trimDerivedTables() {
        try {
            DB.db.beginTransaction();
            doTrimDerivedTables();
            DB.db.commitTransaction();
        } catch (Exception e) {
            Logger.logMessage(e.toString(), e);
            DB.db.rollbackTransaction();
            throw e;
        } finally {
            DB.db.endTransaction();
        }
    }

    private void doTrimDerivedTables() {
        lastTrimHeight = Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0);
        if (lastTrimHeight > 0) {
            for (DerivedDBTable table : derivedTables) {
                blockchain.readLock();
                try {
                    table.trim(lastTrimHeight);
                    DB.db.commitTransaction();
                } finally {
                    blockchain.readUnlock();
                }
            }
        }
    }

    List<DerivedDBTable> getDerivedTables() {
        return derivedTables;
    }

    @Override
    public Node getLastBlockchainFeeder() {
        return lastBlockchainFeeder;
    }

    @Override
    public int getLastBlockchainFeederHeight() {
        return lastBlockchainFeederHeight;
    }

    @Override
    public boolean isScanning() {
        return isScanning;
    }

    @Override
    public int getInitialScanHeight() {
        return initialScanHeight;
    }

    @Override
    public void suspendDownload(boolean suspend) {
        this.isDownloadSuspended = suspend;
    }

    @Override
    public boolean isDownloadSuspended() {
        return isDownloadSuspended;
    }

    @Override
    public boolean isDownloading() {
        return isDownloading;
    }

    @Override
    public boolean isProcessingBlock() {
        return isProcessingBlock;
    }

    @Override
    public int getMinRollbackHeight() {
        return trimDerivedTables ? (lastTrimHeight > 0 ? lastTrimHeight : Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0)) : 0;
    }

    @Override
    public long getGenesisBlockId() {
        return genesisBlockId;
    }

    /**
     * Process a single node block
     *
     * The block must be a continuation of the current chain or a replacement for the current last block
     *
     * @param   inputBlock              Node block
     * @throws ShareschainException            Block was not accepted
     */
    @Override
    public void processNodeBlock(Block inputBlock) throws ShareschainException {
        BlockImpl block = (BlockImpl)inputBlock;
        BlockImpl lastBlock = blockchain.getLastBlock();
        if (block.getPreviousBlockId() == lastBlock.getId()) {
            pushBlock(block);
        } else if (block.getPreviousBlockId() == lastBlock.getPreviousBlockId() && block.getTimestamp() < lastBlock.getTimestamp()) {
            blockchain.writeLock();
            try {
                if (lastBlock.getId() != blockchain.getLastBlock().getId()) {
                    return; // blockchain changed, ignore the block
                }
                BlockImpl previousBlock = blockchain.getBlock(lastBlock.getPreviousBlockId());
                lastBlock = popOffTo(previousBlock).get(0);
                try {
                    pushBlock(block);
                    TransactionProcessorImpl.getInstance().processLater(lastBlock.getSmcTransactions());
                    Logger.logDebugMessage("Last block " + lastBlock.getStringId() + " was replaced by " + block.getStringId());
                } catch (BlockNotAcceptedException e) {
                    Logger.logDebugMessage("Replacement block failed to be accepted, pushing back our last block");
                    pushBlock(lastBlock);
                    TransactionProcessorImpl.getInstance().processLater(block.getSmcTransactions());
                    throw e;
                }
            } finally {
                blockchain.writeUnlock();
            }
        } // else ignore the block
    }

    /**
     * Process multiple node blocks
     *
     * The node blocks must represent a 2-block fork where the common block is the block preceding
     * the current last block.
     *
     * @param   inputBlocks             Node blocks
     * @throws ShareschainException            Blocks were not accepted
     */
    @Override
    public void processNodeBlocks(List<Block> inputBlocks) throws ShareschainException {
        if (inputBlocks.size() != 2) {
            return;                     // We only handle 2-block forks
        }
        blockchain.writeLock();
        try {
            BlockImpl lastBlock = blockchain.getLastBlock();
            BlockImpl commonBlock = blockchain.getBlock(lastBlock.getPreviousBlockId());
            BlockImpl previousBlock = (BlockImpl)inputBlocks.get(0);
            if (commonBlock.getId() != previousBlock.getPreviousBlockId()) {
                return;                 // Blockchain has changed
            }
            processFork(inputBlocks, commonBlock);
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public List<BlockImpl> popOffTo(int height) {
        if (height < 0) {
            fullReset();
        } else if (height < blockchain.getHeight()) {
            return popOffTo(blockchain.getBlockAtHeight(height));
        }
        return Collections.emptyList();
    }

    @Override
    public void fullReset() {
        blockchain.writeLock();
        try {
            try {
                setGetMoreBlocks(false);
                //BlockDB.deleteBlock(Genesis.GENESIS_BLOCK_ID); // fails with stack overflow in H2
                BlockDB.deleteAll();
                addGenesisBlock();
            } finally {
                setGetMoreBlocks(true);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public void setGetMoreBlocks(boolean getMoreBlocks) {
        this.getMoreBlocks = getMoreBlocks;
    }

    public void shutdown() {
        ThreadPool.shutdownExecutor("networkService", networkService, 5);
    }

    /**
     * 将区块保存到数据库，并修改当前区块链最后一个区块
     * @param block
     */
    private void addBlock(BlockImpl block) {
        try (Connection con = BlockDB.getConnection()) {
            BlockDB.saveBlock(con, block);
            blockchain.setLastBlock(block);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    // 添加创世区块
    private void addGenesisBlock() {
        /*
         * 从数据库中获取区块信息，如果可以获取到，添加到区块链中
         */
        BlockImpl lastBlock = BlockDB.findLastBlock();
        if (lastBlock != null) {
            Logger.logMessage("Genesis block already in database");
            blockchain.setLastBlock(lastBlock);

            popOffTo(lastBlock);

            genesisBlockId = BlockDB.findBlockIdAtHeight(0);
            Logger.logMessage("Last block height: " + lastBlock.getHeight());
            return;
        }
        /*
         * 数据库中未查询到数据，根据json文件生成新的创世区块
         */
        Logger.logMessage("Genesis block not in database, starting from scratch");
        BlockImpl genesisBlock = new BlockImpl(Genesis.generationSignature);
        genesisBlockId = genesisBlock.getId();
        //如果是轻客户端
        if (Constants.isLightClient) {
            blockchain.setLastBlock(genesisBlock);
            return;
        }
        /*
         * 将创世区块持久化
         */
        try (Connection con = DB.db.beginTransaction()) {
            /*
             *将区块信息添加的数据库与内存
             */
            addBlock(genesisBlock);

            /*
             *加载json文件中的初始化信息
             */
            byte[] generationSignature = Genesis.apply();

            /*
             *如果加载json文件的摘要信息与初始化摘要信息不一致，设置重新扫描，并抛出异常
             */
            if (!Arrays.equals(generationSignature, genesisBlock.getGenerationSignature())) {
                scheduleScan(0, true);
                DB.db.commitTransaction();
                throw new RuntimeException("Invalid generation signature " + Arrays.toString(generationSignature));
            } else {
                DB.db.commitTransaction();
                for (DerivedDBTable table : derivedTables) {
                    table.createSearchIndex(con);
                }
            }
            DB.db.commitTransaction();
        } catch (SQLException e) {
            DB.db.rollbackTransaction();
            Logger.logMessage(e.getMessage());
            throw new RuntimeException(e.toString(), e);
        } finally {
            DB.db.endTransaction();
        }
    }

    /**
     *
     * @param block
     * @throws BlockNotAcceptedException
     */
    private void pushBlock(final BlockImpl block) throws BlockNotAcceptedException {

        int curTime = Shareschain.getEpochTime();

        blockchain.writeLock();
        try {
            BlockImpl previousLastBlock = null;
            try {
                DB.db.beginTransaction();
                previousLastBlock = blockchain.getLastBlock();

                /**
                 * 验证区块
                 */
                validate(block, previousLastBlock, curTime);

                long nextHitTime = Generator.getNextHitTime(previousLastBlock.getId(), curTime);
                if (nextHitTime > 0 && block.getTimestamp() > nextHitTime + 1) {
                    String msg = "Rejecting block " + block.getStringId() + " at height " + previousLastBlock.getHeight()
                            + " block timestamp " + block.getTimestamp() + " next hit time " + nextHitTime
                            + " current time " + curTime;
                    Logger.logDebugMessage(msg);
                    Generator.setDelay(-Constants.FORGING_SPEEDUP);
                    throw new BlockOutOfOrderException(msg, block);
                }

                Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();

                /**
                 * 验证交易是否合法
                 */
                validateTransactions(block, previousLastBlock, curTime, duplicates, previousLastBlock.getHeight() >= Constants.LAST_CHECKSUM_BLOCK);

                /**
                 * 设置新的区块的上级区块
                 */
                block.setPrevious(previousLastBlock);

                blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);

                //将按照队列排序来决定哪些交易被包含到当前生成的区块中，并撤销原来已经处理的未确认的交易，放到后面重新处理
                TransactionProcessorImpl.getInstance().requeueAllUnconfirmedTransactions();
                /**
                 * 保存区块到数据库,修改当前区块链最后一个区块信息
                 */
                addBlock(block);

                //接收本区块
                accept(block);

                DB.db.commitTransaction();
            } catch (Exception e) {
                DB.db.rollbackTransaction();
                blockchain.setLastBlock(BlockDB.findLastBlock());
//                blockchain.setLastBlock(previousLastBlock);
                throw e;
            } finally {
                DB.db.endTransaction();
            }
            blockListeners.notify(block, Event.AFTER_BLOCK_ACCEPT);
        } finally {
            blockchain.writeUnlock();
        }

        if (block.getTimestamp() >= curTime - 600) {
            NetworkHandler.broadcastMessage(new NetworkMessage.BlockInventoryMessage(block));
        }

        blockListeners.notify(block, Event.BLOCK_PUSHED);

    }

    /**
     * 验证区块信息
     * 1、父区块编号是否与本区块保存一致
     * 2、通过父区块计算的区块版本是否与本区别版本一致，目前默认都是3
     * 3、区块时间戳是否合法
     * 4、区块时间戳是否大于父区块时间戳
     * 5、父区块摘要是否与本区块保存的区块摘要一致
     * 6、数据库保存的父区块高度是否小于当前区块高度
     * @param block
     * @param previousLastBlock
     * @param curTime
     * @throws BlockNotAcceptedException
     */
    private void validate(BlockImpl block, BlockImpl previousLastBlock, int curTime) throws BlockNotAcceptedException {
        if (previousLastBlock.getId() != block.getPreviousBlockId()) {
            throw new BlockOutOfOrderException("Previous block id doesn't match", block);
        }
        if (block.getVersion() != getBlockVersion(previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Invalid version " + block.getVersion(), block);
        }
        if (block.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT) {
            Logger.logWarningMessage("Received block " + block.getStringId() + " from the future, timestamp " + block.getTimestamp()
                            + " generator " + Long.toUnsignedString(block.getGeneratorId()) + " current time " + curTime + ", system clock may be off");
            throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
                    + " current time is " + curTime, block);
        }
        if (block.getTimestamp() <= previousLastBlock.getTimestamp()) {
            throw new BlockNotAcceptedException("Block timestamp " + block.getTimestamp() + " is before previous block timestamp "
                    + previousLastBlock.getTimestamp(), block);
        }
        if (!Arrays.equals(Crypto.sha256().digest(previousLastBlock.bytes()), block.getPreviousBlockHash())) {
            throw new BlockNotAcceptedException("Previous block hash doesn't match", block);
        }
        //6、数据库保存的父区块高度是否小于当前区块高度
        if (block.getId() == 0L || BlockDB.hasBlock(block.getId(), previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Duplicate block or invalid id", block);
        }
        //验证区块生成时间是否合法,锻造者的公钥是否合法
        if (!block.verifyGenerationSignature() && !Generator.allowsFakeForging(block.getGeneratorPublicKey())) {
            Account generatorAccount = Account.getAccount(block.getGeneratorId());
            long generatorBalance = generatorAccount == null ? 0 : generatorAccount.getEffectiveBalanceSCTK();
            throw new BlockNotAcceptedException("Generation signature verification failed, effective balance " + generatorBalance, block);
        }
        //验证区块的签名
        if (!block.verifyBlockSignature()) {
            throw new BlockNotAcceptedException("Block signature verification failed", block);
        }
        //判断区块最多交易数量是否大于10
        if (block.getSmcTransactions().size() > Constants.MAX_NUMBER_OF_SMC_TRANSACTIONS) {
            throw new BlockNotAcceptedException("Invalid block transaction count " + block.getSmcTransactions().size(), block);
        }
    }

    /**
     * 验证交易信息
     * @param block
     * @param previousLastBlock
     * @param curTime
     * @param duplicates
     * @param fullValidation 完全验证交易信息
     * @throws BlockNotAcceptedException
     */
    private void validateTransactions(BlockImpl block, BlockImpl previousLastBlock, int curTime, Map<TransactionType, Map<String, Integer>> duplicates,
                                      boolean fullValidation) throws BlockNotAcceptedException {
        long calculatedTotalFee = 0;
        MessageDigest digest = Crypto.sha256();
        Set<Long> transactionIds = fullValidation ? new HashSet<>() : null;
        for (SmcTransactionImpl smcTransaction : block.getSmcTransactions()) {
            /*
             *验证交易时间戳及交易签名信息
             */
            validateTransaction(smcTransaction, block, previousLastBlock, curTime);

            if (fullValidation) {
                if (!transactionIds.add(smcTransaction.getId())) {
                    throw new TransactionNotAcceptedException("Duplicate transaction id", smcTransaction);
                }
                //验证交易是否合法
                fullyValidateTransaction(smcTransaction, block, previousLastBlock, curTime);
            }

            if (smcTransaction.attachmentIsDuplicate(duplicates, true)) {
                throw new TransactionNotAcceptedException("Transaction is a duplicate", smcTransaction);
            }
            //计算交易金额
            calculatedTotalFee += smcTransaction.getFee();
            //计算交易hash值
            digest.update(smcTransaction.bytes());
        }
        //交易的总金额，与区块中交易的总金额不一致，抛出异常
        if (calculatedTotalFee != block.getTotalFeeKER()) {
            throw new BlockNotAcceptedException("Total fee doesn't match transaction total", block);
        }
        //交易的最终hash值与区块中保存的payloadHash值不一致，抛出异常
        if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
            throw new BlockNotAcceptedException("Payload hash doesn't match", block);
        }
    }

    /**
     * 验证交易时间戳及交易签名信息
     * @param transaction
     * @param block
     * @param previousLastBlock
     * @param curTime
     * @throws BlockNotAcceptedException
     */
    private void validateTransaction(TransactionImpl transaction, BlockImpl block, BlockImpl previousLastBlock, int curTime)
            throws BlockNotAcceptedException {
        if (transaction.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT) {
            throw new BlockOutOfOrderException("Invalid transaction timestamp: " + transaction.getTimestamp()
                    + ", current time is " + curTime, block);
        }
        if (!transaction.verifySignature()) {
            throw new TransactionNotAcceptedException("Transaction signature verification failed at height " + previousLastBlock.getHeight(), transaction);
        }
    }

    /**
     * 验证交易是否合法
     * @param transaction
     * @param block
     * @param previousLastBlock
     * @param curTime
     * @throws BlockNotAcceptedException
     */
    private void fullyValidateTransaction(SmcTransactionImpl transaction, BlockImpl block, BlockImpl previousLastBlock, int curTime)
            throws BlockNotAcceptedException {
        if (transaction.getTimestamp() > block.getTimestamp() + Constants.MAX_TIMEDRIFT
                || transaction.getExpiration() < block.getTimestamp()) {
            throw new TransactionNotAcceptedException("Invalid transaction timestamp " + transaction.getTimestamp()
                    + ", current time is " + curTime + ", block timestamp is " + block.getTimestamp(), transaction);
        }
        //判断交易是否存在: 1.通过交易id判断交易是否存在 2.如果存在该交易，将通过高度判断，此交易是否已经发生
        if (TransactionHome.hasSmcTransaction(transaction.getId(), previousLastBlock.getHeight())) {
            throw new TransactionNotAcceptedException("Transaction is already in the blockchain", transaction);
        }
        //交易的版本号是否与上一区块的版本号一致，该版本号设置为1
        if (transaction.getVersion() != getTransactionVersion(previousLastBlock.getHeight())) {
            throw new TransactionNotAcceptedException("Invalid transaction version " + transaction.getVersion()
                    + " at height " + previousLastBlock.getHeight(), transaction);
        }
        try {
            transaction.validateId();
            transaction.validate(); // recursively validates child transactions for Smc transactions
        } catch (ShareschainException.ValidationException e) {
            throw new TransactionNotAcceptedException(e, transaction);
        }
    }


    private void accept(BlockImpl block) throws TransactionNotAcceptedException {
        try {
            isProcessingBlock = true;
            for (SmcTransactionImpl transaction : block.getSmcTransactions()) {
                //更新发送者的未确认余额信息
                if (! transaction.applyUnconfirmed()) {
                    throw new TransactionNotAcceptedException("Double spending", transaction);
                }
            }
            blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);

            /**
             * 更新锻造账户相关费用
             * 1.将锻造的小费增加到锻造当前区块的账户的余额和未确认余额中
             * 2.修改锻造当前区块的账户表（PUBLIC.ACCOUNT）的锻造余额信息
             */
            block.apply();

            /**
             * 将交易保存到prunableTransactions 中，以备恢复
             */
            for (SmcTransactionImpl transaction : block.getSmcTransactions()) {
                try {
                    /**
                     * 更新交易相关余额信息
                     * 1.更新发送者账号余额表
                     * 2.更新接收人账户余额与未确认余额
                     * 3.更新接收人的保证余额
                     */
                    transaction.apply();
                } catch (RuntimeException e) {
                    Logger.logErrorMessage(e.toString(), e);
                    throw new BlockchainProcessor.TransactionNotAcceptedException(e, transaction);
                }
            }

            blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
            if (block.getSmcTransactions().size() > 0) {
                List<Transaction> confirmedTransactions = new ArrayList<>();
                block.getSmcTransactions().forEach(smcTransaction -> {
                    confirmedTransactions.add(smcTransaction);
                });
                TransactionProcessorImpl.getInstance().notifyListeners(confirmedTransactions, TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
            }
            AccountLedger.commitEntries();
        } finally {
            isProcessingBlock = false;
            AccountLedger.clearEntries();
        }
    }


    /**
     * 验证区块高度是否小于区块链当前高度减去720
     * 如果小于，重新扫描整个区块链
     * 如何不小于，加载区块的交易信息，并且校验区块的最后一个区块
     * @param commonBlock
     * @return List<BlockImpl> 比参数区块高的区块集合，带有交易信息
     */
    List<BlockImpl> popOffTo(Block commonBlock) {
        blockchain.writeLock();
        try {
            if (!DB.db.isInTransaction()) {
                try {
                    DB.db.beginTransaction();
                    return popOffTo(commonBlock);
                } finally {
                    DB.db.endTransaction();
                }
            }
            /*
             * 如果当前区块链中没有当前区块，返回空
             */
            if (! blockchain.hasBlock(commonBlock.getId())) {
                Logger.logDebugMessage("Block " + commonBlock.getStringId() + " not found in blockchain, nothing to pop off");
                return Collections.emptyList();
            }
            try {
                /*
                 * 如果当前区块高度，小于最小回滚高度,设置节点重新扫描，并且回滚数据库中区块
                 */
                if (commonBlock.getHeight() < getMinRollbackHeight()) {
                    Logger.logMessage("Rollback to height " + commonBlock.getHeight() + " not supported, will do a full rescan");
                    try {
                        //设置当前节点从创世节点重新扫描，并且不做交易验证
                        scheduleScan(0, false);
                        BlockImpl lastBlock = BlockDB.deleteBlocksFrom(BlockDB.findBlockIdAtHeight(commonBlock.getHeight() + 1));
                        blockchain.setLastBlock(lastBlock);
                        for (DerivedDBTable table : derivedTables) {
                            table.popOffTo(lastBlock.getHeight());
                        }
                        DB.db.clearCache();
                        DB.db.commitTransaction();
                        Logger.logDebugMessage("Deleted blocks starting from height %s", commonBlock.getHeight() + 1);
                    } finally {
                        scan(0, false);
                    }
                    return Collections.emptyList();
                } else {
                    List<BlockImpl> poppedOffBlocks = new ArrayList<>();
                    BlockImpl block = blockchain.getLastBlock();
                    block.loadTransactions();
                    Logger.logDebugMessage("Rollback from block " + block.getStringId() + " at height " + block.getHeight()
                            + " to " + commonBlock.getStringId() + " at " + commonBlock.getHeight());
                    while (block.getId() != commonBlock.getId() && block.getHeight() > 0) {
                        poppedOffBlocks.add(block);
                        /*
                         *删除缓存与数据库中区块链的当前节点
                         */
                        block = popLastBlock();
                    }
                    for (DerivedDBTable table : derivedTables) {
                        table.popOffTo(commonBlock.getHeight());
                    }
                    DB.db.clearCache();
                    DB.db.commitTransaction();
                    return poppedOffBlocks;
                }
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Error popping off to " + commonBlock.getHeight() + ", " + e.toString());
                DB.db.rollbackTransaction();
                BlockImpl lastBlock = BlockDB.findLastBlock();
                blockchain.setLastBlock(lastBlock);
                popOffTo(lastBlock);
                throw e;
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    private BlockImpl popLastBlock() {
        BlockImpl block = blockchain.getLastBlock();
        if (block.getHeight() == 0) {
            throw new RuntimeException("Cannot pop off genesis block");
        }
        BlockImpl previousBlock = BlockDB.deleteBlocksFrom(block.getId());
        previousBlock.loadTransactions();
        blockchain.setLastBlock(previousBlock);
        blockListeners.notify(block, Event.BLOCK_POPPED);
        return previousBlock;
    }

    private int getBlockVersion(int previousBlockHeight) {
        return 3;
    }

    private int getTransactionVersion(int previousBlockHeight) {
        return 1;
    }

    /**
     *获取可当前链上可打包的交易，每次打包只打包10条交易
     * 1.过滤掉引用类型的交易
     * 2.将交易按照时间戳、高度、id进行排序
     * @param duplicates
     * @param previousBlock
     * @param blockTimestamp
     * @return
     */
    SortedSet<UnconfirmedSmcTransaction> selectUnconfirmedSmcTransactions(Map<TransactionType, Map<String, Integer>> duplicates, Block previousBlock, int blockTimestamp) {
        //构建一个进过过滤的未确认的交易集合
        List<UnconfirmedSmcTransaction> orderedUnconfirmedTransactions = new ArrayList<>();
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                // 左边是一个迭代器
                TransactionProcessorImpl.getInstance().getUnconfirmedSmcTransactions(),

                // 右边是一个过滤器,过滤掉引用hash的交易类型(这个是在页面交易的时候选择参考的交易hash值)
                transaction -> transaction.getTransaction().hasAllReferencedTransactions(transaction.getTimestamp(), 0))) {

            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                orderedUnconfirmedTransactions.add((UnconfirmedSmcTransaction)unconfirmedTransaction);
            }
        }

        // 将交易按照时间戳、高度、id排序
        SortedSet<UnconfirmedSmcTransaction> sortedTransactions = new TreeSet<>(transactionArrivalComparator);

        // 只将通过校验规则的未确认交易放入集合中。
        outer:
        for (UnconfirmedSmcTransaction unconfirmedTransaction : orderedUnconfirmedTransactions) {
            //判断sortedSet 集合中是否已存在该笔交易
            if (sortedTransactions.contains(unconfirmedTransaction)) {
                continue;
            }
            //判断区块版本与交易版本是否一致，目前默认写死为1
            if (unconfirmedTransaction.getVersion() != getTransactionVersion(previousBlock.getHeight())) {
                continue;
            }
            //判断交易的时间戳不能大于区块的时间戳.并且交易的截止时间不能小于区块的时间戳
            if (blockTimestamp > 0 && (unconfirmedTransaction.getTimestamp() > blockTimestamp + Constants.MAX_TIMEDRIFT
                    || unconfirmedTransaction.getExpiration() < blockTimestamp)) {
                continue;
            }
            //验证交易是否合法
            try {
                unconfirmedTransaction.getTransaction().validate();
            } catch (ShareschainException.ValidationException e) {
                continue;
            }
            //
            if (unconfirmedTransaction.getTransaction().attachmentIsDuplicate(duplicates, true)) {
                continue;
            }
            sortedTransactions.add(unconfirmedTransaction);
            //每个区块值处理10条交易
            if (sortedTransactions.size() == Constants.MAX_NUMBER_OF_SMC_TRANSACTIONS) {
                break;
            }
        }
        return sortedTransactions;
    }


    private static final Comparator<UnconfirmedTransaction> transactionArrivalComparator = Comparator
            .comparingLong(UnconfirmedTransaction::getArrivalTimestamp)
            .thenComparingInt(UnconfirmedTransaction::getHeight)
            .thenComparingLong(UnconfirmedTransaction::getId);

    /**
     * 生成区块 只有generator 中调用
     * @param secretPhrase 密钥
     * @param blockTimestamp
     * @throws BlockNotAcceptedException
     */
    public void generateBlock(String secretPhrase, int blockTimestamp) throws BlockNotAcceptedException {

        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        /**
         * 获取当前区块链最后一个区块信息，作为当前区块的上级区块处理
         */
        BlockImpl previousBlock = blockchain.getLastBlock();
        /**
         * 处理所有等待池中的交易信息
         */
        TransactionProcessorImpl.getInstance().processWaitingTransactions();

        //获取主链上可打包的交易 每次打包只打包10条交易
        //   1.过滤掉引用类型的交易
        //   2.将交易按照时间戳、高度、id进行排序
        SortedSet<UnconfirmedSmcTransaction> sortedTransactions = selectUnconfirmedSmcTransactions(duplicates, previousBlock, blockTimestamp);

        List<SmcTransactionImpl> blockTransactions = new ArrayList<>();
        MessageDigest digest = Crypto.sha256();
        long totalFeeKER = 0;
        for (UnconfirmedSmcTransaction unconfirmedTransaction : sortedTransactions) {
            SmcTransactionImpl transaction = unconfirmedTransaction.getTransaction();
            blockTransactions.add(transaction);
            digest.update(transaction.bytes());
            totalFeeKER += transaction.getFee();
        }
        /**
         * 所有未确认的主链交易数据生成一个消息摘要，并用作payloadHash。
         */
        byte[] payloadHash = digest.digest();
        /**
         * 添加上一个区块的签名
         */
        digest.update(previousBlock.getGenerationSignature());

        /**
         * 根据密钥获取公钥信息
         */
        final byte[] publicKey = Crypto.getPublicKey(secretPhrase);
        /**
         * 生成新的区块的签名。
         */
        byte[] generationSignature = digest.digest(publicKey);
        /**
         * 获取上一个区块的hash值
         */
        byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.bytes());

        /**
         * 生成区块信息
         */
        BlockImpl block = new BlockImpl(getBlockVersion(previousBlock.getHeight()), blockTimestamp, previousBlock.getId(), totalFeeKER,
                payloadHash, publicKey, generationSignature, previousBlockHash, blockTransactions, secretPhrase);

        try {

            pushBlock(block);

            blockListeners.notify(block, Event.BLOCK_GENERATED);

            Logger.logDebugMessage(String.format("Account %s generated block %s at height %d timestamp %d fee %f %s",
                    Long.toUnsignedString(block.getGeneratorId()), block.getStringId(), block.getHeight(), block.getTimestamp(),
                    ((float)block.getTotalFeeKER())/Constants.KER_PER_SCTK, Mainchain.MAINCHAIN_NAME));
        } catch (TransactionNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            TransactionProcessorImpl.getInstance().processWaitingTransactions();
            TransactionImpl transaction = e.getTransaction();
            Logger.logDebugMessage("Removing invalid transaction: " + transaction.getStringId());
            blockchain.writeLock();
            try {
                TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
            } finally {
                blockchain.writeUnlock();
            }
            throw e;
        } catch (BlockNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 设置扫描表，从某高度重新扫描
     * @param height 区块高度
     * @param validate 是否验证交易信息
     */
    public void scheduleScan(int height, boolean validate) {
        try (Connection con = DB.getConnection();
             PreparedStatement pstmt = con.prepareStatement("UPDATE scan SET rescan = TRUE, height = ?, validate = ?")) {
            pstmt.setInt(1, height);
            pstmt.setBoolean(2, validate);
            pstmt.executeUpdate();
            Logger.logDebugMessage("Scheduled scan starting from height " + height + (validate ? ", with validation" : ""));
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void scan(int height, boolean validate) {
        scan(height, validate, false);
    }

    @Override
    public void fullScanWithShutdown() {
        scan(0, true, true);
    }

    /**
     * 重新扫描，如果某一个区块验证失败，回滚该区块之后的所有区块信息
     * 如果该区块小于当前区块链-720 ，将整个区块链重置，
     * @param height
     * @param validate
     * @param shutdown
     */
    private void scan(int height, boolean validate, boolean shutdown) {
        blockchain.writeLock();
        try {
            if (!DB.db.isInTransaction()) {
                try {
                    DB.db.beginTransaction();
                    if (validate) {
                        blockListeners.addListener(checksumListener, Event.BLOCK_SCANNED);
                    }
                    scan(height, validate, shutdown);
                    DB.db.commitTransaction();
                } catch (Exception e) {
                    DB.db.rollbackTransaction();
                    throw e;
                } finally {
                    DB.db.endTransaction();
                    blockListeners.removeListener(checksumListener, Event.BLOCK_SCANNED);
                }
                return;
            }

            scheduleScan(height, validate);

            if (height > 0 && height < getMinRollbackHeight()) {
                Logger.logMessage("Rollback to height less than " + getMinRollbackHeight() + " not supported, will do a full scan");
                height = 0;
            }
            if (height < 0) {
                height = 0;
            }
            Logger.logMessage("Scanning blockchain starting from height " + height + "...");
            if (validate) {
                Logger.logDebugMessage("Also verifying signatures and validating transactions...");
            }
            try (Connection con = DB.getConnection();
                 PreparedStatement pstmtSelect = con.prepareStatement("SELECT * FROM block WHERE " + (height > 0 ? "height >= ? AND " : "")
                         + " db_id >= ? ORDER BY db_id ASC LIMIT 50000");
                 PreparedStatement pstmtDone = con.prepareStatement("UPDATE scan SET rescan = FALSE, height = 0, validate = FALSE")) {
                isScanning = true;
                initialScanHeight = blockchain.getHeight();
                //如果回滚高度，大于当前区块链节点高度，无需回滚，直接返回
                if (height > blockchain.getHeight() + 1) {
                    Logger.logMessage("Rollback height " + (height - 1) + " exceeds current blockchain height of " + blockchain.getHeight() + ", no scan needed");
                    pstmtDone.executeUpdate();
                    DB.db.commitTransaction();
                    return;
                }
                //如果区块高度为0 ，删除数据库中表
                if (height == 0) {
                    Logger.logDebugMessage("Dropping all full text search indexes");
                    FullTextTrigger.dropAll(con);
                }
                for (DerivedDBTable table : derivedTables) {
                    if (height == 0) {//如果区块高度为0 ，重新创建表
                        table.truncate();
                    } else {//回滚数据库中的数据
                        table.rollback(height - 1);
                    }
                }
                DB.db.clearCache();
                DB.db.commitTransaction();
                Logger.logDebugMessage("Rolled back derived tables");

                BlockImpl currentBlock = BlockDB.findBlockAtHeight(height);
                blockListeners.notify(currentBlock, Event.RESCAN_BEGIN);
                long currentBlockId = currentBlock.getId();
                if (height == 0) {//高度为0 ，从json文件中加载创世快
                    blockchain.setLastBlock(currentBlock); // special case to avoid no last block
                    byte[] generationSignature = Genesis.apply();
                    if (!Arrays.equals(generationSignature, currentBlock.getGenerationSignature())) {
                        throw new RuntimeException("Invalid generation signature " /*+ Arrays.toString(generationSignature)*/);
                    }
                } else {//设置区块链中的最后一个区块
                    blockchain.setLastBlock(BlockDB.findBlockAtHeight(height - 1));
                }
                /**
                 * ?
                 */
                if (shutdown) {
                    Logger.logMessage("Scan will be performed at next start");
                    new Thread(() -> System.exit(0)).start();
                    return;
                }

                int pstmtSelectIndex = 1;
                if (height > 0) {
                    pstmtSelect.setInt(pstmtSelectIndex++, height);
                }
                long dbId = Long.MIN_VALUE;
                boolean hasMore = true;
                outer:
                while (hasMore) {
                    hasMore = false;
                    pstmtSelect.setLong(pstmtSelectIndex, dbId);
                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        while (rs.next()) {
                            try {
                                dbId = rs.getLong("db_id");
                                //从数据库中取出一个区块,第三个参数表示是否加载交易信息
                                currentBlock = BlockDB.loadBlock(con, rs, true);
                                if (currentBlock.getHeight() > 0) {
                                    //加载交易信息
                                    currentBlock.loadTransactions();

                                    if (currentBlock.getId() != currentBlockId || currentBlock.getHeight() > blockchain.getHeight() + 1) {
                                        throw new ShareschainException.NotValidException("Database blocks in the wrong order!");
                                    }

                                    int curTime = Shareschain.getEpochTime();
                                    Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();

                                    /*
                                     *验证区块中的交易信息
                                     */
                                    validateTransactions(currentBlock, blockchain.getLastBlock(), curTime, duplicates, validate);

                                    if (validate) {//判断是否验证区块
                                        //验证区块
                                        validate(currentBlock, blockchain.getLastBlock(), curTime);

                                        byte[] blockBytes = currentBlock.bytes();
                                        //块字节是否能解析回同一区块
                                        if (!Arrays.equals(blockBytes, BlockImpl.parseBlock(blockBytes, currentBlock.getSmcTransactions()).bytes())) {
                                            throw new ShareschainException.NotValidException("Block bytes cannot be parsed back to the same block");
                                        }
                                        //获取区块中所有的交易信息，包括子链交易
                                        List<TransactionImpl> transactions = new ArrayList<>();
                                        for (SmcTransactionImpl smcTransaction : currentBlock.getSmcTransactions()) {
                                            transactions.add(smcTransaction);
                                        }
                                        /*
                                         *判断交易的字节信息是否可以解析回同一交易
                                         */
                                        for (TransactionImpl transaction : transactions) {
                                            byte[] transactionBytes = transaction.bytes();
                                            //交易字节是否能够解析回同一交易
                                            if (!Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionBytes).build().bytes())) {
                                                throw new ShareschainException.NotValidException("Transaction bytes cannot be parsed back to the same transaction: "
                                                        + JSON.toJSONString(transaction.getJSONObject()));
                                            }
                                            //交易的json不能解析回同一交易
                                            JSONObject transactionJSON = (JSONObject) JSONValue.parse(JSON.toJSONString(transaction.getJSONObject()));
                                            if (!Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionJSON).build().bytes())) {
                                                throw new ShareschainException.NotValidException("Transaction JSON cannot be parsed back to the same transaction: "
                                                        + JSON.toJSONString(transaction.getJSONObject()));
                                            }
                                        }
                                    }
                                    blockListeners.notify(currentBlock, Event.BEFORE_BLOCK_ACCEPT);
                                    blockchain.setLastBlock(currentBlock);
                                    //接收区块
                                    accept(currentBlock);

                                    DB.db.clearCache();
                                    DB.db.commitTransaction();
                                    blockListeners.notify(currentBlock, Event.AFTER_BLOCK_ACCEPT);
                                }
                                blockListeners.notify(currentBlock, Event.BLOCK_SCANNED);
                                hasMore = true;
                                currentBlockId = currentBlock.getNextBlockId();
                            } catch (ShareschainException | RuntimeException e) {//如果以上抛出异常，对当前节点之后的节点信息进行回滚
                                DB.db.rollbackTransaction();
                                Logger.logDebugMessage(e.toString(), e);
                                Logger.logDebugMessage("Applying block " + Long.toUnsignedString(currentBlockId) + " at height "
                                        + currentBlock.getHeight() + " failed, deleting from database");
                                BlockImpl lastBlock = BlockDB.deleteBlocksFrom(currentBlockId);
                                blockchain.setLastBlock(lastBlock);
                                popOffTo(lastBlock);
                                break outer;
                            }
                        }
                        dbId = dbId + 1;
                    }
                }
                if (height == 0) {
                    for (DerivedDBTable table : derivedTables) {
                        table.createSearchIndex(con);
                    }
                }
                pstmtDone.executeUpdate();
                DB.db.commitTransaction();
                blockListeners.notify(currentBlock, Event.RESCAN_END);
                Logger.logMessage("...done at height " + blockchain.getHeight());
                if (height == 0 && validate) {
                    Logger.logMessage("SUCCESSFULLY PERFORMED FULL RESCAN WITH VALIDATION");
                }
                lastRestoreTime = 0;
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } finally {
                isScanning = false;
            }
        } finally {
            blockchain.writeUnlock();
        }
    }
}

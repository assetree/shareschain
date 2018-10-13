package shareschain.node;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainException;
import shareschain.blockchain.Block;
import shareschain.blockchain.BlockchainProcessor;
import shareschain.blockchain.ChainTransactionId;
import shareschain.blockchain.Transaction;
import shareschain.util.Logger;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class BlockInventory {

    /** Block cache */
    private static final ConcurrentHashMap<Long, Block> blockCache = new ConcurrentHashMap<>();

    /** Pending blocks */
    private static final Set<Long> pendingBlocks = Collections.synchronizedSet(new HashSet<>());

    private BlockInventory() {}

    /**
     * Process a BlockInventory message (there is no response message)
     *
     * @param   node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.BlockInventoryMessage request) {
        final long invBlockId = request.getBlockId();
        long invPreviousBlockId = request.getPreviousBlockId();
        int invTimestamp = request.getTimestamp();
        //
        // Ignore the block if we already have it or are in the process of getting it
        //
        if (blockCache.get(invBlockId) != null || pendingBlocks.contains(invBlockId)) {
            return null;
        }
        //
        // Accept the block if it is a continuation of the current chain or represents
        // a fork of 1 or 2 blocks.  Forks longer than 2 blocks will be handled by the
        // blockchain download processor.
        //
        Block invLastBlock = Shareschain.getBlockchain().getLastBlock();
        Block invTipBlock = blockCache.get(invPreviousBlockId);
        if (invPreviousBlockId == invLastBlock.getId() ||
                (invPreviousBlockId == invLastBlock.getPreviousBlockId() &&
                        invTimestamp < invLastBlock.getTimestamp()) ||
                (invTipBlock != null && invTipBlock.getPreviousBlockId() == invLastBlock.getPreviousBlockId())) {
            if (!Shareschain.getBlockchainProcessor().isDownloadSuspended()) {
                Logger.logDebugMessage("Suspending blockchain download - blockchain synchronized");
                Shareschain.getBlockchainProcessor().suspendDownload(true);
            }
            pendingBlocks.add(invBlockId);
            Nodes.nodesService.execute(() -> {
                Node feederNode = null;
                try {
                    //
                    // Build the GetBlock request.  We will exclude transactions that are
                    // in the TransactionsInventory transaction cache.
                    //
                    List<ChainTransactionId> invTransactionIds = request.getTransactionIds();
                    BitSet excludedTransactionIds = new BitSet();
                    List<Transaction> cachedTransactions = new ArrayList<>(invTransactionIds.size());
                    for (int i = 0; i < invTransactionIds.size(); i++) {
                        Transaction tx = TransactionsInventory.getCachedTransaction(invTransactionIds.get(i));
                        if (tx != null) {
                            cachedTransactions.add(tx);
                            excludedTransactionIds.set(i);
                        }
                    }
                    if (Nodes.isLogLevelEnabled(Nodes.LOG_LEVEL_DETAILS)) {
                        Logger.logDebugMessage("Requesting block " + Long.toUnsignedString(invBlockId));
                    }
                    NetworkMessage.GetBlockMessage blockRequest =
                            new NetworkMessage.GetBlockMessage(invBlockId, excludedTransactionIds);
                    //
                    // Request the block, starting with the node that sent the BlocksInventory message
                    //
                    List<Node> connectedNodes = Nodes.getConnectedNodes();
                    if (connectedNodes.isEmpty()) {
                        return;
                    }
                    int index = connectedNodes.indexOf(node);
                    if (index < 0) {
                        index = 0;
                    }
                    int startIndex = index;
                    NetworkMessage.BlocksMessage response;
                    while (true) {
                        feederNode = connectedNodes.get(index);
                        response = (NetworkMessage.BlocksMessage)feederNode.sendRequest(blockRequest);
                        if (blockCache.get(invBlockId) != null) {
                            return;
                        }
                        if (response == null || response.getBlockCount() == 0) {
                            index = (index < connectedNodes.size() - 1 ? index + 1 : 0);
                            if (index == startIndex) {
                                return;
                            }
                            continue;
                        }
                        break;
                    }
                    //
                    // Process the block
                    //
                    Block block = response.getBlock(cachedTransactions);
                    if (Nodes.isLogLevelEnabled(Nodes.LOG_LEVEL_DETAILS)) {
                        Logger.logDebugMessage("Received block " + block.getStringId());
                    }
                    long previousBlockId = block.getPreviousBlockId();
                    Block lastBlock = Shareschain.getBlockchain().getLastBlock();
                    try {
                        if (previousBlockId == lastBlock.getId() ||
                                (previousBlockId == lastBlock.getPreviousBlockId() &&
                                        block.getTimestamp() < lastBlock.getTimestamp())) {
                            Shareschain.getBlockchainProcessor().processNodeBlock(block);
                        } else {
                            Block tipBlock = blockCache.get(previousBlockId);
                            if (tipBlock != null && tipBlock.getPreviousBlockId() == lastBlock.getPreviousBlockId()) {
                                List<Block> blockList = new ArrayList<>(2);
                                blockList.add(tipBlock);
                                blockList.add(block);
                                Shareschain.getBlockchainProcessor().processNodeBlocks(blockList);
                            }
                        }
                    } catch (BlockchainProcessor.BlockOutOfOrderException | BlockchainProcessor.BlockOfLowerDifficultyException ignore) {}
                    if (block.getTimestamp() < Shareschain.getEpochTime() + Constants.MAX_TIMEDRIFT) {
                        blockCache.put(block.getId(), block);
                    }
                    int now = Shareschain.getEpochTime();
                    blockCache.values().removeIf(cacheBlock -> cacheBlock.getTimestamp() < now - 10 * 60);
                } catch (ShareschainException | RuntimeException e) {
                    if (feederNode != null) {
                        feederNode.blacklist(e);
                    }
                } finally {
                    pendingBlocks.remove(invBlockId);
                }
            });
        } else if (invBlockId == invLastBlock.getId()) {
            if (!Shareschain.getBlockchainProcessor().isDownloadSuspended()) {
                Logger.logDebugMessage("Suspending blockchain download - blockchain synchronized");
                Shareschain.getBlockchainProcessor().suspendDownload(true);
            }
        } else if (!Shareschain.getBlockchain().hasBlock(invBlockId)) {
            if (Shareschain.getBlockchainProcessor().isDownloadSuspended()) {
                Logger.logDebugMessage("Resuming blockchain download - fork resolution required");
                Shareschain.getBlockchainProcessor().suspendDownload(false);
            }
        }
        return null;
    }
}

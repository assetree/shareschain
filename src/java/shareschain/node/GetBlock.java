package shareschain.node;

import shareschain.Shareschain;
import shareschain.blockchain.Block;
import shareschain.blockchain.Blockchain;

final class GetBlock {

    private GetBlock() { }

    /**
     * Process the GetBlock message and returnnode the BlocksMessage
     *
     * @param   node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.GetBlockMessage request) {
        long blockId = request.getBlockId();
        byte[] excludedTransactions = request.getExcludedTransactions();
        NetworkMessage message;
        Blockchain blockchain = Shareschain.getBlockchain();
        blockchain.readLock();
        try {
            Block block = blockchain.getBlock(blockId, true);
            message = new NetworkMessage.BlocksMessage(request.getMessageId(), block, excludedTransactions);
        } finally {
            blockchain.readUnlock();
        }
        return message;
    }
}

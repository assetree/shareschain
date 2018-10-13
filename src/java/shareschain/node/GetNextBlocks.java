
package shareschain.node;

import shareschain.Shareschain;
import shareschain.blockchain.Block;

import java.util.List;

final class GetNextBlocks {

    private GetNextBlocks() {}

    /**
     * Process the GetNextBlocks message and return the Blocks message
     *
     * @param   Node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl Node, NetworkMessage.GetNextBlocksMessage request) {
        long blockId = request.getBlockId();
        List<Long> blockIds = request.getBlockIds();
        int limit = (request.getLimit() != 0 ? request.getLimit() : 36);
        List<? extends Block> blocks;
        if (!blockIds.isEmpty()) {
            if (blockIds.size() > 36) {
                throw new IllegalArgumentException(Errors.TOO_MANY_BLOCKS_REQUESTED);
            }
            blocks = Shareschain.getBlockchain().getBlocksAfter(blockId, blockIds);
        } else {
            if (limit > 36) {
                throw new IllegalArgumentException(Errors.TOO_MANY_BLOCKS_REQUESTED);
            }
            blocks = Shareschain.getBlockchain().getBlocksAfter(blockId, limit);
        }
        return new NetworkMessage.BlocksMessage(request.getMessageId(), blocks);
    }
}

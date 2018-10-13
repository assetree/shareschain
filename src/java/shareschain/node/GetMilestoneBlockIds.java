
package shareschain.node;

import shareschain.Shareschain;
import shareschain.blockchain.Block;

import java.util.ArrayList;
import java.util.List;

final class GetMilestoneBlockIds {

    private GetMilestoneBlockIds() {}

    /**
     * Process the GetMilestoneBlockIds message and return the MilestoneBlockIds message
     *
     * @param   node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.GetMilestoneBlockIdsMessage request) {
        long lastBlockId = request.getLastBlockId();
        long lastMilestoneBlockId = request.getLastMilestoneBlockIdentifier();
        List<Long> milestoneBlockIds = new ArrayList<>();
        if (lastBlockId != 0) {
            long myLastBlockId = Shareschain.getBlockchain().getLastBlock().getId();
            if (myLastBlockId == lastBlockId || Shareschain.getBlockchain().hasBlock(lastBlockId)) {
                milestoneBlockIds.add(lastBlockId);
                return new NetworkMessage.MilestoneBlockIdsMessage(request.getMessageId(),
                        myLastBlockId == lastBlockId, milestoneBlockIds);
            }
        }
        long blockId;
        int height;
        int jump;
        int limit = 10;
        int blockchainHeight = Shareschain.getBlockchain().getHeight();
        if (lastMilestoneBlockId != 0) {
            Block lastMilestoneBlock = Shareschain.getBlockchain().getBlock(lastMilestoneBlockId);
            if (lastMilestoneBlock == null) {
                throw new IllegalStateException("Don't have block " + Long.toUnsignedString(lastMilestoneBlockId));
            }
            height = lastMilestoneBlock.getHeight();
            jump = Math.min(1440, Math.max(blockchainHeight - height, 1));
            height = Math.max(height - jump, 0);
        } else if (lastBlockId != 0) {
            height = blockchainHeight;
            jump = 10;
        } else {
            node.blacklist("Old getMilestoneBlockIds request");
            throw new IllegalArgumentException("Old getMilestoneBlockIds protocol not supported");
        }
        blockId = Shareschain.getBlockchain().getBlockIdAtHeight(height);
        while (height > 0 && limit-- > 0) {
            milestoneBlockIds.add(blockId);
            blockId = Shareschain.getBlockchain().getBlockIdAtHeight(height);
            height -= jump;
        }
        return new NetworkMessage.MilestoneBlockIdsMessage(request.getMessageId(), false, milestoneBlockIds);
    }
}

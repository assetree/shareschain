
package shareschain.node;

import shareschain.Shareschain;
import shareschain.blockchain.Block;

final class GetCumulativeDifficulty {

    private GetCumulativeDifficulty() {}

    /**
     * Process a GetCumulativeDifficulty message and return a CumulativeDifficulty message
     *
     * @param   node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.GetCumulativeDifficultyMessage request) {
        Block lastBlock = Shareschain.getBlockchain().getLastBlock();
        return new NetworkMessage.CumulativeDifficultyMessage(request.getMessageId(),
                lastBlock.getCumulativeDifficulty(), lastBlock.getHeight());
    }
}


package shareschain.node;

import shareschain.Shareschain;

import java.util.List;

final class GetNextBlockIds {

    private GetNextBlockIds() {}

    /**
     * Process the GetNextBlockIds message and return the BlockIds message
     *
     * @param   Node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl Node, NetworkMessage.GetNextBlockIdsMessage request) {
        long blockId = request.getBlockId();
        int limit = request.getLimit();
        if (limit > 1440) {
            throw new IllegalArgumentException(Errors.TOO_MANY_BLOCKS_REQUESTED);
        }
        /**
         * 从本地数据库中按顺序获取区块ID列表，blockId:从那个ID开始，limit：一次获取个数
         */
        List<Long> ids = Shareschain.getBlockchain().getBlockIdsAfter(blockId, limit > 0 ? limit : 1440);
        return new NetworkMessage.BlockIdsMessage(request.getMessageId(), ids);
    }
}

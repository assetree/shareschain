package shareschain.node;

final class BlockchainState {

    private BlockchainState() {}

    /**
     * Process a BlockchainState message (there is no response message)
     *
     * @param   node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.BlockchainStateMessage request) {
        node.setBlockchainState(request.getBlockchainState());
        return null;
    }
}

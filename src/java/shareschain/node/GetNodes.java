
package shareschain.node;

import java.util.List;

final class GetNodes {

    private GetNodes() {}

    /**
     * Process the GetNodes message and return the AddNodes message
     *
     * @param   node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.GetNodesMessage request) {
        List<Node> nodeList = Nodes.getNodes(p -> !p.isBlacklisted()
                        && p.getState() == Node.State.CONNECTED
                        && p.getAnnouncedAddress() != null
                        && p.shareAddress()
                        && !p.getAnnouncedAddress().equals(node.getAnnouncedAddress()),
                    NetworkMessage.MAX_LIST_SIZE);
        if (!nodeList.isEmpty()) {
            node.sendMessage(new NetworkMessage.AddNodesMessage(nodeList));
        }
        return null;
    }
}

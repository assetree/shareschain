
package shareschain.node;

import java.util.List;

final class AddNodes {

    private AddNodes() {}

    /**
     * Process an AddNodes message (there is no response message)
     *
     * @param   node                    Node
     * @param   msg                     Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl node, NetworkMessage.AddNodesMessage msg) {
        List<String> addresses = msg.getAnnouncedAddresses();
        List<Long> services = msg.getServices();
        if (!addresses.isEmpty() && Nodes.shouldGetMoreNodes() && !Nodes.hasTooManyKnownNodes()) {
            Nodes.nodesService.execute(() -> {
                for (int i=0; i<addresses.size(); i++) {
                    NodeImpl newNode = (NodeImpl)Nodes.findOrCreateNode(addresses.get(i), true);
                    if (newNode != null) {
                        newNode.setShareAddress(true);
                        if (Nodes.addNode(newNode)) {
                            newNode.setServices(services.get(i));
                        }
                    }
                    if (Nodes.hasTooManyKnownNodes()) {
                        break;
                    }
                }
            });
        }
        return null;
    }
}

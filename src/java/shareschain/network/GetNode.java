
package shareschain.network;

import shareschain.node.Node;
import shareschain.node.Nodes;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static shareschain.network.JSONResponses.MISSING_NODE;
import static shareschain.network.JSONResponses.UNKNOWN_NODE;

public final class GetNode extends APIServlet.APIRequestHandler {

    static final GetNode instance = new GetNode();

    private GetNode() {
        super(new APITag[] {APITag.NETWORK}, "node");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        String nodeAddress = req.getParameter("node");
        if (nodeAddress == null) {
            return MISSING_NODE;
        }

        Node node = Nodes.findOrCreateNode(nodeAddress, false);
        if (node == null) {
            return UNKNOWN_NODE;
        }

        return JSONData.node(node);

    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}

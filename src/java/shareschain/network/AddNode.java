
package shareschain.network;

import shareschain.network.APIServlet.APIRequestHandler;
import shareschain.node.Node;
import shareschain.node.Nodes;
import shareschain.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static shareschain.network.JSONResponses.MISSING_NODE;

public class AddNode extends APIRequestHandler {

    static final AddNode instance = new AddNode();

    private AddNode() {
        super(new APITag[] {APITag.NETWORK}, "node");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) {
        String nodeAddress = Convert.emptyToNull(request.getParameter("node"));
        if (nodeAddress == null) {
            return MISSING_NODE;
        }
        JSONObject response = new JSONObject();
        Node node = Nodes.findOrCreateNode(nodeAddress, true);
        if (node != null) {
            boolean isNewlyAdded = Nodes.addNode(node);
            if (node.getState() != Node.State.CONNECTED &&  node.getAnnouncedAddress() != null) {
                node.connectNode();
            }
            response = JSONData.node(node);
            response.put("isNewlyAdded", isNewlyAdded);
        } else {
            response.put("errorCode", 8);
            response.put("errorDescription", "Failed to add node");
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}


package shareschain.network;

import shareschain.node.Node;
import shareschain.node.Nodes;
import shareschain.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

public final class GetNodes extends APIServlet.APIRequestHandler {

    static final GetNodes instance = new GetNodes();

    private GetNodes() {
        super(new APITag[] {APITag.NETWORK}, "active", "state", "service", "service", "service", "includeNodeInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        boolean active = "true".equalsIgnoreCase(req.getParameter("active"));
        String stateValue = Convert.emptyToNull(req.getParameter("state"));
        String[] serviceValues = req.getParameterValues("service");
        boolean includeNodeInfo = "true".equalsIgnoreCase(req.getParameter("includeNodeInfo"));
        final Node.State state;
        if (stateValue != null) {
            try {
                state = Node.State.valueOf(stateValue);
            } catch (RuntimeException exc) {
                return JSONResponses.incorrect("state", "- '" + stateValue + "' is not defined");
            }
        } else {
            state = null;
        }
        long serviceCodes = 0;
        if (serviceValues != null) {
            for (String serviceValue : serviceValues) {
                try {
                    serviceCodes |= Node.Service.valueOf(serviceValue).getCode();
                } catch (RuntimeException exc) {
                    return JSONResponses.incorrect("service", "- '" + serviceValue + "' is not defined");
                }
            }
        }

        Collection<Node> nodes;
        if (active) {
            nodes = Nodes.getNodes(p -> p.getState() != Node.State.NON_CONNECTED);
        } else if (state != null) {
            nodes = Nodes.getNodes(p -> p.getState() == state);
        } else {
            nodes = Nodes.getAllNodes();
        }

        JSONArray nodesJSON = new JSONArray();
        if (serviceCodes != 0) {
            final long services = serviceCodes;
            if (includeNodeInfo) {
                nodes.forEach(node -> {
                    if (node.providesServices(services)) {
                        nodesJSON.add(JSONData.node(node));
                    }
                });
            } else {
                nodes.forEach(node -> {
                    if (node.providesServices(services)) {
                        nodesJSON.add(node.getHost());
                    }
                });
            }
        } else {
            if (includeNodeInfo) {
                nodes.forEach(node -> nodesJSON.add(JSONData.node(node)));
            } else {
                nodes.forEach(node -> nodesJSON.add(node.getHost()));
            }
        }

        JSONObject response = new JSONObject();
        response.put("nodes", nodesJSON);
        return response;
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

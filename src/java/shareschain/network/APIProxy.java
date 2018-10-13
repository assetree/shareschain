
package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.node.Node;
import shareschain.node.Nodes;
import shareschain.util.Logger;
import shareschain.util.ThreadPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class APIProxy {
    public static final Set<String> NOT_FORWARDED_REQUESTS;

    private static final APIProxy instance = new APIProxy();

    static final boolean enableAPIProxy = Constants.isLightClient ||
            (Shareschain.getBooleanProperty("shareschain.enableAPIProxy") && ! API.isOpenAPI);
    private static final int blacklistingPeriod = Shareschain.getIntProperty("shareschain.apiProxyBlacklistingPeriod") / 1000;
    static final String forcedServerURL = Shareschain.getStringProperty("shareschain.forceAPIProxyServerURL", "");

    private volatile String forcedNodeHost;
    private volatile List<String> nodesHosts = Collections.emptyList();
    private volatile String mainNodeAnnouncedAddress;

    private final Map<String, Integer> blacklistedNodes = new ConcurrentHashMap<>();

    static {
        Set<String> requests = new HashSet<>();
        requests.add("getBlockchainStatus");
        requests.add("getState");

        final EnumSet<APITag> notForwardedTags = EnumSet.of(APITag.DEBUG, APITag.NETWORK);

        for (APIEnum api : APIEnum.values()) {
            APIServlet.APIRequestHandler handler = api.getHandler();
            if (handler.requireBlockchain() && !Collections.disjoint(handler.getAPITags(), notForwardedTags)) {
                requests.add(api.getName());
            }
        }

        NOT_FORWARDED_REQUESTS = Collections.unmodifiableSet(requests);
    }

    private static final Runnable nodesUpdateThread = () -> {
        int curTime = Shareschain.getEpochTime();
        instance.blacklistedNodes.entrySet().removeIf((entry) -> {
            if (entry.getValue() < curTime) {
                Logger.logDebugMessage("Unblacklisting API node " + entry.getKey());
                return true;
            }
            return false;
        });
        List<String> currentNodesHosts = instance.nodesHosts;
        if (currentNodesHosts != null) {
            for (String host : currentNodesHosts) {
                Node node = Nodes.getNode(host);
                if (node != null) {
                    node.connectNode();
                }
            }
        }
    };

    static {
        if (!Constants.isOffline && enableAPIProxy) {
            ThreadPool.scheduleThread("APIProxyNodesUpdate", nodesUpdateThread, 60);
        }
    }

    private APIProxy() {

    }

    public static void init() {}

    public static APIProxy getInstance() {
        return instance;
    }

    Node getServingNode(String requestType) {
        if (forcedNodeHost != null) {
            return Nodes.getNode(forcedNodeHost);
        }

        APIEnum requestAPI = APIEnum.fromName(requestType);
        if (!nodesHosts.isEmpty()) {
            for (String host : nodesHosts) {
                Node node = Nodes.getNode(host);
                if (node != null && node.isApiConnectable() && !node.getDisabledAPIs().contains(requestAPI)) {
                    return node;
                }
            }
        }

        List<Node> connectableNodes = Nodes.getNodes(p -> p.isApiConnectable() && !blacklistedNodes.containsKey(p.getHost()));
        if (connectableNodes.isEmpty()) {
            return null;
        }
        // subset of connectable nodes that have at least one new API enabled, which was disabled for the
        // The first node (element 0 of nodesHosts) is chosen at random. Next nodes are chosen randomly from a
        // previously chosen nodes. In worst case the size of nodesHosts will be the number of APIs
        Node node = getRandomAPINode(connectableNodes);
        if (node == null) {
            return null;
        }

        Node resultNode = null;
        List<String> currentNodesHosts = new ArrayList<>();
        EnumSet<APIEnum> disabledAPIs = EnumSet.noneOf(APIEnum.class);
        currentNodesHosts.add(node.getHost());
        mainNodeAnnouncedAddress = node.getAnnouncedAddress();
        if (!node.getDisabledAPIs().contains(requestAPI)) {
            resultNode = node;
        }
        while (!disabledAPIs.isEmpty() && !connectableNodes.isEmpty()) {
            // remove all nodes that do not introduce new enabled APIs
            connectableNodes.removeIf(p -> p.getDisabledAPIs().containsAll(disabledAPIs));
            node = getRandomAPINode(connectableNodes);
            if (node != null) {
                currentNodesHosts.add(node.getHost());
                if (!node.getDisabledAPIs().contains(requestAPI)) {
                    resultNode = node;
                }
                disabledAPIs.retainAll(node.getDisabledAPIs());
            }
        }
        nodesHosts = Collections.unmodifiableList(currentNodesHosts);
        Logger.logInfoMessage("Selected API node " + resultNode + " node hosts selected " + currentNodesHosts);
        return resultNode;
    }

    Node setForcedNode(Node node) {
        if (node != null) {
            forcedNodeHost = node.getHost();
            mainNodeAnnouncedAddress = node.getAnnouncedAddress();
            return node;
        } else {
            forcedNodeHost = null;
            mainNodeAnnouncedAddress = null;
            return getServingNode(null);
        }
    }

    String getMainNodeAnnouncedAddress() {
        // The first client request GetBlockchainState is handled by the server
        // Not by the proxy. In order to report a node to the client we have
        // To select some initial node.
        if (mainNodeAnnouncedAddress == null) {
            Node node = getServingNode(null);
            if (node != null) {
                mainNodeAnnouncedAddress = node.getAnnouncedAddress();
            }
        }
        return mainNodeAnnouncedAddress;
    }

    static boolean isActivated() {
        return Constants.isLightClient || (enableAPIProxy && Shareschain.getBlockchainProcessor().isDownloading());
    }

    boolean blacklistHost(String host) {
        if (blacklistedNodes.size() > 1000) {
            Logger.logInfoMessage("Too many blacklisted nodes");
            return false;
        }
        blacklistedNodes.put(host, Shareschain.getEpochTime() + blacklistingPeriod);
        if (nodesHosts.contains(host)) {
            nodesHosts = Collections.emptyList();
            getServingNode(null);
        }
        return true;
    }

    private Node getRandomAPINode(List<Node> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(nodes.size());
        return nodes.remove(index);
    }
}

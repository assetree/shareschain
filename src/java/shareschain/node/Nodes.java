package shareschain.node;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.account.Account;
import shareschain.permission.Role;
import shareschain.permission.RoleMapperFactory;
import shareschain.util.crypto.Crypto;
import shareschain.database.DB;
import shareschain.network.API;
import shareschain.util.Convert;
import shareschain.util.Filter;
import shareschain.util.Listener;
import shareschain.util.Listeners;
import shareschain.util.Logger;
import shareschain.util.QueuedThreadPool;
import shareschain.util.ThreadPool;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Nodes {

    public enum Event {
        BLACKLIST,                      // Blackisted
        UNBLACKLIST,                    // Unblacklisted node
        ADD_NODE,                       // Node added to node list
        CHANGE_ANNOUNCED_ADDRESS,       // Node changed announced address
        CHANGE_SERVICES,                // Node changed services
        REMOVE_NODE,                    // Removed node from node list
        ADD_ACTIVE_NODE,                // Node is now active
        CHANGE_ACTIVE_NODE              // Active node state changed
    }

    /** Maximum application version length */
    static final int MAX_VERSION_LENGTH = 10;

    /** Maximum application name length */
    static final int MAX_APPLICATION_LENGTH = 20;

    /** Maximum application platform length */
    static final int MAX_PLATFORM_LENGTH = 30;

    /** Maximum announced address length */
    static final int MAX_ANNOUNCED_ADDRESS_LENGTH = 100;

    /** Bundler rate broadcast interval */
    static final int BUNDLER_RATE_BROADCAST_INTERVAL = 30 * 60;

    /** Communication log levels */
    public static final int LOG_LEVEL_NAMES = 1;
    public static final int LOG_LEVEL_DETAILS = 2;

    /** Node blacklist period (seconds) */
    static final int blacklistingPeriod = Shareschain.getIntProperty("shareschain.blacklistingPeriod", 600);

    /** Communication logging (0=no logging, 1=log message names) */
    private static int communicationLogging = Shareschain.getIntProperty("shareschain.communicationLogging", 0);

    /** Get more nodes */
    private static final boolean getMoreNodes = Shareschain.getBooleanProperty("shareschain.getMoreNodes");

    /** Maximum number of known nodes */
    private static final int maxNumberOfKnownNodes =
            Math.max(100, Shareschain.getIntProperty("shareschain.maxNumberOfKnownNodes", 2000));

    /** Minimum number of known nodes */
    private static final int minNumberOfKnownNodes = Math.max(100,
            Math.min(maxNumberOfKnownNodes, Shareschain.getIntProperty("shareschain.minNumberOfKnownNodes", 1000)));

    /** Use nodes database */
    private static final boolean useNodesDB = Shareschain.getBooleanProperty("shareschain.useNodesDB");

    /** Save Nodes */
    private static final boolean saveNodes = Shareschain.getBooleanProperty("shareschain.saveNodes");

    /** Hide error details */
    static final boolean hideErrorDetails = Shareschain.getBooleanProperty("shareschain.hideErrorDetails");

    /** Ignore node announced address */
    static final boolean ignoreNodeAnnouncedAddress = Shareschain.getBooleanProperty("shareschain.ignoreNodeAnnouncedAddress");


    /** Node credentials */
    static final String nodeSecretPhrase = Shareschain.getStringProperty("shareschain.credentials.secretPhrase", null, true);

    /** Local node services */
    static final List<Node.Service> myServices;
    static {
        List<Node.Service> services = new ArrayList<>();
        if (!Constants.ENABLE_PRUNING && Constants.INCLUDE_EXPIRED_PRUNABLE) {
            services.add(Node.Service.PRUNABLE);
        }
        if (API.openAPIPort > 0) {
            services.add(Node.Service.API);
        }
        if (API.openAPISSLPort > 0) {
            services.add(Node.Service.API_SSL);
        }
        if (API.apiServerCORS) {
            services.add(Node.Service.CORS);
        }
        myServices = Collections.unmodifiableList(services);
    }

    /** Well-known nodes */
    private static final List<String> wellKnownNodes = Constants.isTestnet ?
            Shareschain.getStringListProperty("shareschain.testnetNodes") : Shareschain.getStringListProperty("shareschain.wellKnownNodes");

    /** Known blacklisted Nodes */
    static final Set<String> knownBlacklistedNodes;
    static {
        List<String> knownBlacklistedNodesList = Shareschain.getStringListProperty("shareschain.knownBlacklistedNodes");
        if (knownBlacklistedNodesList.isEmpty()) {
            knownBlacklistedNodes = Collections.emptySet();
        } else {
            knownBlacklistedNodes = Collections.unmodifiableSet(new HashSet<>(knownBlacklistedNodesList));
        }
    }

    /** Node event listeners */
    private static final Listeners<Node, Event> listeners = new Listeners<>();

    /** Known nodes */
    private static final ConcurrentMap<String, NodeImpl> nodes = new ConcurrentHashMap<>();

    /** Known announced addresses */
    private static final ConcurrentMap<String, String> selfAnnouncedAddresses = new ConcurrentHashMap<>();

    /** Read-only node list */
    private static final Collection<Node> allNodes = Collections.unmodifiableCollection(nodes.values());

    /** Nodes executor service pool */
    static final ExecutorService nodesService = new QueuedThreadPool(2, 15);

    /** Start time */
    private static final int startTime = Shareschain.getEpochTime();

    /** Broadcast blockchain state */
    private static Node.BlockchainState broadcastBlockchainState = Node.BlockchainState.UP_TO_DATE;

    /** Bundler rates broadcast time */
    private static int ratesTime = startTime ;

    /**
     * Initialize node processing
     */
    public static void init() {
        if (Constants.isOffline) {
            Logger.logInfoMessage("Node services are offline");
            return;
        }
        /**
         * 从shareschain.properties中获取默认节点信息
         */
        final List<String> defaultNodes = Constants.isTestnet ?
                Shareschain.getStringListProperty("shareschain.defaultTestnetNodes") : Shareschain.getStringListProperty("shareschain.defaultNodes");

        final List<Future<String>> unresolvedNodes = Collections.synchronizedList(new ArrayList<>());
        //
        // Check node permission
        //
        // In the case of a new node, the node account might not exist yet.  So we will allow
        // it to create outbound connections in order to download the blockchain.  Note that this
        // means the default nodes defined in shareschain-default.properties must have accounts that are
        // defined in the genesis block in order for a new node to accept the connection.
        //
        if (Constants.isPermissioned && nodeSecretPhrase != null) {
            byte[] publicKey = Crypto.getPublicKey(nodeSecretPhrase);
            long accountId = Account.getId(publicKey);
            if (!RoleMapperFactory.getRoleMapper().isUserInRole(accountId, Role.WRITER)) {
                Logger.logWarningMessage("WARNING: Account " + Convert.rsAccount(accountId) + " does not have WRITER permission");
            }
        }
        //
        // Build the node list
        //
            ThreadPool.runBeforeStart(new Runnable() {
            private final Set<NodeDB.Entry> entries = new HashSet<>();

            @Override
            public void run() {
                /**
                 * 将共用的节点，添加到 entries 中
                 */
                wellKnownNodes.forEach(address -> entries.add(new NodeDB.Entry(address, 0, startTime - 1)));
                /**
                 * 判断是否使用本地数据库中记录的几点信息，shareschain.properties中配置
                 */
                if (useNodesDB) {
                    Logger.logDebugMessage("Loading known nodes from the database...");
                    /**
                     * 将默认节点添加到entries中
                     */
                    defaultNodes.forEach(address -> entries.add(new NodeDB.Entry(address, 0, startTime - 1)));

                    if (saveNodes) {
                        List<NodeDB.Entry> dbNodes = NodeDB.loadNodes();
                        dbNodes.forEach(entry -> {
                            if (!entries.add(entry)) {
                                // Database entries override entries from shareschain.properties
                                entries.remove(entry);
                                entries.add(entry);
                            }
                        });
                    }
                }

                /**
                 * 线程池处理已发现节点列表，
                 * 测试节点是否链接成功，并将返回结果放入unresolvedNodes 未确定节点列表中
                 * 将已连接的节点放入ConcurrentMap nodes列表中
                 */
                entries.forEach(entry -> {
                    Future<String> unresolvedAddress = nodesService.submit(() -> {
                        NodeImpl node = (NodeImpl)Nodes.findOrCreateNode(entry.getAddress(), true);
                        if (node != null) {
                            node.setShareAddress(true);
                            node.setLastUpdated(entry.getLastUpdated());
                            node.setServices(entry.getServices());
                            Nodes.addNode(node);

                            // 连接成功，返回null。
                            return null;
                        }
                        // 连接不成功返回地址
                        return entry.getAddress();
                    });
                    // 将Future对象加入到链表中。
                    unresolvedNodes.add(unresolvedAddress);
                });
            }
        }, false);
        //
        // Check the results
        //
        /**
         * 打印未能成功解析的节点地址
         */
        ThreadPool.runAfterStart(() -> {
            for (Future<String> unresolvedNode : unresolvedNodes) {
                try {
                    // 遍历Future链表，打印出未连接成功的节点。
                    String badAddress = unresolvedNode.get(5, TimeUnit.SECONDS);
                    if (badAddress != null) {
                        Logger.logDebugMessage("Failed to resolve node address: " + badAddress);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Logger.logDebugMessage("Failed to add node", e);
                } catch (TimeoutException ignore) {
                }
            }
            Logger.logDebugMessage("Known nodes: " + nodes.size());
        });
        //
        // Schedule our background processing threads
        //
        /**
         *每1分钟修改一次黑名单状态
         * 黑名单是为了防止DDOS攻击
         */
        ThreadPool.scheduleThread("NodeUnBlacklisting", nodeUnBlacklistingThread, 60);

        /**
         * 连接节点线程
         */
        ThreadPool.scheduleThread("NodeConnecting", nodeConnectingThread, 15);
        if (getMoreNodes) {
            /**
             * 10分钟获取一次新的节点
             */
            ThreadPool.scheduleThread("GetMoreNodes", getMoreNodesThread, 60);
    	}
        if (saveNodes) {
            ThreadPool.scheduleThread("UpdateNodeDB", updateNodeDBThread, 60*60);
        }
    }

    /**
     * Shutdown node processing
     */
    public static void shutdown() {
        ThreadPool.shutdownExecutor("nodesService", nodesService, 5);
    }

    /**
     * Add a node listener
     *
     * @param   listener                Listener
     * @param   eventType               Listener event
     * @return                          TRUE if the listener was added
     */
    public static boolean addListener(Listener<Node> listener, Event eventType) {
        return Nodes.listeners.addListener(listener, eventType);
    }

    /**
     * Remove a node listener
     *
     * @param   listener                Listener
     * @param   eventType               Listener event
     * @return                          TRUE if the listener was removed
     */
    public static boolean removeListener(Listener<Node> listener, Event eventType) {
        return Nodes.listeners.removeListener(listener, eventType);
    }

    /**
     * Notify all listeners for the specified event
     *
     * @param   node                    Node
     * @param   eventType               Listener event
     */
    static void notifyListeners(Node node, Event eventType) {
        Nodes.listeners.notify(node, eventType);
    }

    /**
     * Add a node to the node list
     *
     * @param   node                    Node to add
     * @return                          TRUE if this is a new node
     */
    public static boolean addNode(Node node) {
        Node oldNode = nodes.put(node.getHost(), (NodeImpl)node);
        if (oldNode != null) {
            return false;
        }
        selfAnnouncedAddresses.put(node.getAnnouncedAddress(), node.getHost());
        listeners.notify(node, Event.ADD_NODE);
        return true;
    }

    /**
     * Change the announced address for a node
     *
     * @param   node                    Node
     * @param   newAnnouncedAddress     The new announced address
     */
    static void changeNodeAnnouncedAddress(NodeImpl node, String newAnnouncedAddress) {
        selfAnnouncedAddresses.remove(node.getAnnouncedAddress());
        node.setAnnouncedAddress(newAnnouncedAddress);
        selfAnnouncedAddresses.put(node.getAnnouncedAddress(), node.getHost());
        listeners.notify(node,Event.CHANGE_ANNOUNCED_ADDRESS);
    }

    /**
     * Remove a node from the node list
     *
     * @param   node                    Node to remove
     * @return                          TRUE if the node was removed
     */
    public static boolean removeNode(Node node) {
        if (node.getAnnouncedAddress() != null) {
            selfAnnouncedAddresses.remove(node.getAnnouncedAddress());
        }
        if (nodes.remove(node.getHost()) == null) {
            return false;
        }
        notifyListeners(node, Event.REMOVE_NODE);
        return true;
    }

    /**
     * Return local node services
     *
     * @return                      List of local node services
     */
    public static List<Node.Service> getServices() {
        return myServices;
    }

    /**
     * Find or create a node
     *
     * The announced address will be used for the host address if a new node is created
     *
     * @param   announcedAddress        Node announced address
     * @param   create                  TRUE to create the node if it is not found
     * @return                          Node or null if the node could not be created
     */
    public static Node findOrCreateNode(String announcedAddress, boolean create) {
        NodeImpl node;
        if (announcedAddress == null) {
            return null;
        }
        String address = announcedAddress.toLowerCase().trim();
        String hostAddress = selfAnnouncedAddresses.get(address);
        if (hostAddress != null && (node = nodes.get(hostAddress)) != null) {
            return node;
        }
        InetAddress inetAddress;
        String host;
        int port;
        try {
            URI uri = new URI("http://" + address);
            host = uri.getHost();
            if (host == null) {
                return null;
            }
            port = (uri.getPort() == -1 ? NetworkHandler.getDefaultNodePort() : uri.getPort());
            inetAddress = InetAddress.getByName(host);
        } catch (UnknownHostException | URISyntaxException e) {
            return null;
        }
        if (Constants.isTestnet && port != NetworkHandler.TESTNET_NODE_PORT) {
            Logger.logDebugMessage("Node " + host + " on testnet is not using port " + NetworkHandler.TESTNET_NODE_PORT + ", ignoring");
            return null;
        }
        if (!Constants.isTestnet && port == NetworkHandler.TESTNET_NODE_PORT) {
            Logger.logDebugMessage("Node " + host + " is using testnet port " + NetworkHandler.TESTNET_NODE_PORT + ", ignoring");
            return null;
        }
        return findOrCreateNode(inetAddress, address, create);
    }

    /**
     * Find or create a node
     *
     * The announced address will be set to the host address if a new node is created.
     * A new node will be created if an existing node is not found.
     *
     * @param   inetAddress             Node address
     * @return                          Node or null if the node could not be created
     */
    static NodeImpl findOrCreateNode(InetAddress inetAddress) {
        return findOrCreateNode(inetAddress, inetAddress.getHostAddress(), true);
    }

    /**
     * Find or create a node
     *
     * @param   inetAddress             Node address
     * @param   announcedAddress        Announced address
     * @param   create                  TRUE to create the node if it doesn't exist
     * @return                          Node or null if the node could not be created
     */
    private static NodeImpl findOrCreateNode(InetAddress inetAddress, String announcedAddress, boolean create) {
        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
            return null;
        }
        NodeImpl node;
        String host = inetAddress.getHostAddress();
        if ((node = nodes.get(host)) != null) {
            return node;
        }
        if (!create) {
            return null;
        }
        if (NetworkHandler.announcedAddress != null && NetworkHandler.announcedAddress.equalsIgnoreCase(announcedAddress)) {
            return null;
        }
        if (announcedAddress != null && announcedAddress.length() > MAX_ANNOUNCED_ADDRESS_LENGTH) {
            return null;
        }
        node = new NodeImpl(inetAddress, announcedAddress);
        return node;
    }

    public static Node getNode(String host) {
        return nodes.get(host);
    }

    /**
     * Get a random node that satisfies the supplied filter
     *
     * @param   filter                  Filter
     * @return                          Selected node or null
     */
    public static Node getAnyNode(Filter<Node> filter) {
        List<? extends Node> nodeList = new ArrayList<>(nodes.values());
        return getAnyNode(nodeList, filter);
    }

    /**
     * Get a random node from the supplied list
     *
     * @param   nodeList                Node list
     * @return                          Selected node or null
     */
    public static Node getAnyNode(List<? extends Node> nodeList) {
        return getAnyNode(nodeList, null);
    }

    /**
     * Get a random node that satisfies the supplied filter
     *
     * @param   nodeList                Node list
     * @param   filter                  Filter or null if no filter supplied
     * @return                          Selected node or null
     */
    public static Node getAnyNode(List<? extends Node> nodeList, Filter<Node> filter) {
        if (nodeList.isEmpty()) {
            return null;
        }
        Node node = null;
        int start = ThreadLocalRandom.current().nextInt(nodeList.size());
        boolean foundNode = false;
        for (int i=start; i<nodeList.size(); i++) {
            node = nodeList.get(i);
            if (filter == null || filter.ok(node)) {
                foundNode = true;
                break;
            }
        }
        if (!foundNode) {
            for (int i=0; i<start; i++) {
                node = nodeList.get(i);
                if (filter == null || filter.ok(node)) {
                    foundNode = true;
                    break;
                }
            }
        }
        return (foundNode ? node : null);
    }

    /**
     * Get a list of nodes satisfying the supplied filter
     *
     * @param   filter                  Filter
     * @return                          List of nodes
     */
    public static List<Node> getNodes(Filter<Node> filter) {
        return getNodes(filter, Integer.MAX_VALUE);
    }

    /**
     * Get a list of nodes satisfying the supplied filter
     *
     * @param   filter                  Filter
     * @param   limit                   Maximum number of nodes to return
     * @return                          List of nodes
     */
    public static List<Node> getNodes(Filter<Node> filter, int limit) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (filter.ok(node)) {
                result.add(node);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Return all known nodes
     *
     * @return                          List of known nodes
     */
    public static Collection<Node> getAllNodes() {
        return allNodes;
    }

    /**
     * Return all connected nodes
     *
     * @return                          List of connected nodes
     */
    public static List<Node> getConnectedNodes() {
        return new ArrayList<>(NetworkHandler.connectionMap.values());
    }

    /**
     * Check if we should get more nodes
     *
     * @return                          TRUE if we should get more nodes
     */
    static boolean shouldGetMoreNodes() {
        return getMoreNodes;
    }

    /**
     * Check if there are too many known nodes
     *
     * @return                          TRUE if there are too many known nodes
     */
    static boolean hasTooManyKnownNodes() {
        return nodes.size() >= maxNumberOfKnownNodes;
    }

    /**
     * Update node blacklist status
     */
    private static final Runnable nodeUnBlacklistingThread = () -> {
        try {
            int curTime = Shareschain.getEpochTime();
            for (NodeImpl node : nodes.values()) {
                node.updateBlacklistedStatus(curTime);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Create outbound connections
     */
    private static final Runnable nodeConnectingThread = () -> {
        if (!NetworkHandler.isNetworkStarted()) {
            return;
        }
        try {
            final int now = Shareschain.getEpochTime();
            //
            // Create new outbound connections
            //
            // The well-known nodes are tried first.  If we need more outbound connections, we
            // will randomly select nodes from the list of known nodes.
            //
            int connectCount = Math.min(10, NetworkHandler.getMaxOutboundConnections() - NetworkHandler.getOutboundCount());
            List<NodeImpl> connectList = new ArrayList<>();
            if (connectCount > 0) {
                for (String wellKnownNode : wellKnownNodes) {
                    NodeImpl node = (NodeImpl)findOrCreateNode(wellKnownNode, true);
                    if (node == null) {
                        Logger.logWarningMessage("Unable to create node for well-known node " + wellKnownNode);
                        continue;
                    }
                    /**
                     * 判断不在黑名单中
                     * 判断节点是否是对外分享地址
                     * 判断节点不是链接状态
                     * 判断节点最后一次链接时间在10分钟之前
                     * 判断节点最后一次链接时间在当前节点开始运行之前  当前节点启动时，处理所有节点
                     * 将节点放入链接列表中
                     */
                    if (!node.isBlacklisted()
                            && node.shareAddress()
                            && node.getState() != Node.State.CONNECTED
                            && now - node.getLastConnectAttempt() > 10*60 || node.getLastConnectAttempt() < startTime) {
                        node.setLastConnectAttempt(now);
                        connectList.add(node);
                        connectCount--;
                        if (connectCount == 0) {
                            break;
                        }
                    }
                }
            }
            if (connectCount > 0) {
                List<Node> resultList = getNodes(node -> !node.isBlacklisted()
                        && node.shareAddress()
                        && node.getState() != Node.State.CONNECTED
                        && (now - node.getLastUpdated() > 60*60 || node.getLastUpdated() < startTime)
                        && now - node.getLastConnectAttempt() > 10*60 || node.getLastConnectAttempt() < startTime);
                while (!resultList.isEmpty() && connectCount > 0) {
                    int i = ThreadLocalRandom.current().nextInt(resultList.size());
                    NodeImpl node = (NodeImpl)resultList.remove(i);
                    node.setLastConnectAttempt(now);
                    connectList.add(node);
                    connectCount--;
                }
            }

            /**
             * 使用线程池创建与节点的通道，放入connectionMap 中
             */
            if (!connectList.isEmpty()) {
                connectList.forEach(node -> nodesService.execute(node::connectNode));
            }
            /**
             * Check for dead connections
             * 如果节点握手成功并且最后一次链接时间为10s之前，从connectionMap中去除此节点
            */
            getConnectedNodes().forEach(node -> {
                if (((NodeImpl)node).isHandshakePending() && node.getLastUpdated() < now - NetworkHandler.nodeConnectTimeout) {
                    node.disconnectNode();
                }
            });
            //
            // Remove nodes if we have too many 2000
            //
            if (nodes.size() > maxNumberOfKnownNodes) {
                int initialSize = nodes.size();
                /**
                 * Comparator.comparingInt 按照节点的最后一次更新时间进行降序
                 */
                PriorityQueue<NodeImpl> sortedNodes = new PriorityQueue<>(nodes.size(), Comparator.comparingInt(NodeImpl::getLastUpdated));
                sortedNodes.addAll(nodes.values());
                /**
                 *删除节点，直至节点数目少于100
                 */
                while (nodes.size() > minNumberOfKnownNodes) {
                    sortedNodes.poll().remove();
                }
                Logger.logDebugMessage("Reduced node pool size from " + initialSize + " to " + nodes.size());
            }
            //
            // Notify connected nodes if our blockchain state has changed
            //
            Node.BlockchainState currentState = getMyBlockchainState();
            if (currentState != broadcastBlockchainState) {
                Logger.logDebugMessage("Broadcasting blockchain state change from "
                        + broadcastBlockchainState.name() + " to " + currentState.name());
                NetworkMessage blockchainStateMessage = new NetworkMessage.BlockchainStateMessage(currentState);
                NetworkHandler.broadcastMessage(blockchainStateMessage);
                broadcastBlockchainState = currentState;
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Get more nodes
     */
    private static final Runnable getMoreNodesThread = () -> {
        try {
            /**
             * 随机获取一个已连接的节点，给这个节点发送1、获取节点的消息；2、本节点已连接节点的列表
             *
             */
            Node node = getAnyNode(getConnectedNodes());
            if (node != null && node.getState() == Node.State.CONNECTED) {
                //
                // Request a list of connected nodes (the response is asynchronous)
                //
                if (nodes.size() < maxNumberOfKnownNodes) {
                    node.sendMessage(new NetworkMessage.GetNodesMessage());
                }
                //
                // Send a list of our connected nodes
                //
                List<Node> nodeList = getNodes(p -> !p.isBlacklisted()
                        && p.getState() == Node.State.CONNECTED
                        && p.getAnnouncedAddress() != null
                        && p.shareAddress()
                        && !p.getAnnouncedAddress().equals(node.getAnnouncedAddress()),
                    NetworkMessage.MAX_LIST_SIZE);
                if (!nodeList.isEmpty()) {
                    node.sendMessage(new NetworkMessage.AddNodesMessage(nodeList));
                }
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /**
     * Update the node database
     */
    private static final Runnable updateNodeDBThread = () -> {
        try {
            int now = Shareschain.getEpochTime();
            //
            // Load the current database entries and map announced address to database entry
            //
            List<NodeDB.Entry> oldNodes = NodeDB.loadNodes();
            Map<String, NodeDB.Entry> oldMap = new HashMap<>(oldNodes.size());
            oldNodes.forEach(entry -> oldMap.put(entry.getAddress(), entry));
            //
            // Create the current node map (note that there can be duplicate node entries with
            // the same announced address)
            //
            Map<String, NodeDB.Entry> currentNodes = new HashMap<>();
            nodes.values().forEach(node -> {
                if (node.getAnnouncedAddress() != null
                        && node.shareAddress()
                        && !node.isBlacklisted()
                        && now - node.getLastUpdated() < 7*24*3600) {
                    currentNodes.put(node.getAnnouncedAddress(),
                            new NodeDB.Entry(node.getAnnouncedAddress(), node.getServices(), node.getLastUpdated()));
                }
            });
            //
            // Build toDelete and toUpdate lists
            //
            List<NodeDB.Entry> toDelete = new ArrayList<>(oldNodes.size());
            oldNodes.forEach(entry -> {
                if (currentNodes.get(entry.getAddress()) == null)
                    toDelete.add(entry);
            });
            List<NodeDB.Entry> toUpdate = new ArrayList<>(currentNodes.size());
            currentNodes.values().forEach(entry -> {
                NodeDB.Entry oldEntry = oldMap.get(entry.getAddress());
                if (oldEntry == null || entry.getLastUpdated() - oldEntry.getLastUpdated() > 24*3600)
                    toUpdate.add(entry);
            });
            //
            // Nothing to do if all of the lists are empty
            //
            if (toDelete.isEmpty() && toUpdate.isEmpty()) {
                return;
            }
            //
            // Update the node database
            //
            try {
                DB.db.beginTransaction();
                NodeDB.deleteNodes(toDelete);
                NodeDB.updateNodes(toUpdate);
                DB.db.commitTransaction();
            } catch (Exception e) {
                DB.db.rollbackTransaction();
                throw e;
            } finally {
                DB.db.endTransaction();
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS", t);
            System.exit(1);
        }
    };

    /*
     * Update the node database when the services provided by a node changes
     */
    static {
        Nodes.addListener(node -> nodesService.submit(() -> {
            if (node.getAnnouncedAddress() != null && !node.isBlacklisted()) {
                try {
                    DB.db.beginTransaction();
                    NodeDB.updateNode((NodeImpl) node);
                    DB.db.commitTransaction();
                } catch (RuntimeException e) {
                    Logger.logErrorMessage("Unable to update node database", e);
                    DB.db.rollbackTransaction();
                } finally {
                    DB.db.endTransaction();
                }
            }
        }), Nodes.Event.CHANGE_SERVICES);
    }

    /**
     * Check for an old NRS version
     *
     * @param   version         Node version
     * @param   minVersion      Minimum acceptable version
     * @return                  TRUE if this is an old version
     */
    static boolean isOldVersion(String version, int[] minVersion) {
        if (version == null) {
            return true;
        }
        String[] versions = (version.endsWith("e") ?
                version.substring(0, version.length() - 1).split("\\.") : version.split("\\."));
        for (int i = 0; i < minVersion.length && i < versions.length; i++) {
            try {
                int v = Integer.parseInt(versions[i]);
                if (v > minVersion[i]) {
                    return false;
                } else if (v < minVersion[i]) {
                    return true;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return versions.length < minVersion.length;
    }

    private static final int[] MAX_VERSION;
    static {
        String version = Shareschain.VERSION;
        if (version.endsWith("e")) {
            version = version.substring(0, version.length() - 1);
        }
        String[] versions = version.split("\\.");
        MAX_VERSION = new int[versions.length];
        for (int i = 0; i < versions.length; i++) {
            MAX_VERSION[i] = Integer.parseInt(versions[i]);
        }
    }

    /**
     * Check for a new version
     *
     * @param   version         Node version
     * @return                  TRUE if this is a newer version
     */
    static boolean isNewVersion(String version) {
        if (version == null) {
            return true;
        }
        String[] versions = (version.endsWith("e") ?
                version.substring(0, version.length() - 1).split("\\.") : version.split("\\."));
        for (int i = 0; i < MAX_VERSION.length && i < versions.length; i++) {
            try {
                int v = Integer.parseInt(versions[i]);
                if (v > MAX_VERSION[i]) {
                    return true;
                } else if (v < MAX_VERSION[i]) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return versions.length > MAX_VERSION.length;
    }

    /**
     * Get the current blockchain state
     *
     * @return                  The blockchain state
     */
    public static Node.BlockchainState getMyBlockchainState() {
        return Constants.isLightClient ? Node.BlockchainState.LIGHT_CLIENT :
                (Shareschain.getBlockchainProcessor().isDownloading() ||
                        Shareschain.getBlockchain().getLastBlockTimestamp() < Shareschain.getEpochTime() - 600) ?
                    Node.BlockchainState.DOWNLOADING :
                        (Shareschain.getBlockchain().getLastBlock().getBaseTarget() / Constants.INITIAL_BASE_TARGET > 10 &&
                                !Constants.isTestnet) ? Node.BlockchainState.FORK :
                        Node.BlockchainState.UP_TO_DATE;
    }

    /**
     * Check if the specified communication log level is enabled
     *
     * @param   logLevel            Communication logging level
     * @return                      TRUE if the log level is enabled
     */
    public static boolean isLogLevelEnabled(int logLevel) {
        return ((communicationLogging & logLevel) != 0);
    }

    /**
     * Set communication logging
     *
     * @param   logging             Communication logging value
     */
    public static void setCommunicationLogging(int logging) {
        communicationLogging = logging;
    }

    private Nodes() {} // never

    public static void main(String[] agrs){
        System.out.println(1 & 1);
    }
}

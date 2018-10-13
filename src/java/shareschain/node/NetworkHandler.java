package shareschain.node;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.util.crypto.Crypto;
import shareschain.network.API;
import shareschain.network.APIEnum;
import shareschain.util.Convert;
import shareschain.util.Logger;
import shareschain.util.ThreadPool;
import shareschain.util.UPnP;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The network handler creates outbound connections and adds them to the
 * network selector.  A new outbound connection will be created whenever
 * the number of outbound connections is less than the maximum number of
 * outbound connections.
 *
 * The network handler opens a local port and listens for incoming connections.
 * When a connection is received, it creates a socket channel and accepts the
 * connection as long as the maximum number of inbound connections has not been reached.
 * The socket is then added to the network selector.
 *
 * When a message is received from a node node, it is processed by a message
 * handler executing on a separate thread.  The message handler processes the
 * message and then creates a response message to be returned to the originating node.
 *
 * The network handler terminates when its shutdown() method is called.
 */
public final class NetworkHandler implements Runnable {

    /** Default node port */
    static final int DEFAULT_NODE_PORT = (Constants.isPermissioned ? 51314 : 31415);

    /** Testnet node port */
    static final int TESTNET_NODE_PORT = (Constants.isPermissioned ? 51314 : 31415);

    /** Maximum number of pending messages for a single node */
    static final int MAX_PENDING_MESSAGES = 25;

    /** Message header magic bytes */
    private static final byte[] MESSAGE_HEADER_MAGIC = new byte[] {(byte)0x03, (byte)0x2c, (byte)0x05, (byte)0xc2};

    /** Message header length */
    private static final int MESSAGE_HEADER_LENGTH = MESSAGE_HEADER_MAGIC.length + 4;

    /** Maximum message size */
    static final int MAX_MESSAGE_SIZE = 1024 * 1024;

    /** Server port */
    private static final int serverPort = Constants.isTestnet ? TESTNET_NODE_PORT :
            Shareschain.getIntProperty("shareschain.NodeServerPort", DEFAULT_NODE_PORT);

    /** Enable UPnP */
    private static final boolean enableNodeUPnP = Shareschain.getBooleanProperty("shareschain.enableNodeUPnP");

    /** Share my address */
    private static final boolean shareAddress = Shareschain.getBooleanProperty("shareschain.shareMyAddress");

    /** Maximum number of outbound connections */
    private static final int maxOutbound = Shareschain.getIntProperty("shareschain.maxNumberOfOutboundConnections", 8);

    /** Maximum number of inbound connections */
    private static final int maxInbound = Shareschain.getIntProperty("shareschain.maxNumberOfInboundConnections", 64);

    /** Connect timeout (seconds) */
    static final int nodeConnectTimeout = Shareschain.getIntProperty("shareschain.NodeConnectTimeout", 10);

    /** Node read timeout (seconds) */
    static final int nodeReadTimeout = Shareschain.getIntProperty("shareschain.NodeReadTimeout", 10);

    /** Listen address */
    private static final String listenAddress = Shareschain.getStringProperty("shareschain.NodeServerHost", "0.0.0.0");

    /** GetInfo message which is sent each time an outbound connection is created */
    private static final NetworkMessage.GetInfoMessage getInfoMessage;

    /** My address */
    private static String myAddress;

    /** My host name */
    private static String myHost;

    /** Announced address */
    static String announcedAddress;

    static {
        try {
            myAddress = Convert.emptyToNull(Shareschain.getStringProperty("shareschain.myAddress"));
            if (myAddress != null) {
                myAddress = myAddress.toLowerCase().trim();
                URI uri = new URI("http://" + myAddress);
                myHost = uri.getHost();
                /* My port */
                int myPort = uri.getPort();
                if (myHost == null) {
                    throw new RuntimeException("shareschain.myAddress is not a valid host address");
                }
                if (myPort == TESTNET_NODE_PORT && !Constants.isTestnet) {
                    throw new RuntimeException("Port " + TESTNET_NODE_PORT + " should only be used for testnet");
                }
                if (Constants.isTestnet) {
                    announcedAddress = myHost;
                } else if (myPort == -1 && serverPort != DEFAULT_NODE_PORT) {
                    announcedAddress = myHost + ":" + serverPort;
                } else if (myPort == DEFAULT_NODE_PORT) {
                    announcedAddress = myHost;
                } else {
                    announcedAddress = myAddress;
                }
            }
        } catch (URISyntaxException e) {
            Logger.logWarningMessage("Your announced address is not valid: " + e.toString());
            myAddress = null;
        }
    }

    /** Network listener instance */
    private static final NetworkHandler listener = new NetworkHandler();

    /** Network listener thread */
    private static Thread listenerThread;

    /** Current number of inbound connections */
    private static final AtomicInteger inboundCount = new AtomicInteger(0);

    /** Current number of outbound connections */
    private static final AtomicInteger outboundCount = new AtomicInteger(0);

    /** Listen channel */
    private static ServerSocketChannel listenChannel;

    /** Network selector */
    private static Selector networkSelector;

    /** Channel register queue */
    private static final ConcurrentLinkedQueue<KeyEvent> keyEventQueue = new ConcurrentLinkedQueue<>();

    /** Connection map */
    static final ConcurrentHashMap<InetAddress, NodeImpl> connectionMap = new ConcurrentHashMap<>();

    /** Network started */
    private static volatile boolean networkStarted = false;

    /** Network shutdown */
    private static volatile boolean networkShutdown = false;

    /**
     * Construct a network handler
     */
    private NetworkHandler() { }

    /**
     * Initialize the network handler
     */
    public static void init() {}

    static {
        //
        // Don't start the network handler if we are offline
        //
        if (! Constants.isOffline) {
            //
            // Create the GetInfo message which is sent when an outbound connection is
            // completed.  The remote node will send its GetInfo message in response.
            //
            if (serverPort == TESTNET_NODE_PORT && !Constants.isTestnet) {
                throw new RuntimeException("Port " + TESTNET_NODE_PORT + " should only be used for testnet");
            }
            String platform = Shareschain.getStringProperty("shareschain.myPlatform",
                    System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            if (platform.length() > Nodes.MAX_PLATFORM_LENGTH) {
                platform = platform.substring(0, Nodes.MAX_PLATFORM_LENGTH);
            }
            if (myAddress != null) {
                try {
                    InetAddress[] myAddrs = InetAddress.getAllByName(myHost);
                    boolean addrValid = false;
                    Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
                    chkAddr:
                    while (intfs.hasMoreElements()) {
                        NetworkInterface intf = intfs.nextElement();
                        List<InterfaceAddress> intfAddrs = intf.getInterfaceAddresses();
                        for (InterfaceAddress intfAddr : intfAddrs) {
                            InetAddress extAddr = intfAddr.getAddress();
                            for (InetAddress myAddr : myAddrs) {
                                if (extAddr.equals(myAddr)) {
                                    addrValid = true;
                                    break chkAddr;
                                }
                            }
                        }
                    }
                    if (!addrValid) {
                        InetAddress extAddr = UPnP.getExternalAddress();
                        if (extAddr != null) {
                            for (InetAddress myAddr : myAddrs) {
                                if (extAddr.equals(myAddr)) {
                                    addrValid = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!addrValid) {
                        Logger.logWarningMessage("Your announced address does not match your external address");
                    }
                } catch (SocketException e) {
                    Logger.logErrorMessage("Unable to enumerate the network interfaces: " + e.toString());
                } catch (UnknownHostException e) {
                    Logger.logWarningMessage("Your announced address is not valid: " + e.toString());
                }
            }
            long services = 0;
            for (Node.Service service : Nodes.myServices) {
                services |= service.getCode();
            }
            String disabledAPIs = null;
            if ((API.isOpenAPI) && !Constants.isLightClient) {
                EnumSet<APIEnum> disabledAPISet = EnumSet.noneOf(APIEnum.class);

                API.disabledAPIs.forEach(apiName -> {
                    APIEnum api = APIEnum.fromName(apiName);
                    if (api != null) {
                        disabledAPISet.add(api);
                    }
                });
                API.disabledAPITags.forEach(apiTag -> {
                    for (APIEnum api : APIEnum.values()) {
                        if (api.getHandler() != null && api.getHandler().getAPITags().contains(apiTag)) {
                            disabledAPISet.add(api);
                        }
                    }
                });
                disabledAPIs = APIEnum.enumSetToBase64String(disabledAPISet);
            }
            if (Constants.isPermissioned && Nodes.nodeSecretPhrase == null) {
                networkShutdown = true;
                throw new RuntimeException("Node credentials not specified for permissioned blockchain");
            }
            getInfoMessage = new NetworkMessage.GetInfoMessage(Shareschain.APPLICATION, Shareschain.VERSION, platform,
                    shareAddress, announcedAddress, API.openAPIPort, API.openAPISSLPort, services,
                    disabledAPIs, API.apiServerIdleTimeout,
                    (Constants.isPermissioned ? Crypto.getPublicKey(Nodes.nodeSecretPhrase) : null));
            try {
                //
                // Create the selector for listening for network events
                //
                networkSelector = Selector.open();
                //
                // Create the listen channel
                //
                listenChannel = ServerSocketChannel.open();
                listenChannel.configureBlocking(false);
                listenChannel.bind(new InetSocketAddress(listenAddress, serverPort), 10);
                //将通道注册进networkSelector 并监听接收事件
                listenChannel.register(networkSelector, SelectionKey.OP_ACCEPT);
            } catch (IOException exc) {
                networkShutdown = true;
                throw new RuntimeException("Unable to create network listener", exc);
            }
            //
            // Start the network handler after server initialization has completed
            //
            ThreadPool.runAfterStart(() -> {
                if (enableNodeUPnP) {
                    UPnP.addPort(serverPort);
                }
                //
                // Start the network listener
                //
                listenerThread = new Thread(listener, "Network Listener");
                listenerThread.setDaemon(true);
                listenerThread.start();
                //
                // Start the message handlers
                //
//                for (int i = 1; i <= 4; i++) {
                for (int i = 1; i <= 1; i++) {
                    MessageHandler handler = new MessageHandler();
                    Thread handlerThread = new Thread(handler, "Message Handler " + i);
                    handlerThread.setDaemon(true);
                    handlerThread.start();
                }
            });
        } else {
            networkShutdown = true;
            getInfoMessage = null;
            Logger.logInfoMessage("Network handler is offline");
        }
    }

    /**
     * Shutdown the network handler
     */
    public static void shutdown() {
        if (!networkShutdown) {
            networkShutdown = true;
            MessageHandler.shutdown();
            if (enableNodeUPnP) {
                UPnP.deletePort(serverPort);
            }
            if (networkSelector != null) {
                wakeup();
            }
        }
    }

    /**
     * Wakes up the network listener
     */
    private static void wakeup() {
        if (Thread.currentThread() != listenerThread) {
            networkSelector.wakeup();
        }
    }

    /**
     * Network listener
     */
    @Override
    public void run() {
        try {
            Logger.logDebugMessage("Network listener started");
            networkStarted = true;
            //
            // Process network events
            //
            while (!networkShutdown) {
                processEvents();
            }
        } catch (RejectedExecutionException exc) {
            Logger.logInfoMessage("Server shutdown started, Network listener stopping");
        } catch (Throwable exc) {
            Logger.logErrorMessage("Network listener abnormally terminated", exc);
            networkShutdown = true;
        }
        networkStarted = false;
        Logger.logDebugMessage("Network listener stopped");
    }

    /**
     * Process network events
     */
    private void processEvents() {
        int count;
        try {
            //
            // Process pending selection key events
            //
            KeyEvent keyEvent;
            while ((keyEvent = keyEventQueue.poll()) != null) {
                keyEvent.process();
            }
            //
            // Process selectable events
            //
            // Note that you need to remove the key from the selected key
            // set.  Otherwise, the selector will return immediately since
            // it thinks there are still unprocessed events.  Also, accessing
            // a key after the channel is closed will cause an exception to be
            // thrown, so it is best to test for just one event at a time for
            // each selection key.
            //
            count = networkSelector.select();
            if (count > 0 && !networkShutdown) {
                Set<SelectionKey> selectedKeys = networkSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext() && !networkShutdown) {
                    SelectionKey key = keyIterator.next();
                    SelectableChannel channel = key.channel();
                    if (channel.isOpen() && key.isValid()) {
                        if (key.isAcceptable())
                            processAccept(key);
                        else if (key.isConnectable())
                            processConnect(key);
                        else if (key.isReadable())
                            processRead(key);
                        else if (key.isWritable())
                            processWrite(key);
                    }
                    keyIterator.remove();
                }
            }
        } catch (CancelledKeyException exc) {
            Logger.logDebugMessage("Network selector key cancelled - retrying", exc);
        } catch (ClosedSelectorException exc) {
            Logger.logErrorMessage("Network selector closed unexpectedly", exc);
            networkShutdown = true;
        } catch (IOException exc) {
            Logger.logErrorMessage("I/O error while processing selection event", exc);
        }
    }

    /**
     * We need to register channels and modify selection keys on the listener thread to
     * avoid deadlocks in the network selector
     */
    static class KeyEvent {

        /** Node */
        private final NodeImpl node;

        /** Socket channel */
        private final SocketChannel channel;

        /** Interest ops to add */
        private int addOps;

        /** Interest ops to remove */
        private int removeOps;

        /** Selection key */
        private SelectionKey key = null;

        /** Cyclic barrier used to wait for event completion */
        private final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);

        /**
         * Construct a KeyEvent
         *
         * @param   node                Node
         * @param   channel             Channel to register
         * @param   initialOps          Initial interest operations
         */
        private KeyEvent(NodeImpl node, SocketChannel channel, int initialOps) {
            this.node = node;
            this.channel = channel;
            this.addOps = initialOps;
        }

        /**
         * Register the channel and wait for completion (called by the thread creating the channel)
         *
         * @return                      Selection key assigned to the channel
         */
        private SelectionKey register() {
            if (Thread.currentThread() == listenerThread) {
                registerChannel();
                return key;
            }
            keyEventQueue.add(this);
            networkSelector.wakeup();
            try {
                cyclicBarrier.await(5, TimeUnit.SECONDS);
            } catch (BrokenBarrierException | InterruptedException | TimeoutException exc) {
                exc.printStackTrace();
                throw new IllegalStateException("Thread interrupted while waiting for key event completion");
            }
            cyclicBarrier.reset();
            return key;
        }

        /**
         * Update the interest operations for the selection key
         *
         * @param   addOps              Operations to be added
         * @param   removeOps           Operations to be removed
         */
        void update(int addOps, int removeOps) {
            if (node.isDisconnectPending()) {
                return;
            }
            if (Thread.currentThread() == listenerThread) {
                if (key.isValid()) {
                    key.interestOps((key.interestOps() | addOps) & (~removeOps));
                }
            } else {
                synchronized(this) {
                    cyclicBarrier.reset();
                    this.addOps = addOps;
                    this.removeOps = removeOps;
                    keyEventQueue.add(this);
                    networkSelector.wakeup();
                    try {
                        cyclicBarrier.await(5, TimeUnit.SECONDS);
                    } catch (BrokenBarrierException | InterruptedException | TimeoutException exc) {
                        throw new IllegalStateException("Thread interrupted while waiting for key event completion");
                    }
                }
            }
        }

        /**
         * Process the key event (called on the listener thread)
         */
        private void process() {
            try {
                if (key == null) {
                    registerChannel();
                } else if (key.isValid()) {
                    key.interestOps((key.interestOps() | addOps) & (~removeOps));
                }
                cyclicBarrier.await(100, TimeUnit.MILLISECONDS);
            } catch (BrokenBarrierException | InterruptedException | TimeoutException exc) {
                Logger.logErrorMessage("Listener thread interrupted while waiting for key event completion");
            }
        }

        /**
         * Register the channel
         */
        private void registerChannel() {
            try {
                key = channel.register(networkSelector, addOps);
                key.attach(node);
                node.setKeyEvent(this);
            } catch (IOException exc) {
                // Ignore - the channel has been closed
            }
        }

        /**
         * Get the selection key
         *
         * @return                      Selection key
         */
        SelectionKey getKey() {
            return key;
        }
    }

    /**
     * Create a new outbound connection
     *
     * @param   node                    Target node
     */
    static void createConnection(NodeImpl node) {
        try {
            InetAddress address = InetAddress.getByName(node.getHost());
            InetSocketAddress remoteAddress = new InetSocketAddress(address, node.getPort());
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.bind(null);
            channel.connect(remoteAddress);
            node.setConnectionAddress(remoteAddress);
            node.setChannel(channel);
            connectionMap.put(address, node);
            //记录outbound数量 加1 ；线程安全处理
            outboundCount.incrementAndGet();
            KeyEvent event = new KeyEvent(node, channel, SelectionKey.OP_CONNECT);
            SelectionKey key = event.register();
            if (key == null) {
                Logger.logErrorMessage("Unable to register socket channel for " + node.getHost());
            }
        } catch (BindException exc) {
            Logger.logErrorMessage("Unable to bind local port: " +
                    (exc.getMessage() != null ? exc.getMessage() : exc.toString()));
        } catch (UnknownHostException exc) {
            Logger.logErrorMessage("Unable to resolve host " + node.getHost() + ": " +
                    (exc.getMessage() != null ? exc.getMessage() : exc.toString()));
        } catch (IOException exc) {
            Logger.logErrorMessage("Unable to open connection to " + node.getHost() + ": " +
                    (exc.getMessage() != null ? exc.getMessage() : exc.toString()));
        }
    }

    /**
     * Process OP_CONNECT event (outbound connect completed)
     *
     * @param   connectKey              Selection key
     */
    private void processConnect(SelectionKey connectKey) {
        //从SelectionKey中获取节点对象
        NodeImpl node = (NodeImpl)connectKey.attachment();
        if (node == null) {
            return;
        }
        //获取channel通道信息
        SocketChannel channel = node.getChannel();
        if (channel == null) {
            return; // Channel has been closed
        }
        try {
            //建立链接
            channel.finishConnect();
            if (node.getState() != Node.State.CONNECTED) {
                KeyEvent keyEvent = node.getKeyEvent();
                if (keyEvent != null) {
                    keyEvent.update(SelectionKey.OP_READ, SelectionKey.OP_CONNECT);
                }
                //向已连接的节点发送当前节点的链路状态信息
                Nodes.nodesService.execute(() -> {
                    node.connectComplete(true);
                    sendGetInfoMessage(node);
                });
            }
        } catch (IOException exc) {
            Nodes.nodesService.execute(() -> node.connectComplete(false));
        }
    }

    /**
     * Process OP_ACCEPT event (inbound connect received)
     *
     * @param   acceptKey               Selection key
     */
    private void processAccept(SelectionKey acceptKey) {
        try {
            SocketChannel channel = listenChannel.accept();
            if (channel != null) {
                InetSocketAddress remoteAddress = (InetSocketAddress)channel.getRemoteAddress();
                String hostAddress = remoteAddress.getAddress().getHostAddress();
                //创建新的节点
                NodeImpl node = Nodes.findOrCreateNode(remoteAddress.getAddress());
                if (node == null) {//节点未连接成功，关闭channel通道，不作处理
                    channel.close();
                    Logger.logDebugMessage("Node not accepted: Connection rejected from " + hostAddress);
                } else if (!Nodes.shouldGetMoreNodes()) {//判断当前节点是否配置，需要获取新的节点，如果不需要关闭channel通道，不作处理
                    channel.close();
                    Logger.logDebugMessage("New nodes are not accepted: Connection rejected from " + hostAddress);
                } else if (inboundCount.get() >= maxInbound) {//判断当前入站节点链接数量是否超出最多连接数，文件配置为2000
                    channel.close();
                    Logger.logDebugMessage("Max inbound connections reached: Connection rejected from " + hostAddress);
                } else if (node.isBlacklisted()) {//判断当前节点是否在黑名单中
                    channel.close();
                    Logger.logDebugMessage("Node is blacklisted: Connection rejected from " + hostAddress);
                } else if (connectionMap.get(remoteAddress.getAddress()) != null) {//判断当前节点是否在connectionMap中，如果在,关闭channel通道,清空新创建的节点信息
                    channel.close();
                    Logger.logDebugMessage("Connection already established with " + hostAddress + ", disconnecting");
                    node.setDisconnectPending();
                    Nodes.nodesService.execute(node::disconnectNode);
                } else {
                    channel.configureBlocking(false);
                    //设置链接地址
                    node.setConnectionAddress(remoteAddress);
                    //设置链接通道
                    node.setChannel(channel);
                    //设置最新的节点更新时间
                    node.setLastUpdated(Shareschain.getEpochTime());
                    //将新的节点信息放入connectionMap中
                    connectionMap.put(remoteAddress.getAddress(), node);
                    //当前入站节点添加1
                    inboundCount.incrementAndGet();
                    //将当前节点添加到Nodes list中
                    Nodes.addNode(node);

                    //?
                    KeyEvent event = new KeyEvent(node, channel, 0);
                    SelectionKey key = event.register();
                    if (key == null) {
                        Logger.logErrorMessage("Unable to register socket channel for " + node.getHost());
                    } else {
                        Nodes.nodesService.execute(node::setInbound);
                    }
                }
            }
        } catch (IOException exc) {
            Logger.logErrorMessage("Unable to accept connection", exc);
            networkShutdown = true;
        }
    }

    /**
     * Process OP_READ event (ready to read data)
     *
     * @param   readKey                 Network selection key
     */
    private void processRead(SelectionKey readKey) {
        NodeImpl node = (NodeImpl)readKey.attachment();
        SocketChannel channel = node.getChannel();
        if (channel == null) {
            return; // Channel has been closed
        }
        ByteBuffer buffer = node.getInputBuffer();
        node.setLastUpdated(Shareschain.getEpochTime());
        try {
            int count;
            //
            // Read data until we have a complete message or no more data is available
            //
            while (true) {
                //
                // Allocate a header buffer if no read is in progress
                //   4-byte magic bytes
                //   4-byte message length (High-order bit set if message is encrypted)
                //
                if (buffer == null) {
                    buffer = ByteBuffer.wrap(new byte[MESSAGE_HEADER_LENGTH]);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    node.setInputBuffer(buffer);
                }
                //
                // Read until buffer is full or there is no more data available
                //
                if (buffer.position() < buffer.limit()) {
                    count = channel.read(buffer);
                    if (count <= 0) {
                        if (count < 0) {
                            Logger.logDebugMessage("Connection with " + node.getHost() + " closed by node");
                            KeyEvent keyEvent = node.getKeyEvent();
                            if (keyEvent != null) {
                                keyEvent.update(0, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                            }
                            node.setDisconnectPending();
                            Nodes.nodesService.execute(node::disconnectNode);
                        }
                        break;
                    }
                    //记录从节点中下载的消息字节个数
                    node.updateDownloadedVolume(count);
                }
                //
                // Process the message header and allocate a new buffer to hold the complete message
                //
                if (buffer.position() == buffer.limit() && buffer.limit() == MESSAGE_HEADER_LENGTH) {
                    buffer.position(0);
                    byte[] hdrBytes = new byte[MESSAGE_HEADER_MAGIC.length];
                    buffer.get(hdrBytes);
                    int msgLength = buffer.getInt();
                    int length = msgLength & 0x7fffffff;
                    if (!Arrays.equals(hdrBytes, MESSAGE_HEADER_MAGIC)) {
                        Logger.logDebugMessage("Incorrect message header received from " + node.getHost());
                        Logger.logDebugMessage("  " + Arrays.toString(hdrBytes));
                        KeyEvent keyEvent = node.getKeyEvent();
                        if (keyEvent != null) {
                            keyEvent.update(0, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        }
                        node.setDisconnectPending();
                        Nodes.nodesService.execute(node::disconnectNode);
                        break;
                    }
                    if (length < 1 || length > MAX_MESSAGE_SIZE + 32) {
                        Logger.logDebugMessage("Message length " + length + " for message from " + node.getHost()
                                + " is not valid");
                        KeyEvent keyEvent = node.getKeyEvent();
                        if (keyEvent != null) {
                            keyEvent.update(0, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        }
                        node.setDisconnectPending();
                        Nodes.nodesService.execute(node::disconnectNode);
                        break;
                    }
                    byte[] msgBytes = new byte[MESSAGE_HEADER_LENGTH + length];
                    buffer = ByteBuffer.wrap(msgBytes);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put(hdrBytes);
                    buffer.putInt(msgLength);
                    node.setInputBuffer(buffer);
                }
                //
                // Queue the message for the message handler if the buffer is full
                //
                // We will disable read operations for this node if it has too many
                // pending messages.  Read operations will be re-enabled once
                // all of the pending messages have been processed.  We do this to keep
                // one node from flooding us with messages.
                //
                if (buffer.position() == buffer.limit()) {
                    node.setInputBuffer(null);
                    buffer.position(MESSAGE_HEADER_LENGTH);
                    int inputCount = node.incrementInputCount();
                    if (inputCount >= MAX_PENDING_MESSAGES) {
                        KeyEvent keyEvent = node.getKeyEvent();
                        if (keyEvent != null) {
                            keyEvent.update(0, SelectionKey.OP_READ);
                        }
                    }
                    MessageHandler.processMessage(node, buffer);
                    break;
                }
            }
        } catch (IOException exc) {
            Logger.logDebugMessage(String.format("%s: Node %s", exc.getMessage(), node.getHost()));
            KeyEvent keyEvent = node.getKeyEvent();
            if (keyEvent != null) {
                keyEvent.update(0, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
            node.setDisconnectPending();
            Nodes.nodesService.execute(node::disconnectNode);
        }
    }

    /**
     * Get the message bytes
     *
     * @param   node                    Node
     * @param   message                 Network message
     * @return                          Serialized message
     */
    static ByteBuffer getMessageBytes(NodeImpl node, NetworkMessage message) {
        ByteBuffer buffer;
        byte[] sessionKey = node.getSessionKey();
        int length = message.getLength();
        if (sessionKey != null) {
            buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH + length + 32);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            message.getBytes(buffer);
            int byteLength = buffer.position();
            byte[] msgBytes = new byte[byteLength];
            buffer.position(0);
            buffer.get(msgBytes);
            byte[] encryptedBytes = Crypto.aesGCMEncrypt(msgBytes, sessionKey);
            buffer.position(0);
            buffer.put(MESSAGE_HEADER_MAGIC);
            buffer.putInt(encryptedBytes.length | 0x80000000);
            buffer.put(encryptedBytes);
        } else {
            buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH + length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(MESSAGE_HEADER_MAGIC);
            buffer.putInt(length);
            message.getBytes(buffer);
        }
        buffer.flip();
        return buffer;
    }

    /**
     * Process OP_WRITE event (ready to write data)
     *
     * @param   writeKey                Network selection key
     */
    private void processWrite(SelectionKey writeKey) {
        NodeImpl node = (NodeImpl)writeKey.attachment();
        SocketChannel channel = node.getChannel();
        if (channel == null) {
            return; // Channel has been closed
        }
        ByteBuffer buffer = node.getOutputBuffer();
        try {
            //
            // Write data until all pending messages have been sent or the socket buffer is full
            //
            while (true) {
                //
                // Get the next message if no write is in progress.  Disable write events
                // if there are no more messages to write.
                //
                if (buffer == null) {
                    buffer = node.getQueuedMessage();
                    if (buffer == null) {
                        KeyEvent keyEvent = node.getKeyEvent();
                        if (keyEvent != null) {
                            keyEvent.update(0, SelectionKey.OP_WRITE);
                        }
                        break;
                    }
                    node.setOutputBuffer(buffer);
                }
                //
                // Write the current buffer to the channel
                //
                int count = channel.write(buffer);
                if (count > 0) {
                    node.updateUploadedVolume(count);
                }
                if (buffer.position() < buffer.limit()) {
                    break;
                }
                buffer = null;
                node.setOutputBuffer(null);
            }
        } catch (IOException exc) {
            Logger.logDebugMessage(String.format("%s: Node %s", exc.getMessage(), node.getHost()));
            KeyEvent keyEvent = node.getKeyEvent();
            if (keyEvent != null) {
                keyEvent.update(0, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
            node.setDisconnectPending();
            Nodes.nodesService.execute(node::disconnectNode);
        }
    }

    /**
     * Close a connection
     *
     * @param   node                    Node connection to close
     */
    static void closeConnection(NodeImpl node) {
        SocketChannel channel = node.getChannel();
        if (channel == null) {
            return;
        }
        try {
            if (node.isInbound()) {
                inboundCount.decrementAndGet();
            } else {
                outboundCount.decrementAndGet();
            }
            connectionMap.remove(node.getConnectionAddress().getAddress());
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException exc) {
            // Ignore since the channel is closed in any event
        }
    }

    /**
     * Send our GetInfo message
     *
     * We will send the default GetInfo message for a non-permissioned blockchain
     *
     * @param   node                    Target node
     */
    static void sendGetInfoMessage(NodeImpl node) {
        getInfoMessage.setBlockchainState(Nodes.getMyBlockchainState());
        node.sendMessage(getInfoMessage);
    }

    /**
     * Send our GetInfo message
     *
     * We will construct a GetInfo message containing the appropriate security
     * token for the target node
     *
     * @param   node                    Target node
     * @param   nodePublicKey           Node public key
     * @param   sessionKey              Session key
     */
    static void sendGetInfoMessage(NodeImpl node, byte[] nodePublicKey, byte[] sessionKey) {
        if (!Constants.isPermissioned) {
            throw new IllegalStateException("Session key not supported");
        }
        NetworkMessage.GetInfoMessage message = new NetworkMessage.GetInfoMessage(
                Shareschain.APPLICATION, Shareschain.VERSION, getInfoMessage.getApplicationPlatform(),
                getInfoMessage.getShareAddress(), getInfoMessage.getAnnouncedAddress(),
                getInfoMessage.getApiPort(), getInfoMessage.getSslPort(), getInfoMessage.getServices(),
                getInfoMessage.getDisabledAPIs(), getInfoMessage.getApiServerIdleTimeout(),
                getInfoMessage.getSecurityToken().getNodePublicKey());
        message.getSecurityToken().setSessionKey(Nodes.nodeSecretPhrase, nodePublicKey, sessionKey);
        node.sendMessage(message);
    }

    /**
     * Check if the network has finished initialization
     *
     * @return                          TRUE if the network is available
     */
    public static boolean isNetworkStarted() {
        return networkStarted;
    }

    /**
     * Broadcast a message to all connected nodes
     *
     * @param   message                 Message to send
     * @return                          number of nodes to which message was sent
     */
    public static int broadcastMessage(NetworkMessage message) {
        return broadcastMessage(null, message);
    }

    /**
     * Broadcast a message to all connected nodes
     *
     * @param   sender                  Message sender or null if our message
     * @param   message                 Message to send
     * @return                          number of nodes to which message was sent
     */
    public static int broadcastMessage(Node sender, NetworkMessage message) {
        if (Constants.isOffline) {
            return 0;
        }
        int n = 0;
        for (Node node : connectionMap.values()) {
            if (node.getState() == Node.State.CONNECTED &&
                    node != sender &&
                    (node.getBlockchainState() != Node.BlockchainState.LIGHT_CLIENT ||
                     message.sendToLightClient())) {
                node.sendMessage(message);
                n += 1;
            }
        }
        wakeup();
        return n;
    }

    /**
     * Get the default node port
     *
     * @return                          Default node port
     */
    public static int getDefaultNodePort() {
        return Constants.isTestnet ? TESTNET_NODE_PORT : DEFAULT_NODE_PORT;
    }

    /**
     * Get the connected node count
     *
     * @return                          Connected node count
     */
    public static int getConnectionCount() {
        return inboundCount.get() + outboundCount.get();
    }

    /**
     * Get the number of inbound connections
     *
     * @return                          Number of inbound connections
     */
    public static int getInboundCount() {
        return inboundCount.get();
    }

    /**
     * Return the maximum number of inbound connections
     *
     * @return                          Number of inbound connections
     */
    public static int getMaxInboundConnections() {
        return maxInbound;
    }

    /**
     * Get the number of outbound connections
     *
     * @return                          Number of outbound connections
     */
    public static int getOutboundCount() {
        return outboundCount.get();
    }

    /**
     * Return the maximum number of outbound connections
     *
     * @return                          Number of outbound connections
     */
    public static int getMaxOutboundConnections() {
        return maxOutbound;
    }
}

package shareschain.node;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.util.crypto.Crypto;
import shareschain.util.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Node message handler
 */
class MessageHandler implements Runnable {

    /** Message queue */
    private static final LinkedBlockingQueue<QueueEntry> messageQueue = new LinkedBlockingQueue<>();

    /** Shutdown started */
    private static volatile boolean messageShutdown = false;

    /**
     * Construct a message handler
     */
    MessageHandler() {
    }

    /**
     * Process a message
     *
     * @param   Node                    Node
     * @param   bytes                   Message bytes
     */
    static void processMessage(NodeImpl Node, ByteBuffer bytes) {
        bytes.position(bytes.position() - 4);
        int msgLength = bytes.getInt();
        messageQueue.offer(new QueueEntry(Node, bytes, (msgLength & 0x80000000) != 0));
    }

    /**
     * Shutdown the message handlers
     */
    static void shutdown() {
        if (!messageShutdown) {
            messageShutdown = true;
            messageQueue.offer(new QueueEntry(null, null, false));
        }
    }

    /**
     * Message handling thread
     */
    @Override
    public void run() {
        Logger.logDebugMessage(Thread.currentThread().getName() + " started");
        try {
            while (true) {

                // LinkedBlockingQueue 在take时如果在头位置的元素不可用时则等待。
                QueueEntry entry = messageQueue.take();
                //
                // During shutdown, discard all pending messages until we reach the shutdown entry.
                // Requeue the shutdown entry to wake up the next message handler so that
                // it can then shutdown.
                //
                if (messageShutdown) {
                    if (entry.getNode() == null) {
                        messageQueue.offer(entry);
                        break;
                    }
                    continue;
                }
                //
                // Process the message
                // 发送消息的节点
                NodeImpl node = entry.getNode();

                // 节点的状态不是已连接则继续处理下个消息
                if (node.getState() != Node.State.CONNECTED) {
                    continue;
                }

                // 节点在处理其它节点发送给自己的消息时需要判断这个节点有没有给自己发送过GetInfo消息，没有则不处理该节点的消息。
                if (node.isHandshakePending() && entry.isEncrypted()) {
                    node.queueInputMessage(entry.getBytes());
                    continue;
                }

                // 开始处理消息
                NetworkMessage message = null;
                NetworkMessage response;
                try {
                    ByteBuffer buffer = entry.getBytes();

                    // 如果消息是加密的，那么先解密
                    if (entry.isEncrypted()) {
                        byte[] sessionKey = node.getSessionKey();
                        if (sessionKey == null) {
                            throw new IllegalStateException("Encrypted message received without a session key");
                        }
                        byte[] encryptedBytes = new byte[buffer.limit() - buffer.position()];
                        buffer.get(encryptedBytes);
                        byte[] msgBytes = Crypto.aesGCMDecrypt(encryptedBytes, sessionKey);
                        buffer = ByteBuffer.wrap(msgBytes);
                        buffer.order(ByteOrder.LITTLE_ENDIAN);
                    }

                    // 根据字节数组构造出消息对象
                    message = NetworkMessage.getMessage(buffer);

                    // 这个地方原来的代码默认是不打印在处理什么消息
                    if (Nodes.isLogLevelEnabled(Nodes.LOG_LEVEL_DETAILS)) {
                        Logger.logDebugMessage(String.format("%s[%d] message received from %s",
                                message.getMessageName(), message.getMessageId(), node.getHost()));
                    }

                    /**
                     * 是否打印日志
                     */
                    if (Nodes.isLogLevelEnabled(Nodes.LOG_LEVEL_NAMES)) {
                        Logger.logDebugMessage(String.format("%s[%d]  message received from %s",
                                message.getMessageName(), message.getMessageId(), node.getHost()));
                    }
                    Logger.logDebugMessage("Does the message needs to be answered : %s",message.isResponse());

                    /**
                     *判断消息是否其它节点回复的
                     */
                    if (message.isResponse()) {
                        if (message.getMessageId() == 0) {
                            Logger.logErrorMessage(message.getMessageName()
                                    + " response message does not have a message identifier");
                        } else {
                            node.completeRequest(message);
                        }
                    } else {

                        // 查看消息是不是支持区块链下载，消息和区块链下载为什么有关系？
                        if (message.downloadNotAllowed()) {

                            // 如果消息是不支持区块链下载的，而此时区块链处理器正在下载区块，那么抛出异常。
                            if (Shareschain.getBlockchainProcessor().isDownloading()) {
                                throw new IllegalStateException(Errors.DOWNLOADING);
                            }
                            if (Constants.isLightClient) {
                                throw new IllegalStateException(Errors.LIGHT_CLIENT);
                            }
                        }
                        response = message.processMessage(node);
                        //判断是否需要回复消息
                        if (message.requiresResponse()) {
                            if (response == null) {
                                Logger.logErrorMessage("No response for " + message.getMessageName() + " message");
                            } else {
                                node.sendMessage(response);
                            }
                        }
                    }
                } catch (NetworkProtocolException exc) {
                    Logger.logDebugMessage("Unable to process message from " + node.getHost() + ": " + exc.getMessage());
                    node.disconnectNode();
                } catch (Exception exc) {
                    String errorMessage = (Nodes.hideErrorDetails ? exc.getClass().getName() :
                            (exc.getMessage() != null ? exc.getMessage() : exc.toString()));
                    boolean severeError;
                    if (exc instanceof IllegalStateException) {
                        severeError = false;
                    } else {
                        severeError = true;
                        Logger.logDebugMessage("Unable to process message from " + node.getHost() + ": " + errorMessage, exc);
                    }
                    if (message != null && message.requiresResponse()) {
                        response = new NetworkMessage.ErrorMessage(message.getMessageId(),
                                severeError, message.getMessageName(), errorMessage);
                        node.sendMessage(response);
                    }
                }
                //
                // Restart reads from the node if the pending messages have been cleared
                //
                if (node.getState() == Node.State.CONNECTED) {
                    int count = node.decrementInputCount();
                    if (count == 0) {
                        try {
                            NetworkHandler.KeyEvent event = node.getKeyEvent();
                            if (event != null && (event.getKey().interestOps() & SelectionKey.OP_READ) == 0) {
                                event.update(SelectionKey.OP_READ, 0);
                            }
                        } catch (IllegalStateException exc) {
                            Logger.logErrorMessage("Unable to update network selection key", exc);
                        }
                    }
                }
            }
        } catch (Throwable exc) {
            Logger.logErrorMessage("Message handler abnormally terminated", exc);
        }
        Logger.logDebugMessage(Thread.currentThread().getName() +  " stopped");
    }

    /**
     * Message queue entry
     */
    private static class QueueEntry {

        /** Node */
        private final NodeImpl Node;

        /** Message buffer */
        private final ByteBuffer bytes;

        /** Message is encrypted */
        private final boolean isEncrypted;

        /**
         * Construct a queue entry
         *
         * @param   Node                Node
         * @param   bytes               Message bytes
         * @param   isEncrypted         TRUE if message is encrypted
         */
        private QueueEntry(NodeImpl Node, ByteBuffer bytes, boolean isEncrypted) {
            this.Node = Node;
            this.bytes = bytes;
            this.isEncrypted = isEncrypted;
        }

        /**
         * Get the node
         *
         * @return                      Node
         */
        private NodeImpl getNode() {
            return Node;
        }

        /**
         * Get the message bytes
         *
         * @return                      Message buffer
         */
        private ByteBuffer getBytes() {
            return bytes;
        }

        /**
         * Check if the message is encrypted
         *
         * @return                      TRUE if the message is encrypted
         */
        private boolean isEncrypted() {
            return isEncrypted;
        }
    }
}

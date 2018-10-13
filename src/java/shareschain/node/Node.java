
package shareschain.node;

import shareschain.network.APIEnum;

import java.util.Set;

/**
 * Node network node
 */
public interface Node {

    /** Node state */
    enum State {
        NON_CONNECTED,                  // Node has never been connected
        CONNECTED,                      // Node is connected
        DISCONNECTED                    // Node is disconnected
    }

    /** Node services */
    enum Service {
        HALLMARK(1),                    // Hallmarked node (no longer used)
        PRUNABLE(2),                    // Stores expired prunable content
        API(4),                         // Provides open API access over http
        API_SSL(8),                     // Provides open API access over https
        CORS(16);                       // API CORS enabled

        private final long code;        // Service code - must be a power of 2

        Service(int code) {
            this.code = code;
        }

        public long getCode() {
            return code;
        }
    }

    enum BlockchainState {
        UP_TO_DATE,
        DOWNLOADING,
        LIGHT_CLIENT,
        FORK
    }

    /**
     * Get the node state
     *
     * @return                          Current state
     */
    State getState();

    /**
     * Get the node host address
     *
     * @return                          Host address
     */
    String getHost();

    /**
     * Get the announced address
     *
     * @return                          Announced address
     */
    String getAnnouncedAddress();

    /**
     * Get the download volume
     *
     * @return                          Download volume
     */
    long getDownloadedVolume();

    /**
     * Get the upload volume
     *
     * @return                          Upload volume
     */
    long getUploadedVolume();

    /**
     * Get the application name
     *
     * @return                          Application name or null
     */
    String getApplication();

    /**
     * Get the application version
     *
     * @return                          Application version or null
     */
    String getVersion();

    /**
     * Get the application platform
     *
     * @return                          Application platform or null
     */
    String getPlatform();

    /**
     * Get the software description as 'name(version)@platform'
     *
     * @return                          Software description
     */
    String getSoftware();

    /**
     * Get the node port
     *
     * @return                          Node port or 0
     */
    int getPort();

    /**
     * Get the open API port
     *
     * @return                          API port or 0
     */
    int getApiPort();

    /**
     * Get the open SSL port
     *
     * @return                          SSL port or 0
     */
    int getApiSSLPort();

    /**
     * Check if address should be shared
     *
     * @return                          TRUE if address should be shared
     */
    boolean shareAddress();

    Set<APIEnum> getDisabledAPIs();

    int getApiServerIdleTimeout();

    BlockchainState getBlockchainState();

    /**
     * Check if node is blacklisted
     *
     * @return                          TRUE if node is blacklisted
     */
    boolean isBlacklisted();

    /**
     * Get the blacklist reason
     *
     * @return                          Blacklist reason
     */
    String getBlacklistingCause();

    /**
     * Connect the node
     */
    void connectNode();

    /**
     * Disconnect the node
     */
    void disconnectNode();

    /**
     * Blacklist the node
     *
     * @param   cause                   Exception causing the blacklist
     */
    void blacklist(Exception cause);

    /**
     * Blacklist the node
     *
     * @param   cause                   Blacklist reason
     */
    void blacklist(String cause);

    /**
     * Unblacklist the node
     */
    void unBlacklist();

    /**
     * Get the time when the last message was received from the node
     *
     * @return                          Epoch time
     */
    int getLastUpdated();

    /**
     * Get the time of the last connect attempt
     *
     * @return                          Epoch time
     */
    int getLastConnectAttempt();

    /**
     * Check if this is an inbound connection
     *
     * @return                          TRUE if this is an inbound connection
     */
    boolean isInbound();

    /**
     * Check if the node provides the specified service
     *
     * @param   service                 Service
     * @return                          TRUE if the service is provided
     */
    boolean providesService(Service service);

    boolean isOpenAPI();

    boolean isApiConnectable();

    StringBuilder getNodeApiUri();

    /**
     * Check if the node provides the specifies services
     *
     * @param   services                Services as a bit map
     * @return                          TRUE if the services are provided
     */
    boolean providesServices(long services);

    /**
     * Send an asynchronous message
     *
     * @param   message                 Network message
     */
    void sendMessage(NetworkMessage message);

    /**
     * Send a request and wait for a response
     *
     * @param   message                 Request message
     * @return                          Response message or null if there is no response
     */
    NetworkMessage sendRequest(NetworkMessage message);
}

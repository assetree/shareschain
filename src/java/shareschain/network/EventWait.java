
package shareschain.network;

import shareschain.network.EventListener.EventListenerException;
import shareschain.network.EventListener.PendingEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>
 * The EventWait API will wait for one of the server events
 * registered by EventRegister.  EventWait will return immediately
 * if one or more events have occurred since the last time EventWait
 * was called.  All pending events will be returned in a single response.
 * The events remain registered so successive calls to EventWait can
 * be made without another call to EventRegister.
 * <p>
 * Request parameters:
 * <ul>
 * <li>token - Event registration token returned by the EventRegister API.
 * <li>timeout - Number of seconds to wait for an event.  The EventWait
 * will complete normally if no event is received within the timeout interval.
 * shareschain.apiEventTimeout will be used if no timeout value is specified or
 * if the requested timeout is greater than shareschain.apiEventTimeout.
 * </ul>
 * <p>
 * Response parameters:
 * <ul>
 * <li>events - An array of event objects
 * </ul>
 * <p>
 * Error Response parameters:
 * <ul>
 * <li>errorCode - API error code
 * <li>errorDescription - API error description
 * </ul>
 * <p>
 * Event object:</p>
 * <ul>
 * <li>name - The event name
 * <li>ids - An array of event object identifiers
 * </ul>
 * <p>
 * Event names:
 * <ul>
 * <li>Block.BLOCK_GENERATED
 * <li>Block.BLOCK_POPPED
 * <li>Block.BLOCK_PUSHED
 * <li>Ledger.ADD_ENTRY.account - Monitor changes to the specified account.  'account'
 * may be the numeric identifier or the Reed-Solomon identifier
 * of the account to monitor for updates.  All accounts will be monitored if no
 * account is specified.
 * Specifying an account identifier of 0 is the same as
 * not specifying an account.
 * <li>Node.ADD_ACTIVE_NODE
 * <li>Node.ADD_NODE
 * <li>Node.BLACKLIST
 * <li>Node.CHANGE_ACTIVE_NODE
 * <li>Node.CHANGE_ANNOUNCED_ADDRESS
 * <li>Node.CHANGE_SERVICES
 * <li>Node.REMOVE_NODE
 * <li>Node.UNBLACKLIST
 * <li>Transaction.ADDED_CONFIRMED_TRANSACTIONS.account (omit account to monitor all accounts)
 * <li>Transaction.ADDED_UNCONFIRMED_TRANSACTIONS.account (omit account to monitor all accounts)
 * <li>Transaction.REJECT_PHASED_TRANSACTION.account (omit account to monitor all accounts)
 * <li>Transaction.RELEASE_PHASED_TRANSACTION.account (omit account to monitor all accounts)
 * <li>Transaction.REMOVED_UNCONFIRMED_TRANSACTIONS.account (omit account to monitor all accounts)
 * </ul>
 * <p>
 * Event object identifiers:
 * <ul>
 * <li>Block string identifier for a Block event
 * <li>Node network address for a Node event
 * <li>Transaction chain and full hash for a Transaction event
 * as a string in the format 'chain:hash'
 * </ul>
 */
public class EventWait extends APIServlet.APIRequestHandler {

    /** EventWait instance */
    static final EventWait instance = new EventWait();

    /** No events registered */
    private static final JSONObject noEventsRegistered = new JSONObject();
    static {
        noEventsRegistered.put("errorCode", 8);
        noEventsRegistered.put("errorDescription", "No events registered");
    }

    /**
     * Create the EventWait instance
     */
    private EventWait() {
        super(new APITag[] {APITag.INFO}, "token", "timeout");
    }

    /**
     * Process the EventWait API request
     *
     * The response will be returned immediately if there are any
     * pending events.  Otherwise, an asynchronous context will
     * be created and the response will be returned after the wait
     * has completed.  By using an asynchronous context, we avoid
     * tying up the Jetty servlet thread while waiting for an event.
     *
     * @param   req                 API request
     * @return                      API response or null
     * @throws ParameterExceptions  Invalid parameter specified
     */
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterExceptions {
        JSONObject response = null;
        //
        // Get the parameters
        //
        long token = ParameterParser.getLong(req, "token", 1, Long.MAX_VALUE, true);
        long timeout = ParameterParser.getLong(req, "timeout", 1, Long.MAX_VALUE, false);
        timeout = (timeout == 0 ? EventListener.eventTimeout : Math.min(timeout, EventListener.eventTimeout));
        //
        // Wait for an event
        //
        if (response == null) {
            String userAddress = req.getRemoteAddr() + ";" + token;
            EventListener listener = EventListener.eventListeners.get(userAddress);
            if (listener == null) {
                response = noEventsRegistered;
            } else {
                try {
                    List<PendingEvent> events = listener.eventWait(req, timeout);
                    if (events != null) {
                        response = formatResponse(events);
                    }
                } catch (EventListenerException exc) {
                    response = new JSONObject();
                    response.put("errorCode", 7);
                    response.put("errorDescription", "Unable to wait for an event: " + exc.getMessage());
                }
            }
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

    /**
     * Format the EventWait response
     *
     * @param   events              Event list
     * @return                      JSON stream
     */
    static JSONObject formatResponse(List<PendingEvent> events) {
        JSONArray eventsJSON = new JSONArray();
        events.forEach(event -> {
            JSONArray idsJSON = new JSONArray();
            if (event.isList())
                idsJSON.addAll(event.getIdList());
            else
                idsJSON.add(event.getId());
            JSONObject eventJSON = new JSONObject();
            eventJSON.put("name", event.getName());
            eventJSON.put("ids", idsJSON);
            eventsJSON.add(eventJSON);
        });
        JSONObject response = new JSONObject();
        response.put("events", eventsJSON);
        return response;
    }
}

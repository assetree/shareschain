
package shareschain.network;

import shareschain.Constants;
import shareschain.util.BooleanExpression;
import shareschain.util.Convert;
import shareschain.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.util.Arrays;
import java.util.List;

public final class JSONResponses {
    public static final JSONStreamAware MISSING_TRANSACTION_BYTES_OR_JSON = missing("transactionBytes", "transactionJSON");
    public static final JSONStreamAware MISSING_ACCOUNT = missing("account");
    public static final JSONStreamAware INCORRECT_ACCOUNT = incorrect("account");
    public static final JSONStreamAware INCORRECT_TIMESTAMP = incorrect("timestamp");
    public static final JSONStreamAware UNKNOWN_ACCOUNT = unknown("account");
    public static final JSONStreamAware UNKNOWN_BLOCK = unknown("block");
    public static final JSONStreamAware INCORRECT_BLOCK = incorrect("block");
    public static final JSONStreamAware MISSING_NODE = missing("node");
    public static final JSONStreamAware UNKNOWN_NODE = unknown("node");
    public static final JSONStreamAware UNKNOWN_TRANSACTION = unknown("transaction");
    public static final JSONStreamAware INCORRECT_TRANSACTION = incorrect("transaction");
    public static final JSONStreamAware INCORRECT_HEIGHT = incorrect("height");
    public static final JSONStreamAware MISSING_HEIGHT = missing("height");
    public static final JSONStreamAware MISSING_ADMIN_PASSWORD = missing("adminPassword");
    public static final JSONStreamAware INCORRECT_ADMIN_PASSWORD = incorrect("adminPassword", "(the specified password does not match shareschain.adminPassword)");
    public static final JSONStreamAware LOCKED_ADMIN_PASSWORD = incorrect("adminPassword", "(locked for 1 hour, too many incorrect password attempts)");
    public static final JSONStreamAware INCORRECT_EC_BLOCK = incorrect("ecBlockId", "ecBlockId does not match the block id at ecBlockHeight");
    public static final JSONStreamAware UNKNOWN_CHAIN = unknown("chain");
    public static final JSONStreamAware MISSING_CHAIN = missing("chain");

    public static final JSONStreamAware NOT_ENOUGH_FUNDS;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Not enough funds");
        NOT_ENOUGH_FUNDS = JSON.prepare(response);
    }

    public static final JSONStreamAware NO_COST_ORDER;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Order value is zero");
        NO_COST_ORDER = JSON.prepare(response);
    }


    public static final JSONStreamAware ERROR_NOT_ALLOWED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 7);
        response.put("errorDescription", "Not allowed");
        ERROR_NOT_ALLOWED = JSON.prepare(response);
    }

    public static final JSONStreamAware ERROR_DISABLED;
    static {
        JSONObject response  = new JSONObject();
        response.put("errorCode", 16);
        response.put("errorDescription", "This API has been disabled");
        ERROR_DISABLED = JSON.prepare(response);
    }

    public static final JSONStreamAware ERROR_INCORRECT_REQUEST;
    static {
        JSONObject response  = new JSONObject();
        response.put("errorCode", 1);
        response.put("errorDescription", "Incorrect request");
        ERROR_INCORRECT_REQUEST = JSON.prepare(response);
    }

    public static final JSONStreamAware NOT_FORGING;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Account is not forging");
        NOT_FORGING = JSON.prepare(response);
    }

    public static final JSONStreamAware POST_REQUIRED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 1);
        response.put("errorDescription", "This request is only accepted using POST!");
        POST_REQUIRED = JSON.prepare(response);
    }

    public static final JSONStreamAware FEATURE_NOT_AVAILABLE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 9);
        response.put("errorDescription", "Feature not available");
        FEATURE_NOT_AVAILABLE = JSON.prepare(response);
    }

    public static final JSONStreamAware DECRYPTION_FAILED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Decryption failed");
        DECRYPTION_FAILED = JSON.prepare(response);
    }

    public static final JSONStreamAware ALREADY_DELIVERED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Purchase already delivered");
        ALREADY_DELIVERED = JSON.prepare(response);
    }

    public static final JSONStreamAware DUPLICATE_REFUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Refund already sent");
        DUPLICATE_REFUND = JSON.prepare(response);
    }

    public static final JSONStreamAware GOODS_NOT_DELIVERED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Goods have not been delivered yet");
        GOODS_NOT_DELIVERED = JSON.prepare(response);
    }

    public static final JSONStreamAware NO_MESSAGE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "No attached message found");
        NO_MESSAGE = JSON.prepare(response);
    }

    public static final JSONStreamAware HEIGHT_NOT_AVAILABLE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Requested height not available");
        HEIGHT_NOT_AVAILABLE = JSON.prepare(response);
    }

    public static final JSONStreamAware NO_PASSWORD_IN_CONFIG;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Administrator's password is not configured. Please set shareschain.adminPassword");
        NO_PASSWORD_IN_CONFIG = JSON.prepare(response);
    }

    public static final JSONStreamAware POLL_RESULTS_NOT_AVAILABLE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Poll results no longer available, set shareschain.processPolls=true and rescan");
        POLL_RESULTS_NOT_AVAILABLE = JSON.prepare(response);
    }

    public static final JSONStreamAware POLL_FINISHED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Poll has already finished");
        POLL_FINISHED = JSON.prepare(response);
    }

    public static final JSONStreamAware PHASING_TRANSACTION_FINISHED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "Phasing transaction has already finished");
        PHASING_TRANSACTION_FINISHED = JSON.prepare(response);
    }

    public static final JSONStreamAware TOO_MANY_PHASING_VOTES;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 10);
        response.put("errorDescription", "Can vote for at most " + Constants.MAX_PHASING_VOTE_TRANSACTIONS + " phased transactions at once");
        TOO_MANY_PHASING_VOTES = JSON.prepare(response);
    }

    public static final JSONStreamAware HASHES_MISMATCH;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 10);
        response.put("errorDescription", "Hashes don't match. You should notify Jeff Garzik.");
        HASHES_MISMATCH = JSON.prepare(response);
    }

    public static final JSONStreamAware REQUIRED_BLOCK_NOT_FOUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 13);
        response.put("errorDescription", "Required block not found in the blockchain");
        REQUIRED_BLOCK_NOT_FOUND = JSON.prepare(response);
    }

    public static final JSONStreamAware REQUIRED_LAST_BLOCK_NOT_FOUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 14);
        response.put("errorDescription", "Current last block is different");
        REQUIRED_LAST_BLOCK_NOT_FOUND = JSON.prepare(response);
    }

    public static final JSONStreamAware MISSING_SECRET_PHRASE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 3);
        response.put("errorDescription", "secretPhrase not specified or not submitted to the remote node");
        MISSING_SECRET_PHRASE = JSON.prepare(response);
    }

    public static final JSONStreamAware PRUNED_TRANSACTION;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 15);
        response.put("errorDescription", "Pruned transaction data not currently available from any node");
        PRUNED_TRANSACTION = JSON.prepare(response);
    }

    public static final JSONStreamAware PROXY_MISSING_REQUEST_TYPE;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 17);
        response.put("errorDescription", "Proxy servlet needs requestType parameter in the URL query");
        PROXY_MISSING_REQUEST_TYPE = JSON.prepare(response);
    }

    public static final JSONStreamAware PROXY_SECRET_DATA_DETECTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 18);
        response.put("errorDescription", "Proxied requests contains secret parameters");
        PROXY_SECRET_DATA_DETECTED = JSON.prepare(response);
    }

    public static final JSONStreamAware API_PROXY_NO_OPEN_API_NODES;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 19);
        response.put("errorDescription", "No openAPI nodes found");
        API_PROXY_NO_OPEN_API_NODES = JSON.prepare(response);
    }

    public static final JSONStreamAware LIGHT_CLIENT_DISABLED_API;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 20);
        response.put("errorDescription", "This API is disabled when running as light client");
        LIGHT_CLIENT_DISABLED_API = JSON.prepare(response);
    }

    public static final JSONStreamAware API_PROXY_NO_PUBLIC_NODES;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 21);
        response.put("errorDescription", "No public nodes found. Please wait while retrying connection to nodes...");
        API_PROXY_NO_PUBLIC_NODES = JSON.prepare(response);
    }

    public static final JSONStreamAware NODE_NOT_CONNECTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Node not connected");
        NODE_NOT_CONNECTED = JSON.prepare(response);
    }

    public static final JSONStreamAware NODE_NOT_OPEN_API;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Node is not providing open API");
        NODE_NOT_OPEN_API = JSON.prepare(response);
    }

    static JSONStreamAware missing(String... paramNames) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 3);
        if (paramNames.length == 1) {
            response.put("errorDescription", "\"" + paramNames[0] + "\"" + " not specified");
        } else {
            response.put("errorDescription", "At least one of " + Arrays.toString(paramNames) + " must be specified");
        }
        return JSON.prepare(response);
    }

    static JSONStreamAware either(String... paramNames) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 6);
        response.put("errorDescription", "Not more than one of " + Arrays.toString(paramNames) + " can be specified");
        return JSON.prepare(response);
    }

    static JSONStreamAware incorrect(String paramName) {
        return incorrect(paramName, null);
    }

    static JSONStreamAware incorrect(String paramName, String details) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 4);
        response.put("errorDescription", "Incorrect \"" + paramName + (details != null ? "\" " + details : "\""));
        return JSON.prepare(response);
    }

    static JSONStreamAware unknown(String objectName) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Unknown " + objectName);
        return JSON.prepare(response);
    }

    static JSONStreamAware unknownAccount(long id) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Unknown account");
        response.put("account", Long.toUnsignedString(id));
        response.put("accountRS", Convert.rsAccount(id));
        return JSON.prepare(response);
    }

    static JSONStreamAware fileNotFound(String objectName) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 10);
        response.put("errorDescription", "File not found " + objectName);
        return JSON.prepare(response);
    }

    static JSONStreamAware error(String error) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 11);
        response.put("errorDescription", error);
        return JSON.prepare(response);
    }

    private static JSONStreamAware responseError(String error) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 12);
        response.put("errorDescription", error);
        return JSON.prepare(response);
    }

    static JSONStreamAware booleanExpressionError(BooleanExpression expression) {
        JSONObject response = new JSONObject();
        BooleanExpression.BadSyntaxException exception = expression.getSyntaxException();
        if (exception != null) {
            response.put("errorCode", 21);
            response.put("errorDescription", "Boolean expression syntax error at position " + exception.getPosition() + ": " + exception.getMessage());
            return JSON.prepare(response);
        } else {
            List<BooleanExpression.SemanticWarning> warnings = expression.getSemanticWarnings();
            if (!warnings.isEmpty()) {
                response.put("errorCode", 22);
                response.put("errorDescription", "Boolean expression not optimal: " + warnings.get(0).getMessage() +
                        (warnings.size() > 1 ? ("; and " + (warnings.size() - 1) + " other warning(s)") : ""));
                JSONArray warningsJson = new JSONArray();
                warnings.forEach(w -> warningsJson.add("At position " + w.getPosition() + ": " + w.getMessage()));
                response.put("semanticWarnings", warningsJson);
                return JSON.prepare(response);
            } else {
                throw new RuntimeException();
            }
        }

    }

    public static final JSONStreamAware MONITOR_ALREADY_STARTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Account monitor already started");
        MONITOR_ALREADY_STARTED = JSON.prepare(response);
    }

    public static final JSONStreamAware MONITOR_NOT_STARTED;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 5);
        response.put("errorDescription", "Account monitor not started");
        MONITOR_NOT_STARTED = JSON.prepare(response);
    }

    public static final JSONStreamAware NO_TRADES_FOUND;
    static {
        JSONObject response = new JSONObject();
        response.put("errorCode", 8);
        response.put("errorDescription", "No trades found");
        NO_TRADES_FOUND = JSON.prepare(response);
    }

    private JSONResponses() {} // never

}

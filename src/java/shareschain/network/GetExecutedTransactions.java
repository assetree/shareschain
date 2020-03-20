
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Transaction;
import shareschain.blockchain.TransactionType;
import shareschain.database.DBIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetExecutedTransactions extends APIServlet.APIRequestHandler {
    static final GetExecutedTransactions instance = new GetExecutedTransactions();

    private GetExecutedTransactions() {
        super(new APITag[] {APITag.TRANSACTIONS}, "height", "numberOfConfirmations", "type", "subtype", "sender", "recipient", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {
        Chain chain = ParameterParser.getChain(req);

        long senderId = ParameterParser.getAccountId(req, "sender", false);

        long recipientId = ParameterParser.getAccountId(req, "recipient", false);

        boolean isChildChain = false;

        byte defaultType = (byte) (isChildChain ? -1 : 1);
        byte type = ParameterParser.getByte(req, "type", isChildChain ? 0 : Byte.MIN_VALUE,
                isChildChain ? Byte.MAX_VALUE : -1, defaultType, false);

        byte subtype = ParameterParser.getByte(req, "subtype", (byte)0, Byte.MAX_VALUE, (byte)-1, false);
        if (type != defaultType && subtype != -1) {
            if (TransactionType.findTransactionType(type, subtype) == null) {
                return JSONResponses.unknown("type");
            }
        }

        int height = ParameterParser.getHeight(req);
        int numberOfConfirmations = ParameterParser.getNumberOfConfirmations(req);

        if (height > 0 && numberOfConfirmations > 0) {
            return JSONResponses.either("height", "numberOfConfirmations");
        }

        if (height <= 0 && senderId == 0 && recipientId == 0) {
            return JSONResponses.missing("sender", "recipient");
        }

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactions = new JSONArray();
        try (DBIterator<? extends Transaction> iterator = Shareschain.getBlockchain().getExecutedTransactions(chain, senderId,
                recipientId, type, subtype, height, numberOfConfirmations, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                Transaction transaction = iterator.next();
                transactions.add(JSONData.transaction(transaction));
            }
        }

        JSONObject response = new JSONObject();
        response.put("transactions", transactions);
        return response;
    }
}

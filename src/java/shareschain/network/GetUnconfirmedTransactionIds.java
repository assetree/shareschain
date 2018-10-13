
package shareschain.network;

import shareschain.Shareschain;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Transaction;
import shareschain.database.DBIterator;
import shareschain.database.FilteringIterator;
import shareschain.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

public final class GetUnconfirmedTransactionIds extends APIServlet.APIRequestHandler {

    static final GetUnconfirmedTransactionIds instance = new GetUnconfirmedTransactionIds();

    private GetUnconfirmedTransactionIds() {
        super(new APITag[] {APITag.TRANSACTIONS, APITag.ACCOUNTS}, "account", "account", "account", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        Chain chain = ParameterParser.getChain(req, false);
        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactionIds = new JSONArray();
        if (accountIds.isEmpty() && chain == null) {
            try (DBIterator<? extends Transaction> transactionsIterator = Shareschain.getTransactionProcessor().getAllUnconfirmedTransactions(firstIndex, lastIndex)) {
                while (transactionsIterator.hasNext()) {
                    Transaction transaction = transactionsIterator.next();
                    transactionIds.add(Long.toUnsignedString(transaction.getId()));
                }
            }
        } else {
            DBIterator<? extends Transaction> dbIterator = chain == null ? Shareschain.getTransactionProcessor().getAllUnconfirmedTransactions(0, -1) :
                    Shareschain.getTransactionProcessor().getUnconfirmedSmcTransactions();
            try (FilteringIterator<? extends Transaction> transactionsIterator = new FilteringIterator<> (
                    dbIterator,
                    transaction -> accountIds.isEmpty() || accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId()),
                    firstIndex, lastIndex)) {
                while (transactionsIterator.hasNext()) {
                    Transaction transaction = transactionsIterator.next();
                    transactionIds.add(Long.toUnsignedString(transaction.getId()));
                }
            }
        }

        JSONObject response = new JSONObject();
        response.put("unconfirmedTransactionIds", transactionIds);
        return response;
    }

}

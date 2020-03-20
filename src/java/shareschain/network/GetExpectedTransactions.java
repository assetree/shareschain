
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Transaction;
import shareschain.util.Convert;
import shareschain.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

public final class GetExpectedTransactions extends APIServlet.APIRequestHandler {

    static final GetExpectedTransactions instance = new GetExpectedTransactions();

    private GetExpectedTransactions() {
        super(new APITag[] {APITag.TRANSACTIONS}, "account", "account", "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {

        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        Chain chain = ParameterParser.getChain(req, false);
        Filter<Transaction> filter = accountIds.isEmpty() && chain == null ?
                transaction -> true
                :
                transaction -> {
                    if (chain != null && transaction.getChain() != chain) {
                        return false;
                    }
                    return accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId());
                };
        List<? extends Transaction> transactions = Shareschain.getBlockchain().getExpectedTransactions(filter);

        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        transactions.forEach(transaction -> jsonArray.add(JSONData.unconfirmedTransaction(transaction)));
        response.put("expectedTransactions", jsonArray);

        return response;
    }

}

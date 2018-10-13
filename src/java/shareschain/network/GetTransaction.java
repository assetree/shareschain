
package shareschain.network;

import shareschain.Shareschain;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Transaction;
import shareschain.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static shareschain.network.JSONResponses.INCORRECT_TRANSACTION;
import static shareschain.network.JSONResponses.UNKNOWN_TRANSACTION;

public final class GetTransaction extends APIServlet.APIRequestHandler {

    static final GetTransaction instance = new GetTransaction();

    private GetTransaction() {
        super(new APITag[] {APITag.TRANSACTIONS}, "fullHash", "includePhasingResult");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        byte[] transactionFullHash = ParameterParser.getBytes(req, "fullHash", true);
        boolean includePhasingResult = "true".equalsIgnoreCase(req.getParameter("includePhasingResult"));
        Chain chain = ParameterParser.getChain(req);

        Transaction transaction;
        try {
            transaction = Shareschain.getBlockchain().getTransaction(chain, transactionFullHash);
            if (transaction != null) {
                return JSONData.transaction(transaction, includePhasingResult);
            }
            transaction = Shareschain.getTransactionProcessor().getUnconfirmedTransaction(Convert.fullHashToId(transactionFullHash));
            if (transaction != null) {
                return JSONData.unconfirmedTransaction(transaction);
            }
            return UNKNOWN_TRANSACTION;
        } catch (RuntimeException e) {
            return INCORRECT_TRANSACTION;
        }
    }

}

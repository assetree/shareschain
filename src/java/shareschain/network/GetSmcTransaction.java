
package shareschain.network;

import shareschain.Shareschain;
import shareschain.blockchain.Mainchain;
import shareschain.blockchain.SmcTransaction;
import shareschain.blockchain.Transaction;
import shareschain.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static shareschain.network.JSONResponses.INCORRECT_TRANSACTION;
import static shareschain.network.JSONResponses.UNKNOWN_TRANSACTION;

public final class GetSmcTransaction extends APIServlet.APIRequestHandler {

    static final GetSmcTransaction instance = new GetSmcTransaction();

    private GetSmcTransaction() {
        super(new APITag[] {APITag.TRANSACTIONS}, "transaction", "fullHash", "includeChildTransactions");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterExceptions {

        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", false);
        if (transactionId == 0) {
            byte[] transactionFullHash = ParameterParser.getBytes(req, "fullHash", true);
            transactionId = Convert.fullHashToId(transactionFullHash);
        }
        boolean includeChildTransactions = "true".equalsIgnoreCase(req.getParameter("includeChildTransactions"));

        SmcTransaction transaction;
        try {
            transaction = Shareschain.getBlockchain().getSmcTransaction(transactionId);
            if (transaction != null) {
                return JSONData.smcTransaction(transaction, includeChildTransactions);
            }
            Transaction unconfirmedTransaction = Shareschain.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
            if (unconfirmedTransaction != null && unconfirmedTransaction.getChain() == Mainchain.mainchain) {
                return JSONData.unconfirmedSmcTransaction((SmcTransaction)unconfirmedTransaction, includeChildTransactions);
            }
            return UNKNOWN_TRANSACTION;
        } catch (RuntimeException e) {
            return INCORRECT_TRANSACTION;
        }
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}

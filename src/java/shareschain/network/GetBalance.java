
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Chain;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetBalance extends APIServlet.APIRequestHandler {

    static final GetBalance instance = new GetBalance();

    private GetBalance() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {
        long accountId = ParameterParser.getAccountId(req, true);
        int height = ParameterParser.getHeight(req);
        Chain chain = ParameterParser.getChain(req);
        Shareschain.getBlockchain().readLock();
        try {
            if (height < 0) {
                height = Shareschain.getBlockchain().getHeight();
            }
            return JSONData.balance(chain, accountId, height);
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
    }

}

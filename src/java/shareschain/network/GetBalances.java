
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Chain;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import java.util.Locale;

import static shareschain.network.JSONResponses.UNKNOWN_CHAIN;

public final class GetBalances extends APIServlet.APIRequestHandler {

    static final GetBalances instance = new GetBalances();

    private GetBalances() {
        super(new APITag[] {APITag.ACCOUNTS}, "chain", "chain", "account", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {
        long accountId = ParameterParser.getAccountId(req, true);
        int height = ParameterParser.getHeight(req);
        Shareschain.getBlockchain().readLock();
        try {
            if (height < 0) {
                height = Shareschain.getBlockchain().getHeight();
            }
            String[] chains = req.getParameterValues("chain");
            if (chains == null || chains.length == 0) {
                return JSONResponses.MISSING_CHAIN;
            }
            JSONObject chainBalances = new JSONObject();
            for (String chainId : chains) {
                Chain chain = Chain.getChain(chainId.toUpperCase(Locale.ROOT));
                if (chain == null) {
                    try {
                        chain = Chain.getChain(Integer.parseInt(chainId));
                    } catch (NumberFormatException ignore) {
                    }
                    if (chain == null) {
                        return UNKNOWN_CHAIN;
                    }
                }
                chainBalances.put(chain.getId(), JSONData.balance(chain, accountId, height));
            }
            JSONObject response = new JSONObject();
            response.put("balances", chainBalances);
            return response;
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
    }

}

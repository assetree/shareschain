
package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetEffectiveBalance extends APIServlet.APIRequestHandler {

    static final GetEffectiveBalance instance = new GetEffectiveBalance();

    private GetEffectiveBalance() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "height");
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
            JSONObject json = new JSONObject();
            Account account = Account.getAccount(accountId, height);
            if (account == null) {
                json.put("forgedBalanceKER", "0");
                json.put("effectiveBalanceSCTK", "0");
                json.put("guaranteedBalanceKER", "0");
            } else {
                json.put("forgedBalanceKER", String.valueOf(account.getForgedBalanceKER()));
                json.put("effectiveBalanceSCTK", account.getEffectiveBalanceSCTK(height));
                json.put("guaranteedBalanceKER", String.valueOf(account.getGuaranteedBalanceKER(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height)));
            }
            return json;
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}

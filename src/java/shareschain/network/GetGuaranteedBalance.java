
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetGuaranteedBalance extends APIServlet.APIRequestHandler {

    static final GetGuaranteedBalance instance = new GetGuaranteedBalance();

    private GetGuaranteedBalance() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.FORGING}, "account", "numberOfConfirmations");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {

        Account account = ParameterParser.getAccount(req);
        int numberOfConfirmations = ParameterParser.getNumberOfConfirmations(req);

        JSONObject response = new JSONObject();
        if (account == null) {
            response.put("guaranteedBalanceKER", "0");
        } else {
            response.put("guaranteedBalanceKER", String.valueOf(account.getGuaranteedBalanceKER(numberOfConfirmations, Shareschain.getBlockchain().getHeight())));
        }

        return response;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}

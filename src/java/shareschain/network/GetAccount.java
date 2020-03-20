
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import shareschain.database.DBIterator;
import shareschain.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccount extends APIServlet.APIRequestHandler {

    static final GetAccount instance = new GetAccount();

    private GetAccount() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "includeLessors", "includeAssets", "includeCurrencies", "includeEffectiveBalance");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {

        boolean includeLessors = "true".equalsIgnoreCase(req.getParameter("includeLessors"));
        boolean includeAssets = "true".equalsIgnoreCase(req.getParameter("includeAssets"));
        boolean includeCurrencies = "true".equalsIgnoreCase(req.getParameter("includeCurrencies"));
        boolean includeEffectiveBalance = "true".equalsIgnoreCase(req.getParameter("includeEffectiveBalance"));
        Shareschain.getBlockchain().readLock();
        try {
            long accountId = ParameterParser.getAccountId(req, true);
            Account account = Account.getAccount(accountId);
            if (account == null) {
                return JSONResponses.unknownAccount(accountId);
            }
            JSONObject response = new JSONObject();
            JSONData.putAccount(response, "account", account.getId());
            response.put("forgedBalanceKER", String.valueOf(account.getForgedBalanceKER()));
            if (includeEffectiveBalance) {
                response.put("effectiveBalanceSCTK", account.getEffectiveBalanceSCTK());
                response.put("guaranteedBalanceKER", String.valueOf(account.getGuaranteedBalanceKER()));
            }
            byte[] publicKey = Account.getPublicKey(account.getId());
            if (publicKey != null) {
                response.put("publicKey", Convert.toHexString(publicKey));
            }
            Account.AccountInfo accountInfo = account.getAccountInfo();
            if (accountInfo != null) {
                response.put("name", Convert.nullToEmpty(accountInfo.getName()));
                response.put("description", Convert.nullToEmpty(accountInfo.getDescription()));
            }
            Account.AccountLease accountLease = account.getAccountLease();
            if (accountLease != null) {
                JSONData.putAccount(response, "currentLessee", accountLease.getCurrentLesseeId());
                response.put("currentLeasingHeightFrom", accountLease.getCurrentLeasingHeightFrom());
                response.put("currentLeasingHeightTo", accountLease.getCurrentLeasingHeightTo());
                if (accountLease.getNextLesseeId() != 0) {
                    JSONData.putAccount(response, "nextLessee", accountLease.getNextLesseeId());
                    response.put("nextLeasingHeightFrom", accountLease.getNextLeasingHeightFrom());
                    response.put("nextLeasingHeightTo", accountLease.getNextLeasingHeightTo());
                }
            }

            if (!account.getControls().isEmpty()) {
                JSONArray accountControlsJson = new JSONArray();
                account.getControls().forEach(accountControl -> accountControlsJson.add(accountControl.toString()));
                response.put("accountControls", accountControlsJson);
            }

            if (includeLessors) {
                try (DBIterator<Account> lessors = account.getLessors()) {
                    if (lessors.hasNext()) {
                        JSONArray lessorIds = new JSONArray();
                        JSONArray lessorIdsRS = new JSONArray();
                        JSONArray lessorInfo = new JSONArray();
                        while (lessors.hasNext()) {
                            Account lessor = lessors.next();
                            lessorIds.add(Long.toUnsignedString(lessor.getId()));
                            lessorIdsRS.add(Convert.rsAccount(lessor.getId()));
                            lessorInfo.add(JSONData.lessor(lessor, includeEffectiveBalance));
                        }
                        response.put("lessors", lessorIds);
                        response.put("lessorsRS", lessorIdsRS);
                        response.put("lessorsInfo", lessorInfo);
                    }
                }
            }
            return response;
        } finally {
            Shareschain.getBlockchain().readUnlock();
        }

    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}

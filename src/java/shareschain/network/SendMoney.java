
package shareschain.network;

import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import shareschain.account.PaymentSmcAttachment;
import shareschain.blockchain.Attachment;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SendMoney extends CreateTransaction {

    static final SendMoney instance = new SendMoney();

    private SendMoney() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "recipient", "amountKER");
    }

    /**
     * 接收前台发送交易的接口
     * @param req
     * @return
     * @throws ShareschainExceptions
     */
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {
        long recipient = ParameterParser.getAccountId(req, "recipient", true);
        //amountKER表示转账金额
        long amountKER = ParameterParser.getAmountKER(req);
        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment = PaymentSmcAttachment.INSTANCE;
        return createTransaction(req, account, recipient, amountKER, attachment);
    }

}

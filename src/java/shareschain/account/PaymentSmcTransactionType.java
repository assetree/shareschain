
package shareschain.account;

import shareschain.Constants;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Attachment;
import shareschain.blockchain.SmcTransactionImpl;
import shareschain.blockchain.SmcTransactionType;
import shareschain.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public abstract class PaymentSmcTransactionType extends SmcTransactionType {

    private PaymentSmcTransactionType() {
    }

    @Override
    public final byte getType() {
        return SmcTransactionType.TYPE_PAYMENT;
    }

    @Override
    protected final boolean applyAttachmentUnconfirmed(SmcTransactionImpl transaction, Account senderAccount) {
        return true;
    }

    @Override
    protected final void applyAttachment(SmcTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
    }

    @Override
    protected final void undoAttachmentUnconfirmed(SmcTransactionImpl transaction, Account senderAccount) {
    }

    @Override
    public final boolean canHaveRecipient() {
        return true;
    }

    public static final TransactionType ORDINARY = new PaymentSmcTransactionType() {

        @Override
        public final byte getSubtype() {
            return SmcTransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
        }

        @Override
        public final AccountChainLedger.LedgerEvent getLedgerEvent() {
            return AccountChainLedger.LedgerEvent.SMC_PAYMENT;
        }

        @Override
        public String getName() {
            return "SmcPayment";
        }

        @Override
        public Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer) {
            return PaymentSmcAttachment.INSTANCE;
        }

        @Override
        public Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) {
            return PaymentSmcAttachment.INSTANCE;
        }

        /**
         * 验证交易的数量是否合法，是否大于允许的最大值
         * @param transaction
         * @throws ShareschainExceptions.ValidationExceptions
         */
        @Override
        protected void validateAttachment(SmcTransactionImpl transaction) throws ShareschainExceptions.ValidationExceptions {
            if (transaction.getAmount() <= 0 || transaction.getAmount() >= Constants.MAX_BALANCE_KER) {
                throw new ShareschainExceptions.NotValidExceptions("Invalid ordinary payment");
            }
        }

    };

}

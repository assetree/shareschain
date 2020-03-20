
package shareschain.account;

import shareschain.Constants;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.*;
import shareschain.blockchain.SmcTransactionImpl;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public abstract class AccountControlSmcTransactionType extends SmcTransactionType {

    private AccountControlSmcTransactionType() {
    }

    @Override
    public final byte getType() {
        return SmcTransactionType.TYPE_ACCOUNT_CONTROL;
    }

    @Override
    protected final boolean applyAttachmentUnconfirmed(SmcTransactionImpl transaction, Account senderAccount) {
        return true;
    }

    @Override
    protected final void undoAttachmentUnconfirmed(SmcTransactionImpl transaction, Account senderAccount) {
    }

    public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControlSmcTransactionType() {

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return new Fee.ConstantFee(Constants.KER_PER_SCTK / 10);
        }

        @Override
        public final byte getSubtype() {
            return SmcTransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
        }

        @Override
        public AccountChainLedger.LedgerEvent getLedgerEvent() {
            return AccountChainLedger.LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
        }

        @Override
        public String getName() {
            return "EffectiveBalanceLeasing";
        }

        @Override
        public EffectiveBalanceLeasingAttachment parseAttachment(ByteBuffer buffer) {
            return new EffectiveBalanceLeasingAttachment(buffer);
        }

        @Override
        public EffectiveBalanceLeasingAttachment parseAttachment(JSONObject attachmentData) {
            return new EffectiveBalanceLeasingAttachment(attachmentData);
        }

        @Override
        protected void applyAttachment(SmcTransactionImpl transaction, Account senderAccount, Account recipientAccount) {
            EffectiveBalanceLeasingAttachment attachment = (EffectiveBalanceLeasingAttachment) transaction.getAttachment();
            Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
        }

        @Override
        protected void validateAttachment(SmcTransactionImpl transaction) throws ShareschainExceptions.ValidationExceptions {
            EffectiveBalanceLeasingAttachment attachment = (EffectiveBalanceLeasingAttachment) transaction.getAttachment();
            if (transaction.getSenderId() == transaction.getRecipientId()) {
                throw new ShareschainExceptions.NotValidExceptions("Account cannot lease balance to itself");
            }
            if (transaction.getAmount() != 0) {
                throw new ShareschainExceptions.NotValidExceptions("Transaction amount must be 0 for effective balance leasing");
            }
            if (attachment.getPeriod() < Constants.LEASING_DELAY || attachment.getPeriod() > 65535) {
                throw new ShareschainExceptions.NotValidExceptions("Invalid effective balance leasing period: " + attachment.getPeriod());
            }
            byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
            if (recipientPublicKey == null) {
                throw new ShareschainExceptions.NotCurrentlyValidExceptions("Invalid effective balance leasing: "
                        + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
            }
        }

        @Override
        public boolean canHaveRecipient() {
            return true;
        }

    };
}

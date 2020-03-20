
package shareschain.blockchain;

import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public interface Attachment extends Appendix {

    TransactionType getTransactionType();

    abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

        protected AbstractAttachment(ByteBuffer buffer) {
            super(buffer);
        }

        protected AbstractAttachment(JSONObject attachmentData) {
            super(attachmentData);
        }

        protected AbstractAttachment(int version) {
            super(version);
        }

        protected AbstractAttachment() {}

        @Override
        public final int getAppendixType() {
            return 0;
        }

        @Override
        public final String getAppendixName() {
            return getTransactionType().getName();
        }

        @Override
        public final void validate(Transaction transaction) throws ShareschainExceptions.ValidationExceptions {
            getTransactionType().validateAttachment(transaction);
        }

        @Override
        public final void validateId(Transaction transaction) throws ShareschainExceptions.ValidationExceptions {
            getTransactionType().validateId(transaction);
        }

        @Override
        public final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
        }

        @Override
        public final Fee getBaselineFee(Transaction transaction) {
            return getTransactionType().getBaselineFee(transaction);
        }

        @Override
        public final Fee getNextFee(Transaction transaction) {
            return getTransactionType().getNextFee(transaction);
        }

        @Override
        public final int getBaselineFeeHeight() {
            return getTransactionType().getBaselineFeeHeight();
        }

        @Override
        public final int getNextFeeHeight() {
            return getTransactionType().getNextFeeHeight();
        }

        @Override
        public final boolean isAllowed(Chain chain) {
            return chain.isAllowed(getTransactionType());
        }

    }

    abstract class EmptyAttachment extends AbstractAttachment {

        protected EmptyAttachment() {
            super(0);
        }

        @Override
        protected final int getMySize() {
            return 0;
        }

        @Override
        protected final void putMyBytes(ByteBuffer buffer) {
        }

        @Override
        protected final void putMyJSON(JSONObject json) {
        }

        @Override
        public final boolean verifyVersion() {
            return getVersion() == 0;
        }

    }

}

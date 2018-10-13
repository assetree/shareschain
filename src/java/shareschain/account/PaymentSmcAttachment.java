
package shareschain.account;

import shareschain.blockchain.Attachment;
import shareschain.blockchain.TransactionType;

public final class PaymentSmcAttachment extends Attachment.EmptyAttachment {

    public static final PaymentSmcAttachment INSTANCE = new PaymentSmcAttachment();

    @Override
    public TransactionType getTransactionType() {
        return PaymentSmcTransactionType.ORDINARY;
    }

}

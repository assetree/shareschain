
package shareschain.blockchain;

import shareschain.ShareschainException;
import shareschain.account.Account;
import shareschain.account.AccountControlSmcTransactionType;
import shareschain.account.AccountLedger;
import shareschain.account.PaymentSmcTransactionType;

public abstract class SmcTransactionType extends TransactionType {

    protected static final byte TYPE_PAYMENT = -2;//支付交易
    protected static final byte TYPE_ACCOUNT_CONTROL = -3;//账户控制


    protected static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    protected static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return PaymentSmcTransactionType.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return AccountControlSmcTransactionType.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    public Fee getBaselineFee(Transaction transaction) {
        return Fee.DEFAULT_SCTK_FEE;
    }

    /**
     * 更新发送者的未确认余额表
     * 交易发送者的未确认的余额小于(当前交易的花费+转账金额)返回false
     * 更新余额表（PUBLIC.BALANCE_SCTK）中发送者的未确认余额unconfirmed_balance字段值
     * @param transaction
     * @param senderAccount
     * @return
     */
    @Override
    public final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amount = transaction.getAmount();
        long fee = transaction.getFee();
        long totalAmount = Math.addExact(amount, fee);
        if (Mainchain.mainchain.getBalanceHome().getBalance(senderAccount.getId()).getUnconfirmedBalance() < totalAmount) {
            return false;
        }

        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(transaction);
        //更新余额表中的未确认余额unconfirmed_balance字段值
        senderAccount.addToUnconfirmedBalance(Mainchain.mainchain, getLedgerEvent(), eventId, -amount, -fee);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {

            // 做的修改回滚
            senderAccount.addToUnconfirmedBalance(Mainchain.mainchain, getLedgerEvent(), eventId, amount, fee);
            return false;
        }
        return true;
    }

    /**
     * 更新交易相关余额信息
     * 1.更新发送者账号余额表
     * 2.更新接收人账户余额与未确认余额
     * 3.更新接收人的保证余额
     * @param transaction
     * @param senderAccount
     * @param recipientAccount
     */
    @Override
    public final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        long amount = transaction.getAmount();
        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(transaction);
        //if (!transaction.attachmentIsPhased()) {
        senderAccount.addToBalance(Mainchain.mainchain, getLedgerEvent(), eventId, -amount, -transaction.getFee());
        /* never phased
        } else {
            senderAccount.addToBalanceKER(getLedgerEvent(), transactionId, -amount);
        }
        */
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalance(Mainchain.mainchain, getLedgerEvent(), eventId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    @Override
    public final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalance(Mainchain.mainchain, getLedgerEvent(), AccountLedger.newEventId(transaction),
                transaction.getAmount(), transaction.getFee());
    }

    @Override
    public final void validateAttachment(Transaction transaction) throws ShareschainException.ValidationException {
        validateAttachment((SmcTransactionImpl)transaction);
    }

    protected abstract void validateAttachment(SmcTransactionImpl transaction) throws ShareschainException.ValidationException;

    @Override
    protected void validateId(Transaction transaction) throws ShareschainException.ValidationException {
        validateId((SmcTransactionImpl)transaction);
    }

    protected void validateId(SmcTransactionImpl transaction) throws ShareschainException.ValidationException {
    }

    @Override
    public final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        return applyAttachmentUnconfirmed((SmcTransactionImpl)transaction, senderAccount);
    }

    protected abstract boolean applyAttachmentUnconfirmed(SmcTransactionImpl transaction, Account senderAccount);

    @Override
    public final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
        applyAttachment((SmcTransactionImpl)transaction, senderAccount, recipientAccount);
    }

    protected abstract void applyAttachment(SmcTransactionImpl transaction, Account senderAccount, Account recipientAccount);

    @Override
    public final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        undoAttachmentUnconfirmed((SmcTransactionImpl)transaction, senderAccount);
    }

    protected abstract void undoAttachmentUnconfirmed(SmcTransactionImpl transaction, Account senderAccount);

    @Override
    public final boolean isPhasingSafe() {
        return true;
    }

    @Override
    public final boolean isGlobal() {
        return true;
    }


}

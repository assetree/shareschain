
package shareschain.blockchain;

import shareschain.ShareschainExceptions;

import java.sql.ResultSet;
import java.sql.SQLException;

final class UnconfirmedSmcTransaction extends UnconfirmedTransaction implements SmcTransaction {

    UnconfirmedSmcTransaction(SmcTransactionImpl transaction, long arrivalTimestamp) {
        super(transaction, arrivalTimestamp, true);
    }

    UnconfirmedSmcTransaction(ResultSet rs) throws SQLException, ShareschainExceptions.NotValidExceptions {
        super(TransactionImpl.newTransactionBuilder(rs.getBytes("transaction_bytes")), rs);
    }

    @Override
    public SmcTransactionImpl getTransaction() {
        return (SmcTransactionImpl)super.getTransaction();
    }

    @Override
    public void validate() throws ShareschainExceptions.ValidationExceptions {
        super.validate();
    }
}

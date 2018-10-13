
package shareschain.blockchain;

import shareschain.ShareschainException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

final class UnconfirmedSmcTransaction extends UnconfirmedTransaction implements SmcTransaction {

    UnconfirmedSmcTransaction(SmcTransactionImpl transaction, long arrivalTimestamp) {
        super(transaction, arrivalTimestamp, true);
    }

    UnconfirmedSmcTransaction(ResultSet rs) throws SQLException, ShareschainException.NotValidException {
        super(TransactionImpl.newTransactionBuilder(rs.getBytes("transaction_bytes")), rs);
    }

    @Override
    public SmcTransactionImpl getTransaction() {
        return (SmcTransactionImpl)super.getTransaction();
    }

    @Override
    public void validate() throws ShareschainException.ValidationException {
        super.validate();
    }
}

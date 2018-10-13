
package shareschain.blockchain;

import shareschain.ShareschainException;

public interface SmcTransaction extends Transaction {

    interface Builder extends Transaction.Builder {

        SmcTransaction build() throws ShareschainException.NotValidException;

        SmcTransaction build(String secretPhrase) throws ShareschainException.NotValidException;

    }

}


package shareschain.blockchain;

import shareschain.ShareschainExceptions;

public interface SmcTransaction extends Transaction {

    interface Builder extends Transaction.Builder {

        SmcTransaction build() throws ShareschainExceptions.NotValidExceptions;

        SmcTransaction build(String secretPhrase) throws ShareschainExceptions.NotValidExceptions;

    }

}

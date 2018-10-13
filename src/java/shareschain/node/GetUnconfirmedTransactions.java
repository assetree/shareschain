
package shareschain.node;

import shareschain.Shareschain;
import shareschain.blockchain.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

final class GetUnconfirmedTransactions {

    private GetUnconfirmedTransactions() {}

    /**
     * Process the GetUnconfirmedTransactions message and return the Transactions message.
     * The request contains a list of unconfirmed transactions to exclude.
     *
     * A maximum of 100 unconfirmed transactions will be returned.
     *
     * @param   Node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl Node, NetworkMessage.GetUnconfirmedTransactionsMessage request) {
        List<Long> exclude = request.getExclusions();
        SortedSet<? extends Transaction> transactionSet = Shareschain.getTransactionProcessor().getCachedUnconfirmedTransactions(exclude);
        List<Transaction> transactions = new ArrayList<>(Math.min(100, transactionSet.size()));
        for (Transaction transaction : transactionSet) {
            transactions.add(transaction);
            if (transactions.size() >= 100) {
                break;
            }
        }
        return new NetworkMessage.TransactionsMessage(request.getMessageId(), transactions);
    }
}

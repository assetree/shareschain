
package shareschain.blockchain;

import shareschain.ShareschainExceptions;
import shareschain.database.DBIterator;
import shareschain.util.Observable;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

public interface TransactionProcessor extends Observable<List<? extends Transaction>,TransactionProcessor.Event> {

    enum Event {
        REMOVED_UNCONFIRMED_TRANSACTIONS,
        ADDED_UNCONFIRMED_TRANSACTIONS,
        ADDED_CONFIRMED_TRANSACTIONS,
        RELEASE_PHASED_TRANSACTION,
        REJECT_PHASED_TRANSACTION
    }

    List<Long> getAllUnconfirmedTransactionIds();
    
    DBIterator<? extends Transaction> getAllUnconfirmedTransactions();

    DBIterator<? extends Transaction> getAllUnconfirmedTransactions(int from, int to);

    DBIterator<? extends Transaction> getAllUnconfirmedTransactions(String sort);

    DBIterator<? extends Transaction> getAllUnconfirmedTransactions(int from, int to, String sort);

    DBIterator<? extends Transaction> getUnconfirmedSmcTransactions();


    UnconfirmedTransaction getUnconfirmedTransaction(long transactionId);

    UnconfirmedTransaction[] getAllWaitingTransactions();

    Transaction[] getAllBroadcastedTransactions();

    void clearUnconfirmedTransactions();

    void requeueAllUnconfirmedTransactions();

    void rebroadcastAllUnconfirmedTransactions();

    void broadcast(Transaction transaction) throws ShareschainExceptions.ValidationExceptions;

    void broadcastLater(Transaction transaction);

    List<? extends Transaction> processNodeTransactions(List<Transaction> transactions) throws ShareschainExceptions.NotValidExceptions;

    void processLater(Collection<? extends SmcTransaction> transactions);

    SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(List<Long> exclude);

}

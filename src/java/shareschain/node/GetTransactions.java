
package shareschain.node;

import shareschain.Shareschain;
import shareschain.blockchain.ChainTransactionId;
import shareschain.blockchain.Transaction;

import java.util.ArrayList;
import java.util.List;

final class GetTransactions {

    private GetTransactions() {}

    /**
     * Process the GetTransactions message and return the Transactions message.
     * The request consists of a list of transactions to return.
     * 请求处理 获取交易的消息返回一个交易的列表
     *
     * A maximum of 100 transactions can be requested.
     *
     * @param   Node                    Node
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(NodeImpl Node, NetworkMessage.GetTransactionsMessage request) {
        List<ChainTransactionId> transactionIds = request.getTransactionIds();
        if (transactionIds.size() > 100) {
            throw new IllegalArgumentException(Errors.TOO_MANY_TRANSACTIONS_REQUESTED);
        }
        List<Transaction> transactions = new ArrayList<>(transactionIds.size());
        for (ChainTransactionId transactionId : transactionIds) {
            //first check the transaction inventory
            Transaction transaction = TransactionsInventory.getCachedTransaction(transactionId);
            if (transaction == null) {
                //check the unconfirmed pool
                transaction = Shareschain.getTransactionProcessor().getUnconfirmedTransaction(transactionId.getTransactionId());
            }
            if (transaction == null) {
                //check the blockchain
                transaction = transactionId.getTransaction();
            }
            if (transaction != null) {
                transaction.getAppendages(true);
                transactions.add(transaction);
            }
        }
        return new NetworkMessage.TransactionsMessage(request.getMessageId(), transactions);
    }
}

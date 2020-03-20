
package shareschain.blockchain;

import shareschain.ShareschainExceptions;
import shareschain.database.DerivedDBTable;
import shareschain.node.Node;
import shareschain.util.JSON;
import shareschain.util.Observable;

import java.util.List;

public interface BlockchainProcessor extends Observable<Block,BlockchainProcessor.Event> {

    enum Event {
        BLOCK_PUSHED, BLOCK_POPPED, BLOCK_GENERATED, BLOCK_SCANNED,
        RESCAN_BEGIN, RESCAN_END,
        BEFORE_BLOCK_ACCEPT, AFTER_BLOCK_ACCEPT,
        BEFORE_BLOCK_APPLY, AFTER_BLOCK_APPLY
    }

    Node getLastBlockchainFeeder();

    int getLastBlockchainFeederHeight();

    boolean isScanning();

    boolean isDownloading();

    boolean isProcessingBlock();

    void suspendDownload(boolean suspend);

    boolean isDownloadSuspended();

    int getMinRollbackHeight();

    int getInitialScanHeight();

    void processNodeBlock(Block inputBlock) throws ShareschainExceptions;

    void processNodeBlocks(List<Block> inputBlocks) throws ShareschainExceptions;

    void fullReset();

    void scan(int height, boolean validate);

    void fullScanWithShutdown();

    void setGetMoreBlocks(boolean getMoreBlocks);

    List<? extends Block> popOffTo(int height);

    void registerDerivedTable(DerivedDBTable table);

    void trimDerivedTables();

    long getGenesisBlockId();

    class BlockNotAcceptedExceptions extends ShareschainExceptions {

        private final BlockImpl block;

        public BlockNotAcceptedExceptions(String message, BlockImpl block) {
            super(message);
            this.block = block;
        }

        public BlockNotAcceptedExceptions(Throwable cause, BlockImpl block) {
            super(cause);
            this.block = block;
        }

        @Override
        public String getMessage() {
            return block == null ? super.getMessage() : super.getMessage() + ", block " + block.getStringId() + " " + block.toString();
        }

    }

    class TransactionNotAcceptedExceptions extends BlockNotAcceptedExceptions {

        private final TransactionImpl transaction;

        TransactionNotAcceptedExceptions(String message, TransactionImpl transaction) {
            super(message, transaction.getBlock());
            this.transaction = transaction;
        }

        TransactionNotAcceptedExceptions(Throwable cause, TransactionImpl transaction) {
            super(cause, transaction.getBlock());
            this.transaction = transaction;
        }

        TransactionImpl getTransaction() {
            return transaction;
        }

        @Override
        public String getMessage() {
            return "Invalid transaction " + transaction.getStringId() + " " + JSON.toJSONString(transaction.getJSONObject())
                    + ",\n" + super.getMessage();
        }
    }

    class BlockOutOfOrderExceptions extends BlockNotAcceptedExceptions {

        public BlockOutOfOrderExceptions(String message, BlockImpl block) {
            super(message, block);
        }

	}

	class BlockOfLowerDifficultyExceptions extends BlockNotAcceptedExceptions {

        public BlockOfLowerDifficultyExceptions(BlockImpl block) {
            super("Lower cumulative difficulty", block);
        }

    }

}


package shareschain.blockchain;

import shareschain.Constants;
import shareschain.ShareschainException;
import shareschain.account.Account;
import shareschain.account.AccountLedger;
import shareschain.account.AccountLedger.LedgerEvent;
import shareschain.util.crypto.Crypto;
import shareschain.util.Convert;
import shareschain.util.JSON;
import shareschain.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BlockImpl implements Block {

    /**
     * 版本号
     */
    private final int version;
    /**
     * 时间戳
     */
    private final int timestamp;
    /**
     * 上级区块编号
     */
    private final long previousBlockId;
    /**
     * generator 公钥信息
     */
    private volatile byte[] generatorPublicKey;
    /**
     * 上级区块hash值
     */
    private final byte[] previousBlockHash;
    /**
     * 总交易金额
     */
    private final long totalFeeKER;
    /**
     * 签名
     */
    private final byte[] generationSignature;

    private final byte[] payloadHash;
    /**
     * 交易列表
     */
    private volatile List<SmcTransactionImpl> blockTransactions;

    /**
     * 区块签名
     */
    private byte[] blockSignature;
    /**
     * 累积难度值
     */
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    /**
     * 基本目标值
     */
    private long baseTarget = Constants.INITIAL_BASE_TARGET;
    /**
     * 下级区块编号
     */
    private volatile long nextBlockId;
    /**
     * 区块高度
     */
    private int height = -1;

    private volatile long id;
    private volatile String stringId = null;
    /**
     * 创造者标号
     */
    private volatile long generatorId;
    private volatile byte[] bytes = null;

    private BlockImpl(int version, int timestamp, long previousBlockId, long totalFeeKER, byte[] payloadHash,
                      byte[] generatorPublicKey, byte[] generationSignature, byte[] blockSignature, byte[] previousBlockHash, List<SmcTransactionImpl> transactions) {
        this.version = version;
        this.timestamp = timestamp;
        this.previousBlockId = previousBlockId;
        this.totalFeeKER = totalFeeKER;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        if (transactions != null) {
            this.blockTransactions = Collections.unmodifiableList(transactions);
        }
    }

    //genesis block only
    BlockImpl(byte[] generationSignature) {
        this(-1, 0, 0, 0, new byte[32],
                new byte[32], generationSignature, new byte[64], new byte[32], Collections.emptyList());
        this.height = 0;
        if (Constants.isTestnet) {
            this.baseTarget = Constants.INITIAL_BASE_TARGET * 10;
        }
    }

    //for forging new blocks only
    BlockImpl(int version, int timestamp, long previousBlockId, long totalFeeKER, byte[] payloadHash,
              byte[] generatorPublicKey, byte[] generationSignature, byte[] previousBlockHash, List<SmcTransactionImpl> transactions, String secretPhrase) {
        this(version, timestamp, previousBlockId, totalFeeKER, payloadHash,
                generatorPublicKey, generationSignature, null, previousBlockHash, transactions);
        blockSignature = Crypto.sign(bytes(), secretPhrase);
        bytes = null;
    }

    //for loading from database only
    BlockImpl(int version, int timestamp, long previousBlockId, long totalFeeKER,
              byte[] payloadHash, long generatorId, byte[] generationSignature, byte[] blockSignature,
              byte[] previousBlockHash, BigInteger cumulativeDifficulty, long baseTarget, long nextBlockId, int height, long id,
              List<SmcTransactionImpl> blockTransactions) {
        this(version, timestamp, previousBlockId, totalFeeKER, payloadHash,
                null, generationSignature, blockSignature, previousBlockHash, blockTransactions);
        this.cumulativeDifficulty = cumulativeDifficulty;
        this.baseTarget = baseTarget;
        this.nextBlockId = nextBlockId;
        this.height = height;
        this.id = id;
        this.generatorId = generatorId;
    }

    private BlockImpl(byte[] blockBytes, List<? extends SmcTransaction> blockTransactions) throws ShareschainException.NotValidException {
        ByteBuffer buffer = ByteBuffer.wrap(blockBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        version = buffer.getInt();
        timestamp = buffer.getInt();
        previousBlockId = buffer.getLong();
        int transactionCount = buffer.getInt();
        totalFeeKER = buffer.getLong();
        payloadHash = new byte[32];
        buffer.get(payloadHash);
        generatorPublicKey = new byte[32];
        buffer.get(generatorPublicKey);
        generationSignature = new byte[32];
        buffer.get(generationSignature);
        previousBlockHash = new byte[32];
        buffer.get(previousBlockHash);
        if (buffer.remaining() >= 64) {
            blockSignature = new byte[64];
            buffer.get(blockSignature);
        }
        if (transactionCount != blockTransactions.size()) {
            throw new ShareschainException.NotValidException("Block transaction count " + transactionCount + " is incorrect");
        }
        List<SmcTransactionImpl> list = new ArrayList<>(transactionCount);
        blockTransactions.forEach((transaction) -> list.add((SmcTransactionImpl)transaction));
        this.blockTransactions = Collections.unmodifiableList(list);
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public long getPreviousBlockId() {
        return previousBlockId;
    }

    @Override
    public byte[] getGeneratorPublicKey() {
        if (generatorPublicKey == null) {
            if (generatorId != 0) {
                generatorPublicKey = Account.getPublicKey(generatorId);
            } else {
                generatorPublicKey = new byte[32];
            }
        }
        return generatorPublicKey;
    }

    @Override
    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    @Override
    public long getTotalFeeKER() {
        return totalFeeKER;
    }

    @Override
    public byte[] getPayloadHash() {
        return payloadHash;
    }

    @Override
    public byte[] getGenerationSignature() {
        return generationSignature;
    }

    @Override
    public byte[] getBlockSignature() {
        return blockSignature;
    }

    /**
     * 获取主链交易数据
     * @return
     */
    @Override
    public List<SmcTransactionImpl> getSmcTransactions() {
        if (this.blockTransactions == null) {
            List<SmcTransactionImpl> transactions = Collections.unmodifiableList(TransactionHome.findBlockTransactions(getId()));
            for (SmcTransactionImpl transaction : transactions) {
                transaction.setBlock(this);
            }
            this.blockTransactions = transactions;
        }
        return this.blockTransactions;
    }

    @Override
    public long getBaseTarget() {
        return baseTarget;
    }

    @Override
    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    @Override
    public long getNextBlockId() {
        return nextBlockId;
    }

    void setNextBlockId(long nextBlockId) {
        this.nextBlockId = nextBlockId;
    }

    @Override
    public int getHeight() {
        if (height == -1) {
            throw new IllegalStateException("Block height not yet set");
        }
        return height;
    }

    @Override
    public long getId() {
        if (id == 0) {
            if (blockSignature == null) {
                throw new IllegalStateException("Block is not signed yet");
            }
            byte[] hash = Crypto.sha256().digest(bytes());
            BigInteger bigInteger = new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public long getGeneratorId() {
        if (generatorId == 0 && height != 0) {
            generatorId = Account.getId(getGeneratorPublicKey());
        }
        return generatorId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockImpl && this.getId() == ((BlockImpl)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    @Override
    public String toString() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("previousBlock", Long.toUnsignedString(previousBlockId));
        json.put("totalFeeKER", totalFeeKER);
        json.put("payloadHash", Convert.toHexString(payloadHash));
        json.put("generatorPublicKey", Convert.toHexString(getGeneratorPublicKey()));
        json.put("generationSignature", Convert.toHexString(generationSignature));
        json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
        json.put("blockSignature", Convert.toHexString(blockSignature));
        JSONArray transactionsData = new JSONArray();
        getSmcTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
        json.put("transactions", transactionsData);
        return JSON.toJSONString(json);
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        if (bytes == null) {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8 + 4 + 8 + 32 + 32 + 32 + 32 + (blockSignature != null ? 64 : 0));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(version);
            buffer.putInt(timestamp);
            buffer.putLong(previousBlockId);
            buffer.putInt(getSmcTransactions().size());
            buffer.putLong(totalFeeKER);
            buffer.put(payloadHash);
            buffer.put(getGeneratorPublicKey());
            buffer.put(generationSignature);
            buffer.put(previousBlockHash);
            if (blockSignature != null) {
                buffer.put(blockSignature);
            }
            bytes = buffer.array();
        }
        return bytes;
    }

    public static BlockImpl parseBlock(byte[] blockBytes, List<? extends SmcTransaction> blockTransactions) throws ShareschainException.NotValidException {
        BlockImpl block = new BlockImpl(blockBytes, blockTransactions);
        if (!block.checkSignature()) {
            throw new ShareschainException.NotValidException("Invalid block signature");
        }
        return block;
    }

    boolean verifyBlockSignature() {
        return checkSignature() && Account.setOrVerify(getGeneratorId(), getGeneratorPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    private boolean checkSignature() {
        if (! hasValidSignature) {
            byte[] data = Arrays.copyOf(bytes(), bytes.length - 64);
            hasValidSignature = blockSignature != null && Crypto.verify(blockSignature, data, getGeneratorPublicKey());
        }
        return hasValidSignature;
    }

    boolean verifyGenerationSignature() throws BlockchainProcessor.BlockOutOfOrderException {

        try {

            BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
            if (previousBlock == null) {
                throw new BlockchainProcessor.BlockOutOfOrderException("Can't verify signature because previous block is missing", this);
            }

            Account account = Account.getAccount(getGeneratorId());
            long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceSCTK();
            if (effectiveBalance <= 0) {
                return false;
            }

            MessageDigest digest = Crypto.sha256();
            digest.update(previousBlock.generationSignature);
            byte[] generationSignatureHash = digest.digest(getGeneratorPublicKey());
            if (!Arrays.equals(generationSignature, generationSignatureHash)) {
                return false;
            }

            BigInteger hit = new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});

            return Generator.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, timestamp);

        } catch (RuntimeException e) {

            Logger.logMessageWithExcpt("Error verifying block generation signature", e);
            return false;

        }

    }

    /**
     * 更新锻造相关费用
     * 1.将锻造的小费增加到锻造当前区块的账户的余额和未确认余额中
     * 2.修改锻造当前区块的账户表（PUBLIC.ACCOUNT）的锻造余额信息
     */
    void apply() {
        Account generatorAccount = Account.addOrGetAccount(getGeneratorId());
        generatorAccount.apply(getGeneratorPublicKey());
        AccountLedger.LedgerEventId eventId = AccountLedger.newEventId(this);
        long totalBackFees = 0;
        if (this.height > 3) {
            long[] backFees = new long[3];
            for (SmcTransactionImpl transaction : getSmcTransactions()) {
                long[] fees = transaction.getBackFees();
                for (int i = 0; i < fees.length; i++) {
                    backFees[i] += fees[i];
                }
            }
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                Account previousGeneratorAccount = Account.getAccount(BlockDB.findBlockAtHeight(this.height - i - 1).getGeneratorId());
                Logger.logDebugMessage("Back fees %f %s to forger at height %d", ((double)backFees[i])/Constants.KER_PER_SCTK, Mainchain.MAINCHAIN_NAME, this.height - i - 1);
                previousGeneratorAccount.addToBalanceAndUnconfirmedBalance(Mainchain.mainchain, LedgerEvent.BLOCK_GENERATED, eventId, backFees[i]);
                previousGeneratorAccount.addToForgedBalanceKER(backFees[i]);
            }
        }
        if (totalBackFees != 0) {
            Logger.logDebugMessage("Fee reduced by %f %s at height %d", ((double)totalBackFees)/Constants.KER_PER_SCTK, Mainchain.MAINCHAIN_NAME, this.height);
        }
        //将锻造的小费增加到锻造当前区块的账户的余额和未确认余额中
        generatorAccount.addToBalanceAndUnconfirmedBalance(Mainchain.mainchain, LedgerEvent.BLOCK_GENERATED, eventId, totalFeeKER - totalBackFees);
        //修改锻造当前区块的账户表（PUBLIC.ACCOUNT）的锻造余额信息
        generatorAccount.addToForgedBalanceKER(totalFeeKER - totalBackFees);
    }

    void setPrevious(BlockImpl block) {
        if (block != null) {
            if (block.getId() != getPreviousBlockId()) {
                throw new IllegalStateException("Previous block id doesn't match");
            }
            this.height = block.getHeight() + 1;
            this.calculateBaseTarget(block);
        } else {
            this.height = 0;
        }
        short index = 0;
        for (SmcTransactionImpl transaction : getSmcTransactions()) {
            transaction.setIndex(index++);
            transaction.setBlock(this);
        }
    }

    void loadTransactions() {
        for (SmcTransactionImpl transaction : getSmcTransactions()) {
            transaction.bytes();
            transaction.getAppendages();
        }
    }

    private static final BigInteger CUMULATIVE_DIFFICULTY_MULTIPLIER = Convert.two64.multiply(BigInteger.valueOf(60));

    /**
     * 根据上一个区块计算累积难度值与基本目标值
     * @param previousBlock
     */
    private void calculateBaseTarget(BlockImpl previousBlock) {
        long prevBaseTarget = previousBlock.baseTarget;

        cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(CUMULATIVE_DIFFICULTY_MULTIPLIER.divide(
                BigInteger.valueOf(prevBaseTarget).multiply(BigInteger.valueOf(this.timestamp - previousBlock.timestamp))));

        int blockchainHeight = previousBlock.height;
        if (blockchainHeight > 2 && blockchainHeight % 2 == 0) {
            BlockImpl block = BlockDB.findBlockAtHeight(blockchainHeight - 2);
            int blocktimeAverage = (this.timestamp - block.timestamp) / 3;
            if (blocktimeAverage > Constants.BLOCK_TIME) {
                baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, Constants.MAX_BLOCKTIME_LIMIT)) / Constants.BLOCK_TIME;
            } else {
                baseTarget = prevBaseTarget - prevBaseTarget * Constants.BASE_TARGET_GAMMA
                        * (Constants.BLOCK_TIME - Math.max(blocktimeAverage, Constants.MIN_BLOCKTIME_LIMIT)) / (100 * Constants.BLOCK_TIME);
            }
            if (baseTarget < 0 || baseTarget > Constants.MAX_BASE_TARGET) {
                baseTarget = Constants.MAX_BASE_TARGET;
            }
            if (baseTarget < Constants.MIN_BASE_TARGET) {
                baseTarget = Constants.MIN_BASE_TARGET;
            }
        } else {
            baseTarget = prevBaseTarget;
        }
    }
}

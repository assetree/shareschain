
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import shareschain.util.crypto.Crypto;
import shareschain.util.Convert;
import shareschain.util.Filter;
import shareschain.util.JSON;
import shareschain.util.Logger;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class TransactionImpl implements Transaction {

    public static abstract class BuilderImpl implements Builder {

        private final short deadline;
        final byte[] senderPublicKey;
        private final long amount;
        private final TransactionType type;
        private final byte version;
        final long fee;
        final int chainId;

        private List<Appendix.AbstractAppendix> appendageList;
        private SortedMap<Integer, Appendix.AbstractAppendix> appendageMap;
        private int appendagesSize;

        private long recipientId;
        byte[] signature;
        private long blockId;
        private int height = Integer.MAX_VALUE;
        private long id;
        long senderId;
        private int timestamp = Integer.MAX_VALUE;
        private int blockTimestamp = -1;
        private byte[] fullHash;
        private boolean ecBlockSet = false;
        private int ecBlockHeight;
        private long ecBlockId;
        private short index = -1;

        private BuilderImpl(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline, TransactionType type) {
            this.version = version;
            this.deadline = deadline;
            this.senderPublicKey = senderPublicKey;
            this.amount = amount;
            this.fee = fee;
            this.chainId = chainId;
            this.type = type;
        }

        BuilderImpl(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                    Attachment.AbstractAttachment attachment) {
            this(chainId, version, senderPublicKey, amount, fee, deadline, attachment.getTransactionType());
            this.appendageMap = new TreeMap<>();
            this.appendageMap.put(attachment.getAppendixType(), attachment);
        }

        BuilderImpl(int chainId, byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                    List<Appendix.AbstractAppendix> appendages) {
            this(chainId, version, senderPublicKey, amount, fee, deadline, ((Attachment)appendages.get(0)).getTransactionType());
            this.appendageList = appendages;
        }

        final void preBuild(String secretPhrase) {
            if (appendageMap != null) {
                appendageList = new ArrayList<>(appendageMap.values());
            }
            if (timestamp == Integer.MAX_VALUE) {
                timestamp = Shareschain.getEpochTime();
            }
            if (!ecBlockSet) {
                Block ecBlock = BlockchainImpl.getInstance().getECBlock(timestamp);
                this.ecBlockHeight = ecBlock.getHeight();
                this.ecBlockId = ecBlock.getId();
            }
            int appendagesSize = 0;
            for (Appendix appendage : appendageList) {
                if (secretPhrase != null && appendage instanceof Appendix.Encryptable) {
                    ((Appendix.Encryptable) appendage).encrypt(secretPhrase);
                }
                appendagesSize += appendage.getSize();
            }
            this.appendagesSize = appendagesSize;
        }

        @Override
        public abstract TransactionImpl build() throws ShareschainExceptions.NotValidExceptions;

        @Override
        public abstract TransactionImpl build(String secretPhrase) throws ShareschainExceptions.NotValidExceptions;

        @Override
        public final BuilderImpl recipientId(long recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        @Override
        public final BuilderImpl appendix(Appendix appendix) {
            if (appendix != null) {
                // 将链表的形式转成map的形式，map的key为类型，value为对象本身。
                if (this.appendageMap == null) {
                    this.appendageMap = new TreeMap<>();
                    this.appendageList.forEach(abstractAppendix -> this.appendageMap.put(abstractAppendix.getAppendixType(), abstractAppendix));
                    this.appendageList = null;
                }
                this.appendageMap.put(appendix.getAppendixType(), (Appendix.AbstractAppendix)appendix);
            }
            return this;
        }

        @Override
        public final BuilderImpl timestamp(int timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public final BuilderImpl ecBlockHeight(int height) {
            this.ecBlockHeight = height;
            this.ecBlockSet = true;
            return this;
        }

        @Override
        public final BuilderImpl ecBlockId(long blockId) {
            this.ecBlockId = blockId;
            this.ecBlockSet = true;
            return this;
        }

        final BuilderImpl id(long id) {
            this.id = id;
            return this;
        }

        final BuilderImpl signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        final BuilderImpl blockId(long blockId) {
            this.blockId = blockId;
            return this;
        }

        public final BuilderImpl height(int height) {
            this.height = height;
            return this;
        }

        final BuilderImpl senderId(long senderId) {
            this.senderId = senderId;
            return this;
        }

        final BuilderImpl fullHash(byte[] fullHash) {
            this.fullHash = fullHash;
            return this;
        }

        final BuilderImpl blockTimestamp(int blockTimestamp) {
            this.blockTimestamp = blockTimestamp;
            return this;
        }

        final BuilderImpl index(short index) {
            this.index = index;
            return this;
        }

        final TransactionType getTransactionType() {
            return type;
        }

        final BuilderImpl prunableAttachments(JSONObject prunableAttachments) throws ShareschainExceptions.NotValidExceptions {
            if (prunableAttachments != null) {
                for (Appendix.Parser parser : AppendixParsers.getPrunableParsers()) {
                    appendix(parser.parse(prunableAttachments));
                }
            }
            return this;
        }

    }

    private final short deadline;
    private volatile byte[] senderPublicKey;
    private final long recipientId;
    private final long amount;
    private final TransactionType type;
    private final int ecBlockHeight;
    private final long ecBlockId;
    private final byte version;
    private final int timestamp;
    final Attachment.AbstractAttachment attachment;
    private final List<Appendix.AbstractAppendix> appendages;
    private final int appendagesSize;

    private volatile int height;
    private volatile long blockId;
    private volatile BlockImpl block;
    private volatile int blockTimestamp;
    private volatile short index;
    private volatile long id;
    private volatile String stringId;
    private volatile long senderId;
    private volatile byte[] fullHash;
    volatile byte[] bytes = null;
    volatile byte[] prunableBytes = null;


    TransactionImpl(BuilderImpl builder) {
        this.timestamp = builder.timestamp;
        this.deadline = builder.deadline;
        this.senderPublicKey = builder.senderPublicKey;
        this.recipientId = builder.recipientId;
        this.amount = builder.amount;
        this.type = builder.type;
        this.attachment = (Attachment.AbstractAttachment)builder.appendageList.get(0);
        this.appendages = Collections.unmodifiableList(builder.appendageList);
        this.appendagesSize = builder.appendagesSize;
        this.version = builder.version;
        this.blockId = builder.blockId;
        this.height = builder.height;
        this.index = builder.index;
        this.id = builder.id;
        this.senderId = builder.senderId;
        this.blockTimestamp = builder.blockTimestamp;
        this.fullHash = builder.fullHash;
        this.ecBlockHeight = builder.ecBlockHeight;
        this.ecBlockId = builder.ecBlockId;
    }

    @Override
    public short getDeadline() {
        return deadline;
    }

    @Override
    public byte[] getSenderPublicKey() {
        if (senderPublicKey == null) {
            senderPublicKey = Account.getPublicKey(senderId);
        }
        return senderPublicKey;
    }

    @Override
    public long getRecipientId() {
        return recipientId;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public int getHeight() {
        return height;
    }

    void setHeight(int height) {
        this.height = height;
    }

    @Override
    public TransactionType getType() {
        return type;
    }

    @Override
    public byte getVersion() {
        return version;
    }

    @Override
    public long getBlockId() {
        return blockId;
    }

    @Override
    public BlockImpl getBlock() {
        if (block == null && blockId != 0) {
            block = BlockchainImpl.getInstance().getBlock(blockId);
        }
        return block;
    }

    void setBlock(BlockImpl block) {
        this.block = block;
        this.blockId = block.getId();
        this.height = block.getHeight();
        this.blockTimestamp = block.getTimestamp();
    }

    void unsetBlock() {
        this.block = null;
        this.blockId = 0;
        this.blockTimestamp = -1;
        // must keep the height set, as transactions already having been included in a popped-off block before
        // get priority when sorted for inclusion in a new block
    }

    @Override
    public short getIndex() {
        if (index == -1) {
            throw new IllegalStateException("Transaction index has not been set");
        }
        return index;
    }

    void setIndex(int index) {
        this.index = (short) index;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public int getBlockTimestamp() {
        return blockTimestamp;
    }

    @Override
    public int getExpiration() {
        return timestamp + deadline * 60;
    }

    /**
     * 获得修剪过的交易快照
     * @return
     */
    @Override
    public Attachment.AbstractAttachment getAttachment() {
        attachment.loadPrunable(this);
        return attachment;
    }

    @Override
    public List<Appendix.AbstractAppendix> getAppendages() {
        return getAppendages(false);
    }

    @Override
    public List<Appendix.AbstractAppendix> getAppendages(boolean includeExpiredPrunable) {
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this, includeExpiredPrunable);
        }
        return appendages;
    }

    @Override
    public List<Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable) {
        List<Appendix> result = new ArrayList<>();
        appendages.forEach(appendix -> {
            if (filter.ok(appendix)) {
                appendix.loadPrunable(this, includeExpiredPrunable);
                result.add(appendix);
            }
        });
        return result;
    }

    List<Appendix.AbstractAppendix> appendages() {
        return appendages;
    }

    /**
     * 获取交易id,并对签名进行二次sha256加密，得到一个摘要的fullHash值，这个fullHash值后续用来判断交易是否存在，是否重复等
     * @return
     */
    @Override
    public final long getId() {
        if (id == 0) {
            if (getSignature() == null) {
                throw new IllegalStateException("Transaction is not signed yet");
            }
            //现将交易相关信息转换成byte数组，然后在将下标为第69的位置开始将值替换为0，共替换64位，得到新的byte数组 替换原先交易中的签名信息
            byte[] data = zeroSignature(getBytes());
            //获取签名（通过密码对交易进行加密sha256签名），然后再经过sha256加密算法，算出一个交易的全hash
            byte[] signatureHash = Crypto.sha256().digest(getSignature());
            MessageDigest digest = Crypto.sha256();
            digest.update(data);
            fullHash = digest.digest(signatureHash);
            BigInteger bigInteger = new BigInteger(1, new byte[]{fullHash[7], fullHash[6], fullHash[5], fullHash[4], fullHash[3], fullHash[2], fullHash[1], fullHash[0]});
            id = bigInteger.longValue();//交易的全hash值取前8位的倒序值
            stringId = getChain().getId() + ":" + Convert.toHexString(getFullHash());
        }
        return id;
    }

    @Override
    public final String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = getChain().getId() + ":" + Convert.toHexString(getFullHash());
            }
        }
        return stringId;
    }

    @Override
    public final byte[] getFullHash() {
        if (fullHash == null) {
            getId();
        }
        return fullHash;
    }

    @Override
    public final long getSenderId() {
        if (senderId == 0) {
            senderId = Account.getId(getSenderPublicKey());
        }
        return senderId;
    }

    public final byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    public final byte[] getPrunableBytes() {
        return Arrays.copyOf(prunableBytes(), prunableBytes.length);
    }

    //把交易相关信息 转换成一个byte数组
    public final byte[] bytes() {
        if (bytes == null) {
            try {
                bytes = generateBytes(false).array();
            } catch (RuntimeException e) {
                if (getSignature() != null) {
                    Logger.logDebugMessage("Failed to get transaction bytes for transaction: " + JSON.toJSONString(getJSONObject()));
                }
                throw e;
            }
        }
        return bytes;
    }

    public final byte[] prunableBytes() {
        if (prunableBytes == null) {
            try {
                prunableBytes = generateBytes(true).array();
            } catch (RuntimeException e) {
                if (getSignature() != null) {
                    Logger.logDebugMessage("Failed to get transaction bytes for transaction: " + JSON.toJSONString(getJSONObject()));
                }
                throw e;
            }
        }
        return prunableBytes;
    }

    /**
     * 把交易相关信息 转换成一个byte数组
     * @param includePrunable
     * @return
     */
    ByteBuffer generateBytes(boolean includePrunable) {
        ByteBuffer buffer = ByteBuffer.allocate(includePrunable ? getFullSize() : getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(getChain().getId());//int占用4字节
        buffer.put(getType().getType());//1字节
        buffer.put(getType().getSubtype());//1字节
        buffer.put(getVersion());//1字节
        buffer.putInt(getTimestamp());//int 4字节
        buffer.putShort(getDeadline());//2字节
        buffer.put(getSenderPublicKey());//32字节
        buffer.putLong(getRecipientId());//8字节
        buffer.putLong(getAmount());//8字节
        buffer.putLong(getFee());//8字节
        buffer.put(getSignature() != null ? getSignature() : new byte[64]);//64字节
        buffer.putInt(getECBlockHeight());//4字节
        buffer.putLong(getECBlockId());//8字节
        putAppendages(buffer, includePrunable);
        return buffer;
    }

    public final byte[] getUnsignedBytes() {
        return zeroSignature(getBytes());
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("chain", getChain().getId());
        json.put("type", type.getType());
        json.put("subtype", type.getSubtype());
        json.put("timestamp", timestamp);
        json.put("deadline", deadline);
        json.put("senderPublicKey", Convert.toHexString(getSenderPublicKey()));
        if (type.canHaveRecipient()) {
            json.put("recipient", Long.toUnsignedString(recipientId));
        }
        json.put("amountKER", amount);
        json.put("feeKER", getFee());
        json.put("ecBlockHeight", ecBlockHeight);
        json.put("ecBlockId", Long.toUnsignedString(ecBlockId));
        json.put("signature", Convert.toHexString(getSignature()));
        if (getSignature() != null) {
            json.put("fullHash", Convert.toHexString(getFullHash()));
        }
        JSONObject attachmentJSON = new JSONObject();
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            attachmentJSON.putAll(appendage.getJSONObject());
        }
        if (!attachmentJSON.isEmpty()) {
            json.put("attachment", attachmentJSON);
        }
        json.put("version", version);
        return json;
    }

    @Override
    public JSONObject getPrunableAttachmentJSON() {
        JSONObject prunableJSON = null;
        for (Appendix.AbstractAppendix appendage : appendages) {
            if (appendage instanceof Appendix.Prunable) {
                appendage.loadPrunable(this);
                if (prunableJSON == null) {
                    prunableJSON = appendage.getJSONObject();
                } else {
                    prunableJSON.putAll(appendage.getJSONObject());
                }
            }
        }
        return prunableJSON;
    }

    @Override
    public int getECBlockHeight() {
        return ecBlockHeight;
    }

    @Override
    public long getECBlockId() {
        return ecBlockId;
    }

    //验证签名  验证发送人的公钥是否合法
    public boolean verifySignature() {
        return checkSignature() && Account.setOrVerify(getSenderId(), getSenderPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    /**
     * 验证签名是否合法
     * @return
     */
    private boolean checkSignature() {
        if (!hasValidSignature) {
            byte[] bytes = getBytes();//把交易相关信息 转换成一个byte数组
            hasValidSignature = getSignature() != null && Crypto.verify(getSignature(), zeroSignature(bytes), getSenderPublicKey());
            if (!hasValidSignature) {
                Logger.logWarningMessage("Invalid signature for transaction bytes " + Convert.toHexString(bytes));
            }
        }
        return hasValidSignature;
    }

    //代表交易byteBuffer的字节数，数字的由来参考本类中的 generateBytes方法
    private static final int SIGNATURE_OFFSET = 4 + 1 + 1 + 1 + 4 + 2 + 32 + 8 + 8 + 8;

    int getSize() {
        return SIGNATURE_OFFSET + 64 + 4 + 8 + 4 + appendagesSize;
    }

    @Override
    public final int getFullSize() {
        int fullSize = getSize() - appendagesSize;
        for (Appendix.AbstractAppendix appendage : getAppendages()) {
            fullSize += appendage.getFullSize();
        }
        return fullSize;
    }
    //从下标为第69的位置开始将值替换为0，共替换64位，
    private byte[] zeroSignature(byte[] data) {
        for (int i = SIGNATURE_OFFSET; i < SIGNATURE_OFFSET + 64; i++) {
            data[i] = 0;
        }
        return data;
    }

    /**
     * 交易的通用验证
     * 1.验证交易的时间戳是否合法
     * 2.交易的截止日期是否小于1分钟
     * 3.交易费用是否小于0
     * 4.交易费用是否大于允许的最大余额
     * 5.交易币的数量是否合法
     * 6.类型是否合法
     * @throws ShareschainExceptions.ValidationExceptions
     */
    @Override
    public void validate() throws ShareschainExceptions.ValidationExceptions {
        if (timestamp <= 0 || deadline < 1 || getFee() < 0
                || getFee() > Constants.MAX_BALANCE_KER
                || amount < 0
                || amount > Constants.MAX_BALANCE_KER
                || type == null) {
            throw new ShareschainExceptions.NotValidExceptions("Invalid transaction parameters:\n type: " + type + ", timestamp: " + timestamp
                    + ", deadline: " + deadline + ", fee: " + getFee() + ", amount: " + amount);
        }
        //SmcPayment type: -2, subtype: 0 ORDINARY
        if (attachment == null || type != attachment.getTransactionType()) {
            throw new ShareschainExceptions.NotValidExceptions("Invalid attachment " + attachment + " for transaction of type " + type);
        }

        if (!type.canHaveRecipient()) {
            if (recipientId != 0 || getAmount() != 0) {
                throw new ShareschainExceptions.NotValidExceptions("Transactions of this type must have recipient == 0, amount == 0");
            }
        }

        if (type.mustHaveRecipient()) {//是否必须有接收人
            if (recipientId == 0) {
                throw new ShareschainExceptions.NotValidExceptions("Transactions of this type must have a valid recipient");
            }
        }

    }

    void validateId() throws ShareschainExceptions.ValidationExceptions {
        if (getId() == 0L) {
            throw new ShareschainExceptions.NotValidExceptions("Invalid transaction id 0");
        }
    }

    //验证已知区块高度和当前区块高度
    final void validateEcBlock() throws ShareschainExceptions.ValidationExceptions {
        if (ecBlockId != 0) {
            if (Shareschain.getBlockchain().getHeight() < ecBlockHeight) {//当前区块链的高度与已知最后区块高度对比
                throw new ShareschainExceptions.NotCurrentlyValidExceptions("ecBlockHeight " + ecBlockHeight
                        + " exceeds blockchain height " + Shareschain.getBlockchain().getHeight());
            }
            if (BlockDB.findBlockIdAtHeight(ecBlockHeight) != ecBlockId) {//根据区块高度获取区块id，与已知最后的区块高度是否一致
                throw new ShareschainExceptions.NotCurrentlyValidExceptions("ecBlockHeight " + ecBlockHeight
                        + " does not match ecBlockId " + Long.toUnsignedString(ecBlockId)
                        + ", transaction was generated on a fork");
            }
        } else {
            throw new ShareschainExceptions.NotValidExceptions("To prevent transaction replay attacks, using ecBlockId=0 is no longer allowed.");
        }
    }

    /**
     * 获取交易的最低费用
     * @return
     */

    @Override
    public final long getMinimumFeeKER() {
        if (blockId != 0) {
            return getMinimumFeeKER(height - 1);
        }
        return getMinimumFeeKER(Shareschain.getBlockchain().getHeight());
    }

    /**
     * 根据区块高度获取最低的KER费用
     * 主链:
     * 子链：
     * @param blockchainHeight
     * @return
     */
    long getMinimumFeeKER(int blockchainHeight) {
        long totalFee = 0;
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            if (blockchainHeight < appendage.getBaselineFeeHeight()) {
                return 0; // No need to validate fees before baseline block
            }
            Fee fee = appendage.getFee(this, blockchainHeight);
            totalFee = Math.addExact(totalFee, fee.getFee(this, appendage));
        }
        if (recipientId != 0
                && ! (Shareschain.getBlockchainProcessor().isScanning() && blockchainHeight < Shareschain.getBlockchainProcessor().getInitialScanHeight() - Constants.MAX_ROLLBACK)
                && ! Account.hasAccount(recipientId, blockchainHeight)) {
            totalFee += Fee.NEW_ACCOUNT_FEE;
        }
        return totalFee;
    }

    abstract boolean hasAllReferencedTransactions(int timestamp, int count);

    public final boolean attachmentIsDuplicate(Map<TransactionType, Map<String, Integer>> duplicates, boolean atAcceptanceHeight) {
        if (!atAcceptanceHeight) {
            // can happen for phased transactions having non-phasable attachment
            return false;
        }
        if (atAcceptanceHeight) {
            // all are checked at acceptance height for block duplicates
            if (getType().isBlockDuplicate(this, duplicates)) {
                return true;
            }
        }
        // non-phased at acceptance height, and phased at execution height
        return getType().isDuplicate(this, duplicates);
    }

    final boolean isUnconfirmedDuplicate(Map<TransactionType, Map<String, Integer>> duplicates) {
        return getType().isUnconfirmedDuplicate(this, duplicates);
    }

    // returns false iff double spending
    final boolean applyUnconfirmed() {
        Account senderAccount = Account.getAccount(getSenderId());
        //SmcPayment type: -2, subtype: 0
        return senderAccount != null && getType().applyUnconfirmed(this, senderAccount);
    }

    final void undoUnconfirmed() {
        Account senderAccount = Account.getAccount(getSenderId());
        getType().undoUnconfirmed(this, senderAccount);
    }

    abstract void apply();

    abstract void save(Connection con, String schemaTable) throws SQLException;

    abstract UnconfirmedTransaction newUnconfirmedTransaction(long arrivalTime, boolean isBundled);

    public static TransactionImpl parseTransaction(byte[] transactionBytes) throws ShareschainExceptions.NotValidExceptions {
        TransactionImpl transaction = newTransactionBuilder(transactionBytes).build();
        if (transaction.getSignature() != null && !transaction.checkSignature()) {
            throw new ShareschainExceptions.NotValidExceptions("Invalid transaction signature for transaction " + JSON.toJSONString(transaction.getJSONObject()));
        }
        return transaction;
    }

    public static TransactionImpl parseTransaction(byte[] transactionBytes, JSONObject prunableAttachments) throws ShareschainExceptions.NotValidExceptions {
        TransactionImpl transaction = newTransactionBuilder(transactionBytes, prunableAttachments).build();
        if (transaction.getSignature() != null && !transaction.checkSignature()) {
            throw new ShareschainExceptions.NotValidExceptions("Invalid transaction signature for transaction " + JSON.toJSONString(transaction.getJSONObject()));
        }
        return transaction;
    }

    static TransactionImpl loadTransaction(Chain chain, ResultSet rs) throws ShareschainExceptions.NotValidExceptions {
        try {
            byte type = rs.getByte("type");
            byte subtype = rs.getByte("subtype");
            int timestamp = rs.getInt("timestamp");
            short deadline = rs.getShort("deadline");
            long amount = rs.getLong("amount");
            long fee = rs.getLong("fee");
            int ecBlockHeight = rs.getInt("ec_block_height");
            long ecBlockId = rs.getLong("ec_block_id");
            byte[] signature = rs.getBytes("signature");
            long blockId = rs.getLong("block_id");
            int height = rs.getInt("height");
            long id = rs.getLong("id");
            long senderId = rs.getLong("sender_id");
            byte[] attachmentBytes = rs.getBytes("attachment_bytes");
            int blockTimestamp = rs.getInt("block_timestamp");
            byte[] fullHash = rs.getBytes("full_hash");
            byte version = rs.getByte("version");
            short transactionIndex = rs.getShort("transaction_index");

            ByteBuffer buffer = null;
            if (attachmentBytes != null) {
                buffer = ByteBuffer.wrap(attachmentBytes);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            }
            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            List<Appendix.AbstractAppendix> appendages = getAppendages(transactionType, buffer);
            TransactionImpl.BuilderImpl builder = chain.newTransactionBuilder(version, amount, fee, deadline, appendages, rs);
            builder.timestamp(timestamp)
                    .signature(signature)
                    .blockId(blockId)
                    .height(height)
                    .id(id)
                    .senderId(senderId)
                    .blockTimestamp(blockTimestamp)
                    .fullHash(fullHash)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId)
                    .index(transactionIndex);
            if (transactionType.canHaveRecipient()) {
                long recipientId = rs.getLong("recipient_id");
                if (! rs.wasNull()) {
                    builder.recipientId(recipientId);
                }
            }
            return builder.build();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static TransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes) throws ShareschainExceptions.NotValidExceptions {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int chainId = buffer.getInt();
            byte type = buffer.get();
            byte subtype = buffer.get();
            byte version = buffer.get();
            int timestamp = buffer.getInt();
            short deadline = buffer.getShort();
            byte[] senderPublicKey = new byte[32];
            buffer.get(senderPublicKey);
            long recipientId = buffer.getLong();
            long amount = buffer.getLong();
            long fee = buffer.getLong();
            byte[] signature = new byte[64];
            buffer.get(signature);
            signature = Convert.emptyToNull(signature);
            int ecBlockHeight = buffer.getInt();
            long ecBlockId = buffer.getLong();
            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            if (transactionType == null) {
                throw new ShareschainExceptions.NotValidExceptions("Invalid transaction type: " + type + ", " + subtype);
            }
            List<Appendix.AbstractAppendix> appendages = getAppendages(transactionType, buffer);
            TransactionImpl.BuilderImpl builder = Chain.getChain(chainId).newTransactionBuilder(version, senderPublicKey, amount, fee, deadline,
                        appendages, buffer);
            builder.timestamp(timestamp)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            if (buffer.hasRemaining()) {
                throw new ShareschainExceptions.NotValidExceptions("Transaction bytes too long, " + buffer.remaining() + " extra bytes");
            }
            return builder;
        } catch (ShareschainExceptions.NotValidExceptions |RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(bytes));
            throw e;
        }
    }

    public static TransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes, JSONObject prunableAttachments) throws ShareschainExceptions.NotValidExceptions {
        TransactionImpl.BuilderImpl builder = newTransactionBuilder(bytes);
        builder.prunableAttachments(prunableAttachments);
        return builder;
    }

    public static TransactionImpl.BuilderImpl newTransactionBuilder(JSONObject transactionData) throws ShareschainExceptions.NotValidExceptions {
        try {
            int chainId = ((Long) transactionData.get("chain")).intValue();
            byte type = ((Long) transactionData.get("type")).byteValue();
            byte subtype = ((Long) transactionData.get("subtype")).byteValue();
            int timestamp = ((Long) transactionData.get("timestamp")).intValue();
            short deadline = ((Long) transactionData.get("deadline")).shortValue();
            byte[] senderPublicKey = Convert.parseHexString((String) transactionData.get("senderPublicKey"));
            long amount = Convert.parseLong(transactionData.get("amountKER"));
            long fee = Convert.parseLong(transactionData.get("feeKER"));
            byte[] signature = Convert.parseHexString((String) transactionData.get("signature"));
            byte version = ((Long) transactionData.get("version")).byteValue();
            JSONObject attachmentData = (JSONObject) transactionData.get("attachment");
            int ecBlockHeight = ((Long) transactionData.get("ecBlockHeight")).intValue();
            long ecBlockId = Convert.parseUnsignedLong((String) transactionData.get("ecBlockId"));

            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            if (transactionType == null) {
                throw new ShareschainExceptions.NotValidExceptions("Invalid transaction type: " + type + ", " + subtype);
            }
            List<Appendix.AbstractAppendix> appendages = new ArrayList<>();
            appendages.add(transactionType.parseAttachment(attachmentData));
            if (attachmentData != null) {
                for (Appendix.Parser parser : AppendixParsers.getParsers()) {
                    Appendix.AbstractAppendix appendix = parser.parse(attachmentData);
                    if (appendix != null) {
                        appendages.add(appendix);
                    }
                }
            }
            TransactionImpl.BuilderImpl builder = Chain.getChain(chainId).newTransactionBuilder(version, senderPublicKey, amount, fee, deadline,
                    appendages, transactionData);
            builder.timestamp(timestamp)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                long recipientId = Convert.parseUnsignedLong((String) transactionData.get("recipient"));
                builder.recipientId(recipientId);
            }
            return builder;
        } catch (ShareschainExceptions.NotValidExceptions |RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + JSON.toJSONString(transactionData));
            throw e;
        }
    }

    static List<Appendix.AbstractAppendix> getAppendages(TransactionType transactionType, ByteBuffer buffer) throws ShareschainExceptions.NotValidExceptions {
        if (buffer == null) {
            return Collections.singletonList(transactionType.parseAttachment(buffer));
        }
        int flags = buffer.getInt();
        Appendix.AbstractAppendix attachment = transactionType.parseAttachment(buffer);
        if (flags == 0) {
            return Collections.singletonList(attachment);
        }
        List<Appendix.AbstractAppendix> list = new ArrayList<>();
        list.add(attachment);
        Collection<Appendix.Parser> parsers = AppendixParsers.getParsers();
        int position = 1;
        for (Appendix.Parser parser : parsers) {
            if ((flags & position) != 0) {
                list.add(parser.parse(buffer));
            }
            position <<= 1;
        }
        return list;
    }

    void putAppendages(ByteBuffer buffer, boolean includePrunable) {
        int flags = 0;
        for (Appendix.AbstractAppendix appendage : appendages()) {
            flags |= appendage.getAppendixType();
        }
        buffer.putInt(flags);
        for (Appendix.AbstractAppendix appendage : appendages()) {
            if (includePrunable) {
                appendage.loadPrunable(this);
                appendage.putPrunableBytes(buffer);
            } else {
                appendage.putBytes(buffer);
            }
        }
    }

}

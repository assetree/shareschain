
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import shareschain.util.crypto.Crypto;
import shareschain.database.DBUtils;
import shareschain.util.Convert;
import shareschain.util.Logger;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

public class SmcTransactionImpl extends TransactionImpl implements SmcTransaction {

    public static final class BuilderImpl extends TransactionImpl.BuilderImpl implements SmcTransaction.Builder {

        private BuilderImpl(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                    Attachment.AbstractAttachment attachment) {
            super(Mainchain.mainchain.getId(), version, senderPublicKey, amount, fee, deadline, attachment);
        }

        private BuilderImpl(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                            List<Appendix.AbstractAppendix> appendages) {
            super(Mainchain.mainchain.getId(), version, senderPublicKey, amount, fee, deadline, appendages);
        }

        @Override
        public SmcTransactionImpl build(String secretPhrase) throws ShareschainExceptions.NotValidExceptions {
            preBuild(secretPhrase);
            return new SmcTransactionImpl(this, secretPhrase);
        }

        @Override
        public SmcTransactionImpl build() throws ShareschainExceptions.NotValidExceptions {
            return build(null);
        }

    }


    private final long feeKER;
    private final byte[] signature;

    /**
     * 通过密码对交易进行加密sha256签名
     * @param builder
     * @param secretPhrase
     * @throws ShareschainExceptions.NotValidExceptions
     */
    SmcTransactionImpl(BuilderImpl builder, String secretPhrase) throws ShareschainExceptions.NotValidExceptions {
        super(builder);
        if (builder.fee <= 0 || (Constants.correctInvalidFees && builder.signature == null)) {
            int effectiveHeight = (getHeight() < Integer.MAX_VALUE ? getHeight() : Shareschain.getBlockchain().getHeight());
            long minFee = getMinimumFeeKER(effectiveHeight);
            this.feeKER = Math.max(minFee, builder.fee);
        } else {
            this.feeKER = builder.fee;
        }
        if (builder.signature != null && secretPhrase != null) {
            throw new ShareschainExceptions.NotValidExceptions("Transaction is already signed");
        } else if (builder.signature != null) {
            this.signature = builder.signature;
        } else if (secretPhrase != null) {
            //判断发送者的公钥是否合法
            byte[] senderPublicKey = builder.senderPublicKey != null ? builder.senderPublicKey : Account.getPublicKey(builder.senderId);
            if (senderPublicKey != null && ! Arrays.equals(senderPublicKey, Crypto.getPublicKey(secretPhrase))) {
                throw new ShareschainExceptions.NotValidExceptions("Secret phrase doesn't match transaction sender public key");
            }
            //利用密码对交易进行签名64位
            this.signature = Crypto.sign(bytes(), secretPhrase);
            bytes = null;
        } else {
            this.signature = null;
        }
    }

    @Override
    public final Chain getChain() {
        return Mainchain.mainchain;
    }

    @Override
    public long getFee() {
        return feeKER;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    boolean hasAllReferencedTransactions(int timestamp, int count) {
        return true;
    }

    /**
     * 交易验证
     * @throws ShareschainExceptions.ValidationExceptions
     */
    @Override
    public void validate() throws ShareschainExceptions.ValidationExceptions {
        try {
            super.validate();//交易通用验证
            //主链是否包含此交易类型
            if (SmcTransactionType.findTransactionType(getType().getType(), getType().getSubtype()) == null) {
                throw new ShareschainExceptions.NotValidExceptions("Invalid transaction type " + getType().getName() + " for SmcTransaction");
            }
            int appendixType = -1;
            //遍历附件，附件被定义交易的附加信息，比如：投票、别名等？？？？？？
            for (Appendix.AbstractAppendix appendage : appendages()) {
                if (appendage.getAppendixType() <= appendixType) {
                    throw new ShareschainExceptions.NotValidExceptions("Duplicate or not in order appendix " + appendage.getAppendixName());
                }
                //appendixType = 0
                appendixType = appendage.getAppendixType();
                if (!appendage.isAllowed(Mainchain.mainchain)) {//判断该链上是否允许该附件，附件被定义交易的附加信息，比如：投票、别名等
                    throw new ShareschainExceptions.NotValidExceptions("Appendix not allowed on Smc chain " + appendage.getAppendixName());
                }
                appendage.loadPrunable(this);//加载
                if (!appendage.verifyVersion()) {//验证附件的版本
                    throw new ShareschainExceptions.NotValidExceptions("Invalid attachment version " + appendage.getVersion());
                }
                appendage.validate(this);//验证长度
            }
            //交易的大小是否超过区块允许的最大值(128*1024b) 128kb
            if (getFullSize() > Constants.MAX_CHILDBLOCK_PAYLOAD_LENGTH) {
                throw new ShareschainExceptions.NotValidExceptions("Transaction size " + getFullSize() + " exceeds maximum payload size");
            }
            //根据区块高度获取最低的KER费用
            long minimumFeeKER = getMinimumFeeKER(Shareschain.getBlockchain().getHeight());
            if (feeKER < minimumFeeKER) {//验证交易费用是否合法
                throw new ShareschainExceptions.NotCurrentlyValidExceptions(String.format("Transaction fee %f %s less than minimum fee %f %s at height %d",
                        ((double) feeKER) / Constants.KER_PER_SCTK, Mainchain.MAINCHAIN_NAME, ((double) minimumFeeKER) / Constants.KER_PER_SCTK, Mainchain.MAINCHAIN_NAME,
                        Shareschain.getBlockchain().getHeight()));
            }
            validateEcBlock();//验证已知区块高度和当前区块高度是否合法
            //检查交易账号是否存在，账号是否属于[账户控制]类型
        } catch (ShareschainExceptions.NotValidExceptions e) {
            if (getSignature() != null) {
                Logger.logMessageWithExcpt("Invalid transaction " + getStringId());
            }
            throw e;
        }
    }

    @Override
    protected void validateId() throws ShareschainExceptions.ValidationExceptions {
        super.validateId();
        for (Appendix.AbstractAppendix appendage : appendages()) {
            appendage.validateId(this);
        }
    }

    /**
     * 更新交易相关余额信息
     * 1.更新发送者账号余额表
     * 2.更新接收人账户余额与未确认余额
     * 3.更新接收人的保证余额
     */
    @Override
    void apply() {
        Account senderAccount = Account.getAccount(getSenderId());
        senderAccount.apply(getSenderPublicKey());
        Account recipientAccount = null;
        if (getRecipientId() != 0) {
            recipientAccount = Account.getAccount(getRecipientId());
            if (recipientAccount == null) {
                recipientAccount = Account.addOrGetAccount(getRecipientId());
            }
        }
        for (Appendix.AbstractAppendix appendage : appendages()) {
            appendage.loadPrunable(this);
            appendage.apply(this, senderAccount, recipientAccount);
        }
    }

    @Override
    void unsetBlock() {
        super.unsetBlock();
        setIndex(-1);
    }

    public long[] getBackFees() {
        return Convert.EMPTY_LONG;
    }

    @Override
    final UnconfirmedSmcTransaction newUnconfirmedTransaction(long arrivalTimestamp, boolean isBundled) {
        return new UnconfirmedSmcTransaction(this, arrivalTimestamp);
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof SmcTransactionImpl && this.getId() == ((Transaction)o).getId();
    }

    @Override
    public final int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    @Override
    void save(Connection con, String schemaTable) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO " + schemaTable
                + " (id, deadline, recipient_id, amount, fee, height, "
                + "block_id, signature, timestamp, type, subtype, sender_id, attachment_bytes, "
                + "block_timestamp, full_hash, version, has_prunable_message, has_prunable_encrypted_message, "
                + "has_prunable_attachment, ec_block_height, ec_block_id, transaction_index) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, getId());
            pstmt.setShort(++i, getDeadline());
            DBUtils.setLongZeroToNull(pstmt, ++i, getRecipientId());
            pstmt.setLong(++i, getAmount());
            pstmt.setLong(++i, getFee());
            pstmt.setInt(++i, getHeight());
            pstmt.setLong(++i, getBlockId());
            pstmt.setBytes(++i, getSignature());
            pstmt.setInt(++i, getTimestamp());
            pstmt.setByte(++i, getType().getType());
            pstmt.setByte(++i, getType().getSubtype());
            pstmt.setLong(++i, getSenderId());
            int bytesLength = 0;
            for (Appendix appendage : getAppendages()) {
                bytesLength += appendage.getSize();
            }
            if (bytesLength == 0) {
                pstmt.setNull(++i, Types.VARBINARY);
            } else {
                ByteBuffer buffer = ByteBuffer.allocate(bytesLength + 4);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                putAppendages(buffer, false);
                pstmt.setBytes(++i, buffer.array());
            }
            pstmt.setInt(++i, getBlockTimestamp());
            pstmt.setBytes(++i, getFullHash());
            pstmt.setByte(++i, getVersion());
            pstmt.setBoolean(++i, false);
            pstmt.setBoolean(++i, false);
            pstmt.setBoolean(++i, getAttachment() instanceof Appendix.Prunable);
            pstmt.setInt(++i, getECBlockHeight());
            DBUtils.setLongZeroToNull(pstmt, ++i, getECBlockId());
            pstmt.setShort(++i, getIndex());
            pstmt.executeUpdate();
        }
    }

    static SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                                List<Appendix.AbstractAppendix> appendages, ResultSet rs) {
        return new BuilderImpl(version, null, amount, fee, deadline, appendages);
    }

    static SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline, Attachment.AbstractAttachment attachment) {
        return new BuilderImpl(version, senderPublicKey, amount, fee, deadline, attachment);
    }

    static SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                List<Appendix.AbstractAppendix> appendages, ByteBuffer buffer) {
            return new BuilderImpl(version, senderPublicKey, amount, fee, deadline, appendages);
    }

    static SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                                List<Appendix.AbstractAppendix> appendages, JSONObject transactionData) {
        return new BuilderImpl(version, senderPublicKey, amount, fee, deadline, appendages);
    }

}

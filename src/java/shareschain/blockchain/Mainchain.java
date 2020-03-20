
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.ShareschainExceptions;
import shareschain.network.APIEnum;
import shareschain.network.APITag;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Mainchain extends Chain {

    public static final String MAINCHAIN_NAME = "SCTK";

    public static final Mainchain mainchain = new Mainchain();

    public static void init() {}

    private Mainchain() {
        super(1, MAINCHAIN_NAME, 8, Constants.isTestnet ? 99949858899030000L : 99846623125660000L, EnumSet.noneOf(APIEnum.class), EnumSet.of(APITag.DGS,
                APITag.DATA, APITag.MS, APITag.SHUFFLING, APITag.VS));
    }

    @Override
    public String getDBSchema() {
        return "PUBLIC";
    }

    @Override
    public boolean isAllowed(TransactionType transactionType) {
        return transactionType.getType() < 0;
    }

    @Override
    public Set<TransactionType> getDisabledTransactionTypes() {
        return Collections.emptySet();
    }

    @Override
    public SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte[] senderPublicKey, long amount, long fee, short deadline, Attachment attachment) {
        return SmcTransactionImpl.newTransactionBuilder((byte)1, senderPublicKey, amount, fee, deadline, (Attachment.AbstractAttachment)attachment);
    }

    @Override
    SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                         List<Appendix.AbstractAppendix> appendages, JSONObject transactionData) {
        return SmcTransactionImpl.newTransactionBuilder(version, senderPublicKey, amount, fee, deadline,
                appendages, transactionData);
    }

    @Override
    SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                         List<Appendix.AbstractAppendix> appendages, ByteBuffer buffer) {
        return SmcTransactionImpl.newTransactionBuilder(version, senderPublicKey, amount, fee, deadline,
                appendages, buffer);
    }

    @Override
    SmcTransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                         List<Appendix.AbstractAppendix> appendages, ResultSet rs) {
        return SmcTransactionImpl.newTransactionBuilder(version, amount, fee, deadline, appendages, rs);
    }

    @Override
    UnconfirmedTransaction newUnconfirmedTransaction(ResultSet rs) throws SQLException, ShareschainExceptions.NotValidExceptions {
        return new UnconfirmedSmcTransaction(rs);
    }
}

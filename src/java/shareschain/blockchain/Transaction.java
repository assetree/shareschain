
package shareschain.blockchain;

import shareschain.ShareschainException;
import shareschain.account.AccountLedger;
import shareschain.util.Filter;
import org.json.simple.JSONObject;

import java.util.List;

public interface Transaction extends AccountLedger.LedgerEventId {

    interface Builder {

        Builder recipientId(long recipientId);

        Builder timestamp(int timestamp);

        Builder ecBlockHeight(int height);

        Builder ecBlockId(long blockId);

        Builder appendix(Appendix appendix);

        Transaction build() throws ShareschainException.NotValidException;

        Transaction build(String secretPhrase) throws ShareschainException.NotValidException;

    }

    Chain getChain();

    long getId();

    String getStringId();

    long getSenderId();

    byte[] getSenderPublicKey();

    long getRecipientId();

    int getHeight();

    long getBlockId();

    Block getBlock();

    short getIndex();

    int getTimestamp();

    int getBlockTimestamp();

    short getDeadline();

    int getExpiration();

    long getAmount();

    long getFee();

    long getMinimumFeeKER();

    byte[] getSignature();

    byte[] getFullHash();

    TransactionType getType();

    Attachment getAttachment();

    boolean verifySignature();

    void validate() throws ShareschainException.ValidationException;

    byte[] getBytes();

    byte[] getUnsignedBytes();

    byte[] getPrunableBytes();

    JSONObject getJSONObject();

    JSONObject getPrunableAttachmentJSON();

    byte getVersion();

    int getFullSize();

    List<? extends Appendix> getAppendages();

    List<? extends Appendix> getAppendages(boolean includeExpiredPrunable);

    List<? extends Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable);

    int getECBlockHeight();

    long getECBlockId();

}

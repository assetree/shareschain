
package shareschain.account;

import shareschain.ShareschainException;
import shareschain.blockchain.Appendix;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Transaction;
import shareschain.util.crypto.Crypto;
import shareschain.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class PublicKeyAnnouncementAppendix extends Appendix.AbstractAppendix {

    public static final int appendixType = 32;
    public static final String appendixName = "PublicKeyAnnouncement";

    public static final Parser appendixParser = new Parser() {
        @Override
        public AbstractAppendix parse(ByteBuffer buffer) {
            return new PublicKeyAnnouncementAppendix(buffer);
        }

        @Override
        public AbstractAppendix parse(JSONObject attachmentData) {
            if (!Appendix.hasAppendix(appendixName, attachmentData)) {
                return null;
            }
            return new PublicKeyAnnouncementAppendix(attachmentData);
        }
    };

    private final byte[] publicKey;

    private PublicKeyAnnouncementAppendix(ByteBuffer buffer) {
        super(buffer);
        this.publicKey = new byte[32];
        buffer.get(this.publicKey);
    }

    private PublicKeyAnnouncementAppendix(JSONObject attachmentData) {
        super(attachmentData);
        this.publicKey = Convert.parseHexString((String)attachmentData.get("recipientPublicKey"));
    }

    public PublicKeyAnnouncementAppendix(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public int getAppendixType() {
        return appendixType;
    }

    @Override
    public String getAppendixName() {
        return appendixName;
    }

    @Override
    protected int getMySize() {
        return 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put(publicKey);
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("recipientPublicKey", Convert.toHexString(publicKey));
    }

    @Override
    public void validate(Transaction transaction) throws ShareschainException.ValidationException {
        if (transaction.getRecipientId() == 0) {
            throw new ShareschainException.NotValidException("PublicKeyAnnouncement cannot be attached to transactions with no recipient");
        }
        if (!Crypto.isCanonicalPublicKey(publicKey)) {
            throw new ShareschainException.NotValidException("Invalid recipient public key: " + Convert.toHexString(publicKey));
        }
        long recipientId = transaction.getRecipientId();
        if (Account.getId(this.publicKey) != recipientId) {
            throw new ShareschainException.NotValidException("Announced public key does not match recipient accountId");
        }
        byte[] recipientPublicKey = Account.getPublicKey(recipientId);
        if (recipientPublicKey != null && ! Arrays.equals(publicKey, recipientPublicKey)) {
            throw new ShareschainException.NotCurrentlyValidException("A different public key for this account has already been announced");
        }
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
        if (Account.setOrVerify(recipientAccount.getId(), publicKey)) {
            recipientAccount.apply(this.publicKey);
        }
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    @Override
    public boolean isAllowed(Chain chain) {
        return false;
    }

}

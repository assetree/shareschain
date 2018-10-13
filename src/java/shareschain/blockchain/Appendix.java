
package shareschain.blockchain;

import shareschain.ShareschainException;
import shareschain.account.Account;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public interface Appendix {

    int getAppendixType();
    int getSize();
    int getFullSize();
    void putBytes(ByteBuffer buffer);
    JSONObject getJSONObject();
    byte getVersion();
    int getBaselineFeeHeight();
    Fee getBaselineFee(Transaction transaction);
    int getNextFeeHeight();
    Fee getNextFee(Transaction transaction);
    Fee getFee(Transaction transaction, int height);
    boolean isAllowed(Chain chain);

    interface Prunable {
        byte[] getHash();
    }

    interface Encryptable {
        void encrypt(String secretPhrase);
    }

    interface Parser {
        AbstractAppendix parse(ByteBuffer buffer) throws ShareschainException.NotValidException;
        AbstractAppendix parse(JSONObject attachmentData) throws ShareschainException.NotValidException;
    }

    abstract class AbstractAppendix implements Appendix {

        private final byte version;

        protected AbstractAppendix(JSONObject attachmentData) {
            version = ((Long) attachmentData.get("version." + getAppendixName())).byteValue();
        }

        protected AbstractAppendix(ByteBuffer buffer) {
            this.version = buffer.get();
        }

        protected AbstractAppendix(int version) {
            this.version = (byte) version;
        }

        protected AbstractAppendix() {
            this.version = 1;
        }

        public abstract String getAppendixName();

        @Override
        public final int getSize() {
            return getMySize() + (version > 0 ? 1 : 0);
        }

        @Override
        public final int getFullSize() {
            return getSize();
        }

        protected abstract int getMySize();

        @Override
        public final void putBytes(ByteBuffer buffer) {
            if (version > 0) {
                buffer.put(version);
            }
            putMyBytes(buffer);
        }

        protected abstract void putMyBytes(ByteBuffer buffer);

        final void putPrunableBytes(ByteBuffer buffer) {
            putBytes(buffer);
        }

        @Override
        public final JSONObject getJSONObject() {
            JSONObject json = new JSONObject();
            json.put("version." + getAppendixName(), version);
            putMyJSON(json);
            return json;
        }

        protected abstract void putMyJSON(JSONObject json);

        @Override
        public final byte getVersion() {
            return version;
        }

        public boolean verifyVersion() {
            return version == 1;
        }

        @Override
        public int getBaselineFeeHeight() {
            return 0;
        }

        @Override
        public Fee getBaselineFee(Transaction transaction) {
            return Fee.NONE;
        }

        @Override
        public int getNextFeeHeight() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Fee getNextFee(Transaction transaction) {
            return getBaselineFee(transaction);
        }

        @Override
        public final Fee getFee(Transaction transaction, int height) {
            return height >= getNextFeeHeight() ? getNextFee(transaction) : getBaselineFee(transaction);
        }

        public abstract void validate(Transaction transaction) throws ShareschainException.ValidationException;

        public void validateId(Transaction transaction) throws ShareschainException.ValidationException {}

        public void validateAtFinish(Transaction transaction) throws ShareschainException.ValidationException {
            validate(transaction);
        }

        public abstract void apply(Transaction transaction, Account senderAccount, Account recipientAccount);

        public final void loadPrunable(Transaction transaction) {
            loadPrunable(transaction, false);
        }

        public void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {}

    }

    static boolean hasAppendix(String appendixName, JSONObject attachmentData) {
        return attachmentData.get("version." + appendixName) != null;
    }

}

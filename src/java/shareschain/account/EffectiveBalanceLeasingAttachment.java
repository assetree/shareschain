
package shareschain.account;

import shareschain.blockchain.Attachment;
import shareschain.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class EffectiveBalanceLeasingAttachment extends Attachment.AbstractAttachment {

    private final int period;

    EffectiveBalanceLeasingAttachment(ByteBuffer buffer) {
        super(buffer);
        this.period = Short.toUnsignedInt(buffer.getShort());
    }

    EffectiveBalanceLeasingAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.period = ((Long) attachmentData.get("period")).intValue();
    }

    public EffectiveBalanceLeasingAttachment(int period) {
        this.period = period;
    }

    @Override
    protected int getMySize() {
        return 2;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putShort((short)period);
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("period", period);
    }

    @Override
    public TransactionType getTransactionType() {
        return AccountControlSmcTransactionType.EFFECTIVE_BALANCE_LEASING;
    }

    public int getPeriod() {
        return period;
    }
}


package shareschain.blockchain;

import shareschain.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ChainTransactionId implements Comparable<ChainTransactionId> {

    public static final int BYTE_SIZE = 4 + 32;

    private final int chainId;
    private final byte[] hash;
    private final long id;

    public ChainTransactionId(int chainId, byte[] hash) {
        this.chainId = chainId;
        this.hash = hash;
        this.id = Convert.fullHashToId(hash);
    }

    public int getChainId() {
        return chainId;
    }

    public String getStringId() {
        return chainId + ":" + Convert.toHexString(hash);
    }

    public byte[] getFullHash() {
        return hash;
    }

    public long getTransactionId() {
        return id;
    }

    public Chain getChain() {
        return Chain.getChain(chainId);
    }


    public Transaction getTransaction() {
        return Chain.getChain(chainId).getTransactionHome().findTransaction(hash);
    }


    public static ChainTransactionId getChainTransactionId(Transaction transaction) {
        return new ChainTransactionId(transaction.getChain().getId(), transaction.getFullHash());
    }

    public static ChainTransactionId parse(ByteBuffer buffer) {
        int chainId = buffer.getInt();
        byte[] hash = new byte[32];
        buffer.get(hash);
        if (Convert.emptyToNull(hash) == null) {
            return null;
        }
        return new ChainTransactionId(chainId, hash);
    }

    public void put(ByteBuffer buffer) {
        buffer.putInt(chainId);
        buffer.put(hash);
    }

    public static ChainTransactionId parse(JSONObject json) {
        if (json == null) {
            return null;
        }
        int chainId = ((Long)json.get("chain")).intValue();
        byte[] hash = Convert.parseHexString((String)json.get("transactionFullHash"));
        return new ChainTransactionId(chainId, hash);
    }

    public JSONObject getJSON() {
        JSONObject json = new JSONObject();
        json.put("chain", chainId);
        json.put("transactionFullHash", Convert.toHexString(hash));
        return json;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ChainTransactionId && chainId == ((ChainTransactionId)object).chainId && Arrays.equals(hash, ((ChainTransactionId)object).hash);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "chain: " + Chain.getChain(chainId).getName() + ", full hash: " + Convert.toHexString(hash);
    }

    @Override
    public int compareTo(ChainTransactionId o) {
        int result = Integer.compare(this.chainId, o.chainId);
        if (result != 0) {
            return result;
        }
        result = Long.compare(this.id, o.id);
        if (result != 0) {
            return result;
        }
        return Convert.byteArrayComparator.compare(this.hash, o.hash);
    }
}

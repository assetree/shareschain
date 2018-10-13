
package shareschain.blockchain;

import shareschain.ShareschainException;
import shareschain.account.BalanceHome;
import shareschain.network.APIEnum;
import shareschain.network.APITag;
import shareschain.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class Chain {

    public static Chain getChain(String name) {
        return Mainchain.mainchain;
    }

    public static Chain getChain(int id) {
        return Mainchain.mainchain;
    }

    public final long ONE_COIN;

    private final String name;
    private final int id;
    private final int decimals;
    private final long totalAmount;
    private final TransactionHome transactionHome;
    private final BalanceHome balanceHome;
    private final Set<APIEnum> disabledAPIs;
    private final Set<APITag> disabledAPITags;

    Chain(int id, String name, int decimals, long totalAmount, EnumSet<APIEnum> disabledAPIs, EnumSet<APITag> disabledAPITags) {
        this.id = id;
        this.name = name;
        this.decimals = decimals;
        this.ONE_COIN = Convert.decimalMultiplier(decimals);
        this.totalAmount = totalAmount;
        this.transactionHome = TransactionHome.forChain(this);
        this.balanceHome = BalanceHome.forChain(this);
        disabledAPIs = disabledAPIs.clone();
        for (APIEnum api : APIEnum.values()) {
            if (!Collections.disjoint(api.getHandler().getAPITags(), disabledAPITags)) {
                disabledAPIs.add(api);
            }
        }
        this.disabledAPIs = Collections.unmodifiableSet(disabledAPIs);
        this.disabledAPITags = Collections.unmodifiableSet(disabledAPITags);

    }

    public final String getName() {
        return name;
    }

    public final int getId() {
        return id;
    }

    public final int getDecimals() {
        return decimals;
    }

    public final long getTotalAmount() {
        return totalAmount;
    }

    public String getDBSchema() {
        return name;
    }

    public final String getSchemaTable(String table) {
        if (table.contains(".")) {
            throw new IllegalArgumentException("Schema already specified: " + table);
        }
        return getDBSchema() + "." + table.toUpperCase(Locale.ROOT);
    }

    public final TransactionHome getTransactionHome() {
        return transactionHome;
    }

    public final BalanceHome getBalanceHome() {
        return balanceHome;
    }

    public abstract boolean isAllowed(TransactionType transactionType);

    public abstract Set<TransactionType> getDisabledTransactionTypes();

    public Set<APIEnum> getDisabledAPIs() {
        return disabledAPIs;
    }

    public Set<APITag> getDisabledAPITags() {
        return disabledAPITags;
    }

    public abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte[] senderPublicKey, long amountKER, long feeKER, short deadline, Attachment attachment);

    abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                               List<Appendix.AbstractAppendix> appendages, JSONObject transactionData);

    abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte version, byte[] senderPublicKey, long amount, long fee, short deadline,
                                                               List<Appendix.AbstractAppendix> appendages, ByteBuffer buffer);

    abstract TransactionImpl.BuilderImpl newTransactionBuilder(byte version, long amount, long fee, short deadline,
                                                               List<Appendix.AbstractAppendix> appendages, ResultSet rs);

    abstract UnconfirmedTransaction newUnconfirmedTransaction(ResultSet rs) throws SQLException, ShareschainException.NotValidException;

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}

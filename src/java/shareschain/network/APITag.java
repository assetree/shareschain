
package shareschain.network;

import java.util.HashMap;
import java.util.Map;

public enum APITag {

    ACCOUNTS("Accounts"), ACCOUNT_CONTROL("Account Control"),
    BLOCKS("Blocks"), CE("Coin Exchange"),
    CREATE_TRANSACTION("Create Transaction"), DGS("Digital Goods Store"), FORGING("Forging"), MESSAGES("Messages"),
    MS("Monetary System"), NETWORK("Networking"), PHASING("Phasing"), SEARCH("Search"), INFO("Server Info"),
    SHUFFLING("Shuffling"), DATA("Tagged Data"), TOKENS("Tokens"), TRANSACTIONS("Transactions"), VS("Voting System"),
    UTILS("Utils"), DEBUG("Debug"), ADDONS("Add-ons");

    private static final Map<String, APITag> apiTags = new HashMap<>();
    static {
        for (APITag apiTag : values()) {
            if (apiTags.put(apiTag.getDisplayName(), apiTag) != null) {
                throw new RuntimeException("Duplicate APITag name: " + apiTag.getDisplayName());
            }
        }
    }

    public static APITag fromDisplayName(String displayName) {
        APITag apiTag = apiTags.get(displayName);
        if (apiTag == null) {
            throw new IllegalArgumentException("Invalid APITag name: " + displayName);
        }
        return apiTag;
    }

    private final String displayName;

    APITag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

}

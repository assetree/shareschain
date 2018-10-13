
package shareschain.blockchain;

import shareschain.Constants;
import shareschain.account.Account;
import shareschain.util.crypto.Crypto;
import shareschain.database.DB;
import shareschain.util.Convert;
import shareschain.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class Genesis {

    static final byte[] generationSignature = Constants.isTestnet ?
            new byte[] {
                    100, -99, 57, 70, -59, -29, 87, -109, -62, -77, 93, 102, -37, -85, 107,
                    95, 112, 2, -82, 112, 100, 88, 38, 39, 123, 77, 13, -61, 5, -23, 89, 69
            }
            :
            new byte[] {
                    -76, -77, -99, 2, 22, 99, -88, 109, -3, 87, 12, -41, -48, -77, 105, -51, 11,
                    117, 8, -85, -3, -87, 66, 16, 22, 98, -80, 119, -23, 86, -70, 97
            };

    static byte[] apply() {
        MessageDigest digest = Crypto.sha256();
        importBalances(digest);
        importAccountInfo(digest);
        importAccountProperties(digest);
        importAccountControls(digest);
        digest.update(Convert.toBytes(Constants.EPOCH_BEGINNING));
        return digest.digest();
    }

    private static long convertPubkeyToAccountId(String pubkey){
        byte[] publicKey = Convert.parseHexString(pubkey);
        return Account.getId(publicKey);
    }

    public static void main(String[] args) {
        String pubkey = "6e77ce49b3bb30475b3d49a07616faaacc9e8812c38f2a637a91b963eb7c8348";
        Long accountId = convertPubkeyToAccountId(pubkey);
        System.out.println("The account id of " + pubkey + " is " + Long.toUnsignedString(accountId));

    }

    private static void importBalances(MessageDigest digest) {
        List<Chain> chains = new ArrayList<>();
        chains.add(Mainchain.mainchain);
        chains.sort(Comparator.comparingInt(Chain::getId));
        for (Chain chain : chains) {
            int count = 0;
            try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                    ClassLoader.getSystemResourceAsStream((Constants.isTestnet ? "data/tst/" : "data/pro/") + chain.getName() + (Constants.isTestnet ? "-testnet.json" : ".json")), digest), "UTF-8")) {
                JSONObject chainBalances = (JSONObject) JSONValue.parseWithException(is);
                Logger.logDebugMessage("Loading genesis amounts for " + chain.getName());
                long total = 0;
                for (Map.Entry<String, Long> entry : ((Map<String, Long>)chainBalances).entrySet()) {
                    Account account = Account.addOrGetAccount(Long.parseUnsignedLong(entry.getKey()));
                    account.addToBalanceAndUnconfirmedBalance(chain, null, null, entry.getValue());
                    total += entry.getValue();
                    if (count++ % 100 == 0) {
                        DB.db.commitTransaction();
                        DB.db.clearCache();
                    }
                }
                Logger.logDebugMessage("Total balance %f %s", (double)total / chain.ONE_COIN, chain.getName());
            } catch (IOException|ParseException e) {
                throw new RuntimeException("Failed to process genesis recipients accounts for " + chain.getName(), e);
            }
        }
    }

    private static void importAccountInfo(MessageDigest digest) {
        try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                ClassLoader.getSystemResourceAsStream((Constants.isTestnet ? "data/tst/" : "data/pro/")+"ACCOUNT_INFO" + (Constants.isTestnet ? "-testnet.json" : ".json")), digest), "UTF-8")) {
            JSONObject accountInfos = (JSONObject) JSONValue.parseWithException(is);
            Logger.logDebugMessage("Loading account info");
            int count = 0;
            for (Map.Entry<String, Map<String, String>> entry : ((Map<String, Map<String, String>>)accountInfos).entrySet()) {
                long accountId = Long.parseUnsignedLong(entry.getKey());
                String name = entry.getValue().get("name");
                String description = entry.getValue().get("description");
                Account.getAccount(accountId).setAccountInfo(name, description);
                if (count++ % 100 == 0) {
                    DB.db.commitTransaction();
                    DB.db.clearCache();
                }
            }
            Logger.logDebugMessage("Loaded " + count + " account infos");
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process account infos", e);
        }
    }

    private static void importAccountProperties(MessageDigest digest) {
        try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                ClassLoader.getSystemResourceAsStream((Constants.isTestnet ? "data/tst/" : "data/pro/")+"ACCOUNT_PROPERTIES" + (Constants.isTestnet ? "-testnet.json" : ".json")), digest), "UTF-8")) {
            JSONObject accountProperties = (JSONObject) JSONValue.parseWithException(is);
            Logger.logDebugMessage("Loading account properties");
            int count = 0;
            long propertyId = 1;
            for (Map.Entry<String, Map<String, Map<String, String>>> entry : ((Map<String, Map<String, Map<String, String>>>)accountProperties).entrySet()) {
                long recipientId = Long.parseUnsignedLong(entry.getKey());
                Map<String, Map<String, String>> setters = entry.getValue();
                for (Map.Entry<String, Map<String, String>> setterEntry : setters.entrySet()) {
                    long setterId = Long.parseUnsignedLong(setterEntry.getKey());
                    Map<String, String> setterProperties = setterEntry.getValue();
                    for (Map.Entry<String, String> property : setterProperties.entrySet()) {
                        String name = property.getKey();
                        String value = property.getValue();
                        Account.importProperty(propertyId++, recipientId, setterId, name, value);
                    }
                }
                if (count++ % 100 == 0) {
                    DB.db.commitTransaction();
                    DB.db.clearCache();
                }
            }
            Logger.logDebugMessage("Loaded " + count + " account properties");
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process account properties", e);
        }
    }

    private static void importAccountControls(MessageDigest digest) {
        try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                ClassLoader.getSystemResourceAsStream((Constants.isTestnet ? "data/tst/" : "data/pro/")+"ACCOUNT_CONTROL" + (Constants.isTestnet ? "-testnet.json" : ".json")), digest), "UTF-8")) {
            JSONObject accountControls = (JSONObject) JSONValue.parseWithException(is);
            Logger.logDebugMessage("Loading account controls");
            int count = 0;
            for (Map.Entry<String, Map<String, Object>> entry : ((Map<String, Map<String, Object>>)accountControls).entrySet()) {
                long accountId = Long.parseUnsignedLong(entry.getKey());
                int quorum = ((Long)entry.getValue().get("quorum")).intValue();
                long maxFees = (Long) entry.getValue().get("maxFees");
                int minDuration = ((Long)entry.getValue().get("minDuration")).intValue();
                int maxDuration = ((Long)entry.getValue().get("maxDuration")).intValue();
                JSONArray whitelist = (JSONArray)entry.getValue().get("whitelist");
                if (count++ % 100 == 0) {
                    DB.db.commitTransaction();
                    DB.db.clearCache();
                }
            }
            Logger.logDebugMessage("Loaded " + count + " account controls");
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process account controls", e);
        }
    }


    private Genesis() {} // never

}

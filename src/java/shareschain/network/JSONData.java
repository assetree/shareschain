
package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.account.Account;
import shareschain.account.AccountLedger;
import shareschain.account.AccountLedger.LedgerEntry;
import shareschain.account.BalanceHome;
import shareschain.account.Token;
import shareschain.blockchain.*;
import shareschain.blockchain.SmcTransaction;
import shareschain.util.crypto.Crypto;
import shareschain.util.crypto.EncryptedData;
import shareschain.node.Node;
import shareschain.util.Convert;
import shareschain.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Map;

public final class JSONData {

    static JSONObject balance(Chain chain, long accountId, int height) {
        JSONObject json = new JSONObject();
        BalanceHome.Balance balance = chain.getBalanceHome().getBalance(accountId, height);
        if (balance == null) {
            json.put("balanceKER", "0");
            json.put("unconfirmedBalanceKER", "0");
        } else {
            json.put("balanceKER", String.valueOf(balance.getBalance()));
            json.put("unconfirmedBalanceKER", String.valueOf(balance.getUnconfirmedBalance()));
        }
        return json;
    }

    static JSONObject lessor(Account account, boolean includeEffectiveBalance) {
        JSONObject json = new JSONObject();
        Account.AccountLease accountLease = account.getAccountLease();
        if (accountLease.getCurrentLesseeId() != 0) {
            putAccount(json, "currentLessee", accountLease.getCurrentLesseeId());
            json.put("currentHeightFrom", String.valueOf(accountLease.getCurrentLeasingHeightFrom()));
            json.put("currentHeightTo", String.valueOf(accountLease.getCurrentLeasingHeightTo()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceSCTK", String.valueOf(account.getGuaranteedBalanceKER() / Constants.KER_PER_SCTK));
            }
        }
        if (accountLease.getNextLesseeId() != 0) {
            putAccount(json, "nextLessee", accountLease.getNextLesseeId());
            json.put("nextHeightFrom", String.valueOf(accountLease.getNextLeasingHeightFrom()));
            json.put("nextHeightTo", String.valueOf(accountLease.getNextLeasingHeightTo()));
        }
        return json;
    }

    static JSONObject accountProperty(Account.AccountProperty accountProperty, boolean includeAccount, boolean includeSetter) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            putAccount(json, "recipient", accountProperty.getRecipientId());
        }
        if (includeSetter) {
            putAccount(json, "setter", accountProperty.getSetterId());
        }
        json.put("property", accountProperty.getProperty());
        json.put("value", accountProperty.getValue());
        return json;
    }


    static JSONObject block(Block block, boolean includeTransactions, boolean includeExecutedPhased) {
        JSONObject json = new JSONObject();
        json.put("block", block.getStringId());
        json.put("height", block.getHeight());
        putAccount(json, "generator", block.getGeneratorId());
        json.put("generatorPublicKey", Convert.toHexString(block.getGeneratorPublicKey()));
        json.put("timestamp", block.getTimestamp());
        json.put("numberOfTransactions", block.getSmcTransactions().size());
        json.put("totalFeeKER", String.valueOf(block.getTotalFeeKER()));
        json.put("version", block.getVersion());
        json.put("baseTarget", Long.toUnsignedString(block.getBaseTarget()));
        json.put("cumulativeDifficulty", block.getCumulativeDifficulty().toString());
        if (block.getPreviousBlockId() != 0) {
            json.put("previousBlock", Long.toUnsignedString(block.getPreviousBlockId()));
        }
        if (block.getNextBlockId() != 0) {
            json.put("nextBlock", Long.toUnsignedString(block.getNextBlockId()));
        }
        json.put("payloadHash", Convert.toHexString(block.getPayloadHash()));
        json.put("generationSignature", Convert.toHexString(block.getGenerationSignature()));
        json.put("previousBlockHash", Convert.toHexString(block.getPreviousBlockHash()));
        json.put("blockSignature", Convert.toHexString(block.getBlockSignature()));
        JSONArray transactions = new JSONArray();
        if (includeTransactions) {
            block.getSmcTransactions().forEach(transaction -> transactions.add(transaction(transaction)));
        } else {
            block.getSmcTransactions().forEach(transaction -> transactions.add(Convert.toHexString(transaction.getFullHash())));
        }
        json.put("transactions", transactions);
        return json;
    }

    static JSONObject encryptedData(EncryptedData encryptedData) {
        JSONObject json = new JSONObject();
        json.put("data", Convert.toHexString(encryptedData.getData()));
        json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
        return json;
    }

    static JSONObject token(Token token) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.getId(token.getPublicKey()));
        json.put("timestamp", token.getTimestamp());
        json.put("valid", token.isValid());
        return json;
    }

    static JSONObject node(Node node) {
        JSONObject json = new JSONObject();
        json.put("address", node.getHost());
        json.put("port", node.getPort());
        json.put("state", node.getState().ordinal());
        json.put("announcedAddress", node.getAnnouncedAddress());
        json.put("shareAddress", node.shareAddress());
        json.put("downloadedVolume", node.getDownloadedVolume());
        json.put("uploadedVolume", node.getUploadedVolume());
        json.put("application", node.getApplication());
        json.put("version", node.getVersion());
        json.put("platform", node.getPlatform());
        if (node.getApiPort() != 0) {
            json.put("apiPort", node.getApiPort());
        }
        if (node.getApiSSLPort() != 0) {
            json.put("apiSSLPort", node.getApiSSLPort());
        }
        json.put("blacklisted", node.isBlacklisted());
        json.put("lastUpdated", node.getLastUpdated());
        json.put("lastConnectAttempt", node.getLastConnectAttempt());
        json.put("inbound", node.isInbound());
        if (node.isBlacklisted()) {
            json.put("blacklistingCause", node.getBlacklistingCause());
        }
        JSONArray servicesArray = new JSONArray();
        for (Node.Service service : Node.Service.values()) {
            if (node.providesService(service)) {
                servicesArray.add(service.name());
            }
        }
        json.put("services", servicesArray);
        json.put("blockchainState", node.getBlockchainState());
        return json;
    }

    static JSONObject unconfirmedTransaction(Transaction transaction) {
        return unconfirmedTransaction(transaction, null);
    }

    static JSONObject unconfirmedTransaction(Transaction transaction, Filter<Appendix> filter) {
        JSONObject json = new JSONObject();
        json.put("type", transaction.getType().getType());
        json.put("subtype", transaction.getType().getSubtype());
        json.put("chain", transaction.getChain().getId());
        json.put("timestamp", transaction.getTimestamp());
        json.put("deadline", transaction.getDeadline());
        json.put("senderPublicKey", Convert.toHexString(transaction.getSenderPublicKey()));
        if (transaction.getRecipientId() != 0) {
            putAccount(json, "recipient", transaction.getRecipientId());
        }
        json.put("amountKER", String.valueOf(transaction.getAmount()));
        json.put("feeKER", String.valueOf(transaction.getFee()));
        byte[] signature = Convert.emptyToNull(transaction.getSignature());
        if (signature != null) {
            json.put("signature", Convert.toHexString(signature));
            json.put("signatureHash", Convert.toHexString(Crypto.sha256().digest(signature)));
            json.put("fullHash", Convert.toHexString(transaction.getFullHash()));
            if (transaction instanceof SmcTransaction) {
                json.put("transaction", Long.toUnsignedString(transaction.getId()));
            }
        }
        JSONObject attachmentJSON = new JSONObject();
        if (filter == null) {
            for (Appendix appendage : transaction.getAppendages(true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        } else {
            for (Appendix appendage : transaction.getAppendages(filter, true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        }
        if (! attachmentJSON.isEmpty()) {
            for (Map.Entry entry : (Iterable<Map.Entry>) attachmentJSON.entrySet()) {
                if (entry.getValue() instanceof Long) {
                    entry.setValue(String.valueOf(entry.getValue()));
                }
            }
            json.put("attachment", attachmentJSON);
        }
        putAccount(json, "sender", transaction.getSenderId());
        json.put("height", transaction.getHeight());
        json.put("version", transaction.getVersion());
        json.put("ecBlockId", Long.toUnsignedString(transaction.getECBlockId()));
        json.put("ecBlockHeight", transaction.getECBlockHeight());

        return json;
    }

    static JSONObject transaction(Transaction transaction) {
        return transaction(transaction, false);
    }

    static JSONObject transaction(Transaction transaction, boolean includePhasingResult) {
        JSONObject json = transaction(transaction, null);
        if (includePhasingResult ) {
        }
        return json;
    }

    static JSONObject transaction(Transaction transaction, Filter<Appendix> filter) {
        JSONObject json = unconfirmedTransaction(transaction, filter);
        json.put("block", Long.toUnsignedString(transaction.getBlockId()));
        json.put("confirmations", Shareschain.getBlockchain().getHeight() - transaction.getHeight());
        json.put("blockTimestamp", transaction.getBlockTimestamp());
        json.put("transactionIndex", transaction.getIndex());
        return json;
    }

    static JSONObject smcTransaction(SmcTransaction smcTransaction, boolean includeChildTransactions) {
        JSONObject json = transaction(smcTransaction);
        if (includeChildTransactions) {
            JSONArray childTransactions = new JSONArray();
            json.put("childTransactions", childTransactions);
        }
        return json;
    }

    static JSONObject unconfirmedSmcTransaction(SmcTransaction smcTransaction, boolean includeChildTransactions) {
        JSONObject json = unconfirmedTransaction(smcTransaction);
        if (includeChildTransactions) {
            JSONArray childTransactions = new JSONArray();
            json.put("childTransactions", childTransactions);
        }
        return json;
    }

    static JSONObject generator(Generator generator, int elapsedTime) {
        JSONObject response = new JSONObject();
        long deadline = generator.getDeadline();
        putAccount(response, "account", generator.getAccountId());
        response.put("deadline", deadline);
        response.put("hitTime", generator.getHitTime());
        response.put("remaining", Math.max(deadline - elapsedTime, 0));
        return response;
    }

    static JSONObject apiRequestHandler(APIServlet.APIRequestHandler handler) {
        JSONObject json = new JSONObject();
        json.put("allowRequiredBlockParameters", handler.allowRequiredBlockParameters());
        if (handler.getFileParameter() != null) {
            json.put("fileParameter", handler.getFileParameter());
        }
        json.put("requireBlockchain", handler.requireBlockchain());
        json.put("requirePost", handler.requirePost());
        json.put("requirePassword", handler.requirePassword());
        json.put("requireFullClient", handler.requireFullClient());
        return json;
    }

    static void putPrunableAttachment(JSONObject json, Transaction transaction) {
        JSONObject prunableAttachment = transaction.getPrunableAttachmentJSON();
        if (prunableAttachment != null) {
            json.put("prunableAttachmentJSON", prunableAttachment);
        }
    }

    static void putException(JSONObject json, Exception e) {
        putException(json, e, "");
    }

    static void putException(JSONObject json, Exception e, String error) {
        json.put("errorCode", 4);
        if (error.length() > 0) {
            error += ": ";
        }
        json.put("error", e.toString());
        json.put("errorDescription", error + e.getMessage());
    }

    static void putAccount(JSONObject json, String name, long accountId) {
        json.put(name, Long.toUnsignedString(accountId));
        json.put(name + "RS", Convert.rsAccount(accountId));
    }

    static void ledgerEntry(JSONObject json, LedgerEntry entry, boolean includeTransactions, boolean includeHoldingInfo) {
        putAccount(json, "account", entry.getAccountId());
        json.put("ledgerId", Long.toUnsignedString(entry.getLedgerId()));
        json.put("block", Long.toUnsignedString(entry.getBlockId()));
        json.put("height", entry.getHeight());
        json.put("timestamp", entry.getTimestamp());
        json.put("eventType", entry.getEvent().name());
        json.put("event", Long.toUnsignedString(entry.getEventId()));
        byte[] eventHash = entry.getEventHash();
        if (eventHash != null) {
            json.put("eventHash", Convert.toHexString(eventHash));
        }
        json.put("chain", entry.getChainId());
        json.put("isTransactionEvent", entry.getEvent().isTransaction());
        json.put("change", String.valueOf(entry.getChange()));
        json.put("balance", String.valueOf(entry.getBalance()));
        AccountLedger.LedgerHolding ledgerHolding = entry.getHolding();
        json.put("holdingType", ledgerHolding.name());
        json.put("holdingTypeCode", ledgerHolding.getCode());
        json.put("holdingTypeIsUnconfirmed", ledgerHolding.isUnconfirmed());
        json.put("holding", Long.toUnsignedString(entry.getHoldingId()));
        if (includeHoldingInfo) {
            JSONObject holdingJson = null;
            if (holdingJson != null) {
                json.put("holdingInfo", holdingJson);
            }
        }
        if (includeTransactions && entry.getEvent().isTransaction()) {
            Chain chain = Chain.getChain(entry.getChainId());
            Transaction transaction = Shareschain.getBlockchain().getTransaction(chain, entry.getEventHash());
            json.put("transaction", JSONData.transaction(transaction));
        }
    }

    private JSONData() {} // never

}

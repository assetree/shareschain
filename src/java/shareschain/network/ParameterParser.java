
package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainException;
import shareschain.account.Account;
import shareschain.blockchain.Chain;
import shareschain.blockchain.ChainTransactionId;
import shareschain.blockchain.Transaction;
import shareschain.util.crypto.Crypto;
import shareschain.util.Convert;
import shareschain.util.Logger;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

import static shareschain.network.JSONResponses.*;

public final class ParameterParser {

    public static byte getByte(HttpServletRequest req, String name, byte min, byte max, boolean isMandatory) throws ParameterException {
        return getByte(req, name, min, max, (byte) 0, isMandatory);
    }

    public static byte getByte(HttpServletRequest req, String name, byte min, byte max, byte defaultValue, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return defaultValue;
        }
        try {
            byte value = Byte.parseByte(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(name, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static int getInt(HttpServletRequest req, String name, int min, int max, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        return getInt(name, paramValue, min, max);
    }

    public static int getInt(HttpServletRequest req, String name, int min, int max, int defaultValue) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return defaultValue;
        }
        return getInt(name, paramValue, min, max);
    }

    private static int getInt(String paramName, String paramValue, int min, int max) throws ParameterException {
        try {
            int value = Integer.parseInt(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(paramName, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(paramName, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static long getLong(HttpServletRequest req, String name, long min, long max,
                        boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        return getLong(name, paramValue, min, max);
    }

    public static long getLong(HttpServletRequest req, String name, long min, long max,
                               long defaultValue) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            return defaultValue;
        }
        return getLong(name, paramValue, min, max);
    }

    private static long getLong(String paramName, String paramValue, long min, long max) throws ParameterException {
        try {
            long value = Long.parseLong(paramValue);
            if (value < min || value > max) {
                throw new ParameterException(incorrect(paramName, String.format("value %d not in range [%d-%d]", value, min, max)));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(paramName, String.format("value %s is not numeric", paramValue)));
        }
    }

    public static long getUnsignedLong(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Convert.parseUnsignedLong(paramValue);
            if (value == 0) { // 0 is not allowed as an id
                throw new ParameterException(incorrect(name));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
    }


    public static byte[] getBytes(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return Convert.EMPTY_BYTE;
        }
        return Convert.parseHexString(paramValue);
    }

    public static String getParameter(HttpServletRequest req, String name) throws ParameterException {
        String value = Convert.emptyToNull(req.getParameter(name));
        if (value == null) {
            throw new ParameterException(missing(name));
        }
        return value;
    }

    /**
     * 根据页面请求，返回账户信息
     * @param req 页面请求
     * @param isMandatory 如果请求中没哟账户信息是否抛出异常:true 抛出异常；false 不抛出异常，返回0
     * @return
     * @throws ParameterException
     */
    public static long getAccountId(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        return getAccountId(req, "account", isMandatory);
    }

    public static long getAccountId(HttpServletRequest req, String name, boolean isMandatory) throws ParameterException {
        String paramValue = Convert.emptyToNull(req.getParameter(name));
        if (paramValue == null) {
            if (isMandatory) {
                throw new ParameterException(missing(name));
            }
            return 0;
        }
        try {
            long value = Convert.parseAccountId(paramValue);
            if (value == 0) {
                throw new ParameterException(incorrect(name));
            }
            return value;
        } catch (RuntimeException e) {
            throw new ParameterException(incorrect(name));
        }
    }

    public static long[] getAccountIds(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        String[] paramValues = req.getParameterValues("account");
        if (paramValues == null || paramValues.length == 0) {
            if (isMandatory) {
                throw new ParameterException(MISSING_ACCOUNT);
            } else {
                return Convert.EMPTY_LONG;
            }
        }
        long[] values = new long[paramValues.length];
        try {
            for (int i = 0; i < paramValues.length; i++) {
                if (paramValues[i] == null || paramValues[i].isEmpty()) {
                    throw new ParameterException(INCORRECT_ACCOUNT);
                }
                values[i] = Convert.parseAccountId(paramValues[i]);
                if (values[i] == 0) {
                    throw new ParameterException(INCORRECT_ACCOUNT);
                }
            }
        } catch (RuntimeException e) {
            throw new ParameterException(INCORRECT_ACCOUNT);
        }
        return values;
    }

    public static long getAmountKER(HttpServletRequest req) throws ParameterException {
        return getLong(req, "amountKER", 1L, Constants.MAX_BALANCE_KER, true);
    }


    public static String getSecretPhrase(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        String secretPhrase = Convert.emptyToNull(req.getParameter("secretPhrase"));
        if (secretPhrase == null && isMandatory) {
            throw new ParameterException(MISSING_SECRET_PHRASE);
        }
        return secretPhrase;
    }

    public static byte[] getPublicKey(HttpServletRequest req) throws ParameterException {
        return getPublicKey(req, null);
    }

    public static byte[] getPublicKey(HttpServletRequest req, String prefix) throws ParameterException {
        String secretPhraseParam = prefix == null ? "secretPhrase" : (prefix + "SecretPhrase");
        String publicKeyParam = prefix == null ? "publicKey" : (prefix + "PublicKey");
        String secretPhrase = Convert.emptyToNull(req.getParameter(secretPhraseParam));
        if (secretPhrase == null) {
            try {
                byte[] publicKey = Convert.parseHexString(Convert.emptyToNull(req.getParameter(publicKeyParam)));
                if (publicKey == null) {
                    throw new ParameterException(missing(secretPhraseParam, publicKeyParam));
                }
                if (!Crypto.isCanonicalPublicKey(publicKey)) {
                    throw new ParameterException(incorrect(publicKeyParam));
                }
                return publicKey;
            } catch (RuntimeException e) {
                throw new ParameterException(incorrect(publicKeyParam));
            }
        } else {
            return Crypto.getPublicKey(secretPhrase);
        }
    }

    public static Account getSenderAccount(HttpServletRequest req) throws ParameterException {
        byte[] publicKey = getPublicKey(req);
        Account account = Account.getAccount(publicKey);
        if (account == null) {
            throw new ParameterException(UNKNOWN_ACCOUNT);
        }
        return account;
    }

    public static Account getAccount(HttpServletRequest req) throws ParameterException {
        return getAccount(req, true);
    }

    public static Account getAccount(HttpServletRequest req, boolean isMandatory) throws ParameterException {
        long accountId = getAccountId(req, "account", isMandatory);
        if (accountId == 0 && !isMandatory) {
            return null;
        }
        Account account = Account.getAccount(accountId);
        if (account == null) {
            throw new ParameterException(JSONResponses.unknownAccount(accountId));
        }
        return account;
    }


    public static int getTimestamp(HttpServletRequest req) throws ParameterException {
        return getInt(req, "timestamp", 0, Integer.MAX_VALUE, false);
    }

    public static int getFirstIndex(HttpServletRequest req) {
        try {
            int firstIndex = Integer.parseInt(req.getParameter("firstIndex"));
            if (firstIndex < 0) {
                return 0;
            }
            return firstIndex;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getLastIndex(HttpServletRequest req) {
        int lastIndex = Integer.MAX_VALUE;
        try {
            lastIndex = Integer.parseInt(req.getParameter("lastIndex"));
            if (lastIndex < 0) {
                lastIndex = Integer.MAX_VALUE;
            }
        } catch (NumberFormatException ignored) {}
        if (!API.checkPassword(req)) {
            int firstIndex = Math.min(getFirstIndex(req), Integer.MAX_VALUE - API.maxRecords + 1);
            lastIndex = Math.min(lastIndex, firstIndex + API.maxRecords - 1);
        }
        return lastIndex;
    }

    public static int getNumberOfConfirmations(HttpServletRequest req) throws ParameterException {
        return getInt(req, "numberOfConfirmations", 0, Shareschain.getBlockchain().getHeight(), false);
    }

    public static int getHeight(HttpServletRequest req) throws ParameterException {
        return getInt(req, "height", 0, Shareschain.getBlockchain().getHeight(), -1);
    }

    public static ChainTransactionId getChainTransactionId(HttpServletRequest req, String name) throws ParameterException {
        String value = Convert.emptyToNull(req.getParameter(name));
        if (value == null) {
            return null;
        }
        return getChainTransactionId(name, value);
    }


    private static ChainTransactionId getChainTransactionId(String name, String value) throws ParameterException {
        String[] s = value.split(":");
        if (s.length != 2) {
            throw new ParameterException(JSONResponses.incorrect(name, "must be in chainId:fullHash format"));
        }
        try {
            int chainId = Integer.parseInt(s[0]);
            Chain chain = Chain.getChain(chainId);
            if (chain == null) {
                throw new ParameterException(UNKNOWN_CHAIN);
            }
            byte[] hash = Convert.parseHexString(s[1]);
            if (hash == null || hash.length != 32) {
                throw new ParameterException(JSONResponses.incorrect(name, "invalid fullHash length"));
            }
            return new ChainTransactionId(chainId, hash);
        } catch (NumberFormatException e) {
            throw new ParameterException(JSONResponses.incorrect(name, "must be in chainId:fullHash format"));
        }
    }

    public static Transaction.Builder parseTransaction(String transactionJSON, String transactionBytes, String prunableAttachmentJSON) throws ParameterException {
        if (transactionBytes == null && transactionJSON == null) {
            throw new ParameterException(MISSING_TRANSACTION_BYTES_OR_JSON);
        }
        if (transactionBytes != null && transactionJSON != null) {
            throw new ParameterException(either("transactionBytes", "transactionJSON"));
        }
        if (prunableAttachmentJSON != null && transactionBytes == null) {
            throw new ParameterException(JSONResponses.missing("transactionBytes"));
        }
        if (transactionJSON != null) {
            try {
                JSONObject json = (JSONObject) JSONValue.parseWithException(transactionJSON);
                return Shareschain.newTransactionBuilder(json);
            } catch (ShareschainException.ValidationException | RuntimeException | ParseException e) {
                Logger.logDebugMessage(e.getMessage(), e);
                JSONObject response = new JSONObject();
                JSONData.putException(response, e, "Incorrect transactionJSON");
                throw new ParameterException(response);
            }
        } else {
            try {
                byte[] bytes = Convert.parseHexString(transactionBytes);
                JSONObject prunableAttachments = prunableAttachmentJSON == null ? null : (JSONObject)JSONValue.parseWithException(prunableAttachmentJSON);
                return Shareschain.newTransactionBuilder(bytes, prunableAttachments);
            } catch (ShareschainException.ValidationException|RuntimeException | ParseException e) {
                Logger.logDebugMessage(e.getMessage(), e);
                JSONObject response = new JSONObject();
                JSONData.putException(response, e, "Incorrect transactionBytes");
                throw new ParameterException(response);
            }
        }
    }


    public static Chain getChain(HttpServletRequest request) throws ParameterException {
        return getChain(request, true);
    }

    public static Chain getChain(HttpServletRequest request, boolean isMandatory) throws ParameterException {
        String chainName = Convert.emptyToNull(request.getParameter("chain"));
        if (chainName != null) {
            Chain chain = Chain.getChain(chainName.toUpperCase(Locale.ROOT));
            if (chain == null) {
                try {
                    chain = Chain.getChain(Integer.valueOf(chainName));
                } catch (NumberFormatException ignore) {}
                if (chain == null) {
                    throw new ParameterException(UNKNOWN_CHAIN);
                }
            }
            return chain;
        } else if (isMandatory) {
            throw new ParameterException(MISSING_CHAIN);
        } else {
            return null;
        }
    }

    public static Chain getChain(HttpServletRequest request, String name, boolean isMandatory) throws ParameterException {
        String chainName = Convert.emptyToNull(request.getParameter(name));
        if (chainName != null) {
            Chain chain = Chain.getChain(chainName.toUpperCase(Locale.ROOT));
            if (chain == null) {
                try {
                    chain = Chain.getChain(Integer.valueOf(chainName));
                } catch (NumberFormatException ignore) {}
                if (chain == null) {
                    throw new ParameterException(JSONResponses.unknown(name));
                }
            }
            return chain;
        } else if (isMandatory) {
            throw new ParameterException(JSONResponses.missing(name));
        }
        return null;
    }



    private ParameterParser() {} // never


}

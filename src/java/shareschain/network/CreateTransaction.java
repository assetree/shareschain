
package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.account.Account;
import shareschain.account.PublicKeyAnnouncementAppendix;
import shareschain.blockchain.Attachment;
import shareschain.blockchain.Chain;
import shareschain.blockchain.ChainTransactionId;
import shareschain.blockchain.SmcTransactionType;
import shareschain.blockchain.Transaction;
import shareschain.util.crypto.Crypto;
import shareschain.util.Convert;
import shareschain.util.JSON;
import shareschain.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static shareschain.network.JSONResponses.FEATURE_NOT_AVAILABLE;
import static shareschain.network.JSONResponses.INCORRECT_EC_BLOCK;
import static shareschain.network.JSONResponses.MISSING_SECRET_PHRASE;
import static shareschain.network.JSONResponses.NOT_ENOUGH_FUNDS;

abstract class CreateTransaction extends APIServlet.APIRequestHandler {

    private static final String[] commonParameters = new String[]{"secretPhrase", "publicKey", "feeKER", "feeRateKERPerSCTK",
            "deadline", "referencedTransaction", "broadcast",
            "message", "messageIsText", "messageIsPrunable",
            "messageToEncrypt", "messageToEncryptIsText", "encryptedMessageData", "encryptedMessageNonce", "encryptedMessageIsPrunable", "compressMessageToEncrypt",
            "messageToEncryptToSelf", "messageToEncryptToSelfIsText", "encryptToSelfMessageData", "encryptToSelfMessageNonce", "compressMessageToEncryptToSelf",
            "phased", "phasingFinishHeight", "phasingVotingModel", "phasingQuorum", "phasingMinBalance", "phasingHolding", "phasingMinBalanceModel",
            "phasingWhitelisted", "phasingWhitelisted", "phasingWhitelisted",
            "phasingLinkedTransaction", "phasingLinkedTransaction", "phasingLinkedTransaction",
            "phasingHashedSecret", "phasingHashedSecretAlgorithm", "phasingParams", "phasingSubPolls",
            "phasingSenderPropertySetter", "phasingSenderPropertyName",
            "phasingSenderPropertyValue", "phasingRecipientPropertySetter",
            "phasingRecipientPropertyName", "phasingRecipientPropertyValue",
            "phasingExpression",
            "recipientPublicKey",
            "ecBlockId", "ecBlockHeight"};

    private static String[] addCommonParameters(String[] parameters) {
        String[] result = Arrays.copyOf(parameters, parameters.length + commonParameters.length);
        System.arraycopy(commonParameters, 0, result, parameters.length, commonParameters.length);
        return result;
    }

    CreateTransaction(APITag[] apiTags, String... parameters) {
        super(apiTags, addCommonParameters(parameters));
        if (!getAPITags().contains(APITag.CREATE_TRANSACTION)) {
            throw new RuntimeException("CreateTransaction API " + getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
        }
    }

    CreateTransaction(String fileParameter, APITag[] apiTags, String... parameters) {
        super(fileParameter, apiTags, addCommonParameters(parameters));
        if (!getAPITags().contains(APITag.CREATE_TRANSACTION)) {
            throw new RuntimeException("CreateTransaction API " + getClass().getName() + " is missing APITag.CREATE_TRANSACTION tag");
        }
    }

    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, Attachment attachment)
            throws ShareschainExceptions {
        return createTransaction(req, senderAccount, 0, 0, attachment, null);
    }

    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, Attachment attachment,
            Chain txChain) throws ShareschainExceptions {
        return createTransaction(req, senderAccount, 0, 0, attachment, txChain);
    }

    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, long recipientId,
                                            long amountKER, Attachment attachment) throws ShareschainExceptions {
        return createTransaction(req, senderAccount, recipientId, amountKER, attachment, null);
    }

    /**
     * 根据接收到的参数创建交易并广播
     * @param req
     * @param senderAccount
     * @param recipientId
     * @param amountKER
     * @param attachment
     * @param txChain
     * @return
     * @throws ShareschainExceptions
     */
    final JSONStreamAware createTransaction(HttpServletRequest req, Account senderAccount, long recipientId,
                                            long amountKER, Attachment attachment, Chain txChain) throws ShareschainExceptions {
        //根据参数referencedTransaction(客户端对应的参考交易哈希) 获取链的交易id
        // 感觉ChainTransactionId的作用不是字面表达的意思
        ChainTransactionId referencedTransactionId = ParameterParser.getChainTransactionId(req, "referencedTransaction");
        //根据参数获取发送者密码：secretPhrase
        String secretPhrase = ParameterParser.getSecretPhrase(req, false);
        //获取publicKey 接收者的公钥
        String publicKeyValue = Convert.emptyToNull(req.getParameter("publicKey"));
        //获取是否广播交易,默认是广播
        boolean broadcast = !"false".equalsIgnoreCase(req.getParameter("broadcast")) && secretPhrase != null;

        //封装公钥公告的信息
        PublicKeyAnnouncementAppendix publicKeyAnnouncement = null;
        String recipientPublicKey = Convert.emptyToNull(req.getParameter("recipientPublicKey"));
        if (recipientPublicKey != null) {//接收人公钥解析成bytes数组字符串
            publicKeyAnnouncement = new PublicKeyAnnouncementAppendix(Convert.parseHexString(recipientPublicKey));
        }

        if (secretPhrase == null && publicKeyValue == null) {//密码为空或者公钥为空
            return MISSING_SECRET_PHRASE;
        }

        short deadline = (short)ParameterParser.getInt(req, "deadline", 1, Short.MAX_VALUE, 15);
        long feeKER = ParameterParser.getLong(req, "feeKER", -1L, Constants.MAX_BALANCE_KER, -1L);
        //获取已知的最后区块的信息
        int ecBlockHeight = ParameterParser.getInt(req, "ecBlockHeight", 0, Integer.MAX_VALUE, false);
        long ecBlockId = Convert.parseUnsignedLong(req.getParameter("ecBlockId"));
        if (ecBlockId != 0 && ecBlockId != Shareschain.getBlockchain().getBlockIdAtHeight(ecBlockHeight)) {
            return INCORRECT_EC_BLOCK;
        }
        if (ecBlockId == 0 && ecBlockHeight > 0) {
            ecBlockId = Shareschain.getBlockchain().getBlockIdAtHeight(ecBlockHeight);
        }
        //默认值为-1
        long feeRateKERPerSCTK = ParameterParser.getLong(req, "feeRateKERPerSCTK", -1L, Constants.MAX_BALANCE_KER, -1L);
        JSONObject response = new JSONObject();

        // shouldn't try to get publicKey from senderAccount as it may have not been set yet
        byte[] publicKey = secretPhrase != null ? Crypto.getPublicKey(secretPhrase) : Convert.parseHexString(publicKeyValue);

        // Allow the caller to specify the chain for the transaction instead of using
        // the 'chain' request parameter

        //  实际上是通过子链的id来获得已经创建的子链对象
        Chain chain;
        if (txChain == null) {
            chain = ParameterParser.getChain(req);
        } else {
            chain = txChain;
        }

        //构建交易信息
        try {
            Transaction.Builder builder = chain.newTransactionBuilder(publicKey, amountKER, feeKER, deadline, attachment);
            if (!(attachment.getTransactionType() instanceof SmcTransactionType)) {
                throw new ParameterExceptions(JSONResponses.incorrect("chain",
                        attachment.getTransactionType().getName() + " attachment not allowed for "
                                + chain.getName() + " chain"));
            }
            if (referencedTransactionId != null) {//主链上不支持 交易参考的hash(客户端传输)
                return JSONResponses.error("Referenced transactions not allowed for Shareschain transactions");
            }
            if (publicKeyAnnouncement != null) {//主链上不支持公钥的公告
                return JSONResponses.error("Public key announcement attachments not allowed for Shareschain transactions");
            }
            if (attachment.getTransactionType().canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            if (ecBlockId != 0) {
                builder.ecBlockId(ecBlockId);
                builder.ecBlockHeight(ecBlockHeight);
            }
            //通过密码对交易进行加密sha256签名
            Transaction transaction = builder.build(secretPhrase);
            try {
                long balance = chain.getBalanceHome().getBalance(senderAccount.getId()).getUnconfirmedBalance();
                if (Math.addExact(amountKER, transaction.getFee()) > balance) {
                    JSONObject infoJson = new JSONObject();
                    infoJson.put("errorCode", 6);
                    infoJson.put("errorDescription", "Not enough funds");
                    infoJson.put("amount", String.valueOf(amountKER));
                    infoJson.put("fee", String.valueOf(transaction.getFee()));
                    infoJson.put("balance", String.valueOf(balance));
                    infoJson.put("diff", String.valueOf(Math.subtractExact(Math.addExact(amountKER, transaction.getFee()), balance)));
                    infoJson.put("chain", chain.getId());
                    return JSON.prepare(infoJson);
                }
            } catch (ArithmeticException e) {
                Logger.logErrorMessage(String.format("amount %d fee %d", amountKER, transaction.getFee()), e);
                return NOT_ENOUGH_FUNDS;
            }
            JSONObject transactionJSON = JSONData.unconfirmedTransaction(transaction);
            response.put("transactionJSON", transactionJSON);
            try {
                response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
            } catch (ShareschainExceptions.NotYetEncryptedException ignore) {}
            if (secretPhrase != null) {
                response.put("fullHash", transactionJSON.get("fullHash"));
                response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
                response.put("signatureHash", transactionJSON.get("signatureHash"));
            }
            //获取交易的最低费用
            response.put("minimumFeeKER", String.valueOf(transaction.getMinimumFeeKER()));
            if (broadcast) {//广播交易
                //启动一个交易的线程池进行广播
                Shareschain.getTransactionProcessor().broadcast(transaction);
                response.put("broadcasted", true);
            } else {//如果交易不广播，就直接验证
                transaction.validate();
                response.put("broadcasted", false);
            }
        } catch (ShareschainExceptions.NotYetEnabledExceptions e) {
            return FEATURE_NOT_AVAILABLE;
        } catch (ShareschainExceptions.InsufficientBalanceExceptions e) {
            throw e;
        } catch (ShareschainExceptions.ValidationExceptions e) {
            if (broadcast) {
                response.clear();
            }
            response.put("broadcasted", false);
            JSONData.putException(response, e);
        }
        return response;

    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected final boolean isChainSpecific() {
        return true;
    }

}

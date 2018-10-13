/*
 * Copyright © 2013-2016 The Shareschain Core Developers.
 * Copyright © 2016-2018 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.blockchain.Chain;
import shareschain.blockchain.Mainchain;
import shareschain.blockchain.TransactionType;
import shareschain.util.crypto.HashFunction;
import shareschain.node.Node;
import shareschain.util.JSON;
import shareschain.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class GetConstants extends APIServlet.APIRequestHandler {

    static final GetConstants instance = new GetConstants();

    private static final class Holder {

        private static final JSONStreamAware CONSTANTS;

        static {
            try {
                JSONObject response = new JSONObject();
                response.put("accountPrefix", Constants.ACCOUNT_PREFIX);
                response.put("genesisBlockId", Long.toUnsignedString(Shareschain.getBlockchainProcessor().getGenesisBlockId()));
                response.put("epochBeginning", Constants.EPOCH_BEGINNING);
                response.put("maxChildBlockPayloadLength", Constants.MAX_CHILDBLOCK_PAYLOAD_LENGTH);
                response.put("maxNumberOfSmcTransactions", Constants.MAX_NUMBER_OF_SMC_TRANSACTIONS);
                response.put("maxNumberOfChildTransaction", Constants.MAX_NUMBER_OF_CHILD_TRANSACTIONS);
                JSONObject lastKnownBlock = new JSONObject();
                lastKnownBlock.put("id", Long.toUnsignedString(Constants.LAST_KNOWN_BLOCK_ID));
                lastKnownBlock.put("height", Constants.LAST_KNOWN_BLOCK);
                response.put("lastKnownBlock", lastKnownBlock);

                JSONObject transactionJSON = new JSONObject();
                TransactionType transactionType;

                response.put("transactionTypes", transactionJSON);

                JSONObject hashFunctions = new JSONObject();
                for (HashFunction hashFunction : HashFunction.values()) {
                    hashFunctions.put(hashFunction.toString(), hashFunction.getId());
                }
                response.put("hashAlgorithms", hashFunctions);


                JSONObject nodeStates = new JSONObject();
                for (Node.State nodeState : Node.State.values()) {
                    nodeStates.put(nodeState.toString(), nodeState.ordinal());
                }
                response.put("nodeStates", nodeStates);
                response.put("maxTaggedDataDataLength", Constants.MAX_TAGGED_DATA_DATA_LENGTH);

                JSONObject requestTypes = new JSONObject();
                for (Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.apiRequestHandlers.entrySet()) {
                    JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
                    handlerJSON.put("enabled", true);

                    if (handlerEntry.getValue().isChainSpecific()) {
                        JSONArray disabledForChains = new JSONArray();
                        APIEnum api = APIEnum.fromName(handlerEntry.getKey());
                        if (Mainchain.mainchain.getDisabledAPIs().contains(api)) {
                            disabledForChains.add(Mainchain.mainchain.getId());
                        }

                        if (disabledForChains.size() > 0) {
                            handlerJSON.put("disabledForChains", disabledForChains);
                        }
                    }

                    requestTypes.put(handlerEntry.getKey(), handlerJSON);
                }
                for (Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.disabledRequestHandlers.entrySet()) {
                    JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
                    handlerJSON.put("enabled", false);
                    requestTypes.put(handlerEntry.getKey(), handlerJSON);
                }
                response.put("requestTypes", requestTypes);

                JSONObject holdingTypes = new JSONObject();

                response.put("holdingTypes", holdingTypes);

                JSONObject apiTags = new JSONObject();
                for (APITag apiTag : APITag.values()) {
                    JSONObject tagJSON = new JSONObject();
                    tagJSON.put("name", apiTag.getDisplayName());
                    tagJSON.put("enabled", !API.disabledAPITags.contains(apiTag));

                    JSONArray disabledForChains = new JSONArray();
                    if (Mainchain.mainchain.getDisabledAPITags().contains(apiTag)) {
                        disabledForChains.add(Mainchain.mainchain.getId());
                    }

                    tagJSON.put("disabledForChains", disabledForChains);

                    apiTags.put(apiTag.name(), tagJSON);
                }
                response.put("apiTags", apiTags);

                JSONArray disabledAPIs = new JSONArray();
                Collections.addAll(disabledAPIs, API.disabledAPIs);
                response.put("disabledAPIs", disabledAPIs);

                JSONArray disabledAPITags = new JSONArray();
                API.disabledAPITags.forEach(apiTag -> disabledAPITags.add(apiTag.getDisplayName()));
                response.put("disabledAPITags", disabledAPITags);

                JSONArray notForwardedRequests = new JSONArray();
                notForwardedRequests.addAll(APIProxy.NOT_FORWARDED_REQUESTS);
                response.put("proxyNotForwardedRequests", notForwardedRequests);

                List<Chain> chains = new ArrayList<>();
                chains.add(Mainchain.mainchain);
                JSONObject chainsJSON = new JSONObject();
                chains.forEach(chain -> chainsJSON.put(chain.getName(), chain.getId()));
                response.put("chains", chainsJSON);

                JSONObject chainPropertiesJSON = new JSONObject();
                chains.forEach(chain -> {
                    JSONObject json = new JSONObject();
                    json.put("name", chain.getName());
                    json.put("id", chain.getId());
                    json.put("decimals", chain.getDecimals());
                    json.put("totalAmount", String.valueOf(chain.getTotalAmount()));
                    json.put("ONE_COIN", String.valueOf(chain.ONE_COIN));
                    JSONArray disabledTransactionTypes = new JSONArray();
                    chain.getDisabledTransactionTypes().forEach(type -> disabledTransactionTypes.add(type.getName()));
                    json.put("disabledTransactionTypes", disabledTransactionTypes);

                    JSONArray disabledAPITagsForChain = new JSONArray();
                    chain.getDisabledAPITags().forEach(tag -> disabledAPITagsForChain.add(tag.name()));
                    json.put("disabledAPITags", disabledAPITagsForChain);

                    chainPropertiesJSON.put(chain.getId(), json);
                });
                response.put("chainProperties", chainPropertiesJSON);
                response.put("initialBaseTarget", Long.toUnsignedString(Constants.INITIAL_BASE_TARGET));
                CONSTANTS = JSON.prepare(response);
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                throw e;
            }
        }
    }

    private GetConstants() {
        super(new APITag[] {APITag.INFO});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        return Holder.CONSTANTS;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

    public static JSONStreamAware getConstants() {
        return Holder.CONSTANTS;
    }
}

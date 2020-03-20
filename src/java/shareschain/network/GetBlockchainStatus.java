
package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.account.AccountChainLedger;
import shareschain.blockchain.Block;
import shareschain.blockchain.BlockchainProcessor;
import shareschain.node.Node;
import shareschain.node.Nodes;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;

public final class GetBlockchainStatus extends APIServlet.APIRequestHandler {

    static final GetBlockchainStatus instance = new GetBlockchainStatus();

    private GetBlockchainStatus() {
        super(new APITag[] {APITag.BLOCKS, APITag.INFO});
    }

    @Override
    protected JSONObject processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        response.put("application", Shareschain.APPLICATION);
        response.put("version", Shareschain.VERSION);
        response.put("time", Shareschain.getEpochTime());
        Block lastBlock = Shareschain.getBlockchain().getLastBlock();
        response.put("lastBlock", lastBlock.getStringId());
        response.put("cumulativeDifficulty", lastBlock.getCumulativeDifficulty().toString());
        response.put("numberOfBlocks", lastBlock.getHeight() + 1);
        BlockchainProcessor blockchainProcessor = Shareschain.getBlockchainProcessor();
        Node lastBlockchainFeeder = blockchainProcessor.getLastBlockchainFeeder();
        response.put("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
        response.put("lastBlockchainFeederHeight", blockchainProcessor.getLastBlockchainFeederHeight());
        response.put("isScanning", blockchainProcessor.isScanning());
        response.put("isDownloading", blockchainProcessor.isDownloading());
        response.put("maxRollback", Constants.MAX_ROLLBACK);
        response.put("currentMinRollbackHeight", Shareschain.getBlockchainProcessor().getMinRollbackHeight());
        response.put("isTestnet", Constants.isTestnet);
        response.put("maxPrunableLifetime", Constants.MAX_PRUNABLE_LIFETIME);
        response.put("includeExpiredPrunable", Constants.INCLUDE_EXPIRED_PRUNABLE);
        response.put("correctInvalidFees", Constants.correctInvalidFees);
        response.put("ledgerTrimKeep", AccountChainLedger.trimKeep);
        JSONArray servicesArray = new JSONArray();
        Nodes.getServices().forEach(service -> servicesArray.add(service.name()));
        response.put("services", servicesArray);
        if (APIProxy.isActivated()) {
            String servingNode = APIProxy.getInstance().getMainNodeAnnouncedAddress();
            response.put("apiProxy", true);
            response.put("apiProxyNode", servingNode);
        } else {
            response.put("apiProxy", false);
        }
        response.put("isLightClient", Constants.isLightClient);
        response.put("maxAPIRecords", API.maxRecords);
        response.put("blockchainState", Nodes.getMyBlockchainState());
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}

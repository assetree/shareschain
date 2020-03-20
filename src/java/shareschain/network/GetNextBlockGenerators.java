
package shareschain.network;

import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Block;
import shareschain.blockchain.Blockchain;
import shareschain.blockchain.Generator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>
 * The GetNextBlockGenerators API will return the next block generators ordered by the
 * hit time.  The list of active forgers is initialized using the block generators
 * with at least 2 blocks generated within the previous 10,000 blocks.  Accounts without
 * a public key will not be included.  The list is
 * updated as new blocks are processed.  This means the results will not be 100%
 * correct since previously active generators may no longer be running and new generators
 * won't be known until they generate a block.  This API will be replaced when transparent
 * forging is activated.
 * <p>
 * Request parameters:
 * <ul>
 * <li>limit - The number of forgers to return and defaults to 1.
 * </ul>
 * <p>
 * Return fields:
 * <ul>
 * <li>activeCount - The number of active generators
 * <li>height - The last block height
 * <li>lastBlock - The last block identifier
 * <li>timestamp - The last block timestamp
 * <li>generators - The next block generators
 * <ul>
 * <li>account - The account identifier
 * <li>accountRS - The account RS identifier
 * <li>deadline - The difference between the generation time and the last block timestamp
 * <li>effectiveBalanceSCTK - The account effective balance
 * <li>hitTime - The generation time for the account
 * </ul>
 * </ul>
 */
public final class GetNextBlockGenerators extends APIServlet.APIRequestHandler {

    static final GetNextBlockGenerators instance = new GetNextBlockGenerators();

    private GetNextBlockGenerators() {
        super(new APITag[] {APITag.FORGING}, "limit");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ShareschainExceptions {
        JSONObject response = new JSONObject();
        int limit = Math.max(1, ParameterParser.getInt(req, "limit", 1, Integer.MAX_VALUE, false));
        Blockchain blockchain = Shareschain.getBlockchain();
        blockchain.readLock();
        try {
            Block lastBlock = blockchain.getLastBlock();
            response.put("timestamp", lastBlock.getTimestamp());
            response.put("height", lastBlock.getHeight());
            response.put("lastBlock", Long.toUnsignedString(lastBlock.getId()));
            List<Generator.ActiveGenerator> activeGenerators = Generator.getNextGenerators();
            response.put("activeCount", activeGenerators.size());
            JSONArray generators = new JSONArray();
            for (Generator.ActiveGenerator generator : activeGenerators) {
                if (generator.getHitTime() > Integer.MAX_VALUE) {
                    break;
                }
                JSONObject resp = new JSONObject();
                JSONData.putAccount(resp, "account", generator.getAccountId());
                resp.put("effectiveBalanceSCTK", generator.getEffectiveBalance());
                resp.put("hitTime", generator.getHitTime());
                resp.put("deadline", (int)generator.getHitTime() - lastBlock.getTimestamp());
                generators.add(resp);
                if (generators.size() == limit) {
                    break;
                }
            }
            response.put("generators", generators);
        } finally {
            blockchain.readUnlock();
        }
        return response;
    }

    /**
     * No required block parameters
     *
     * @return                      FALSE to disable the required block parameters
     */
    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}


package shareschain.network;

import shareschain.ShareschainExceptions;
import org.json.simple.JSONStreamAware;

public final class ParameterExceptions extends ShareschainExceptions {

    private final JSONStreamAware errorResponse;

    ParameterExceptions(JSONStreamAware errorResponse) {
        this.errorResponse = errorResponse;
    }

    JSONStreamAware getErrorResponse() {
        return errorResponse;
    }

}


package shareschain.network;

import shareschain.ShareschainException;
import org.json.simple.JSONStreamAware;

public final class ParameterException extends ShareschainException {

    private final JSONStreamAware errorResponse;

    ParameterException(JSONStreamAware errorResponse) {
        this.errorResponse = errorResponse;
    }

    JSONStreamAware getErrorResponse() {
        return errorResponse;
    }

}

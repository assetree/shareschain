
package shareschain.network;

import shareschain.util.BooleanExpression;
import shareschain.util.Convert;
import shareschain.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class EvaluateExpression extends APIServlet.APIRequestHandler {

    static final EvaluateExpression instance = new EvaluateExpression();

    private EvaluateExpression() {
        super(new APITag[] {APITag.UTILS}, "expression", "checkOptimality", "evaluate", "vars", "vars", "vars", "values", "values", "values");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        String expression = Convert.emptyToNull(req.getParameter("expression"));
        if (expression == null) {
            return JSON.emptyJSON;
        }
        boolean checkOptimality = "true".equalsIgnoreCase(req.getParameter("checkOptimality"));
        boolean evaluate = "true".equalsIgnoreCase(req.getParameter("evaluate"));
        String[] vars = req.getParameterValues("vars");
        String[] values = req.getParameterValues("values");
        if (evaluate && (vars == null || vars.length == 0 || vars.length != values.length)) {
            return JSONResponses.error("Vars undefined or number of vars differs from number of values");
        }
        Map<String, BooleanExpression.Value> variableValues = new HashMap<>();
        if (evaluate) {
            for (int i = 0; i < vars.length; i++) {
                BooleanExpression.Value value;
                try {
                    value = BooleanExpression.Value.valueOf(values[i].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return JSONResponses.error(values[i] + " does not represent a valid boolean value, valid values are " + Arrays.toString(BooleanExpression.Value.values()));
                }
                variableValues.put(vars[i], value);
            }
        }
        BooleanExpression booleanExpression = new BooleanExpression(expression);
        if (booleanExpression.hasErrors(checkOptimality)) {
            return JSONResponses.booleanExpressionError(booleanExpression);
        }

        JSONObject response = new JSONObject();
        if (evaluate) {
            try {
                response.put("result", booleanExpression.evaluate(variableValues));
            } catch (BooleanExpression.BadSyntaxException e) {
                e.printStackTrace();
            }
        }
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(booleanExpression.getVariables());
        response.put("variables", jsonArray);
        response.put("literalsCount", booleanExpression.getLiteralsCount());
        return response;
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

}


package shareschain.network;

import shareschain.Constants;
import shareschain.Shareschain;
import shareschain.ShareschainExceptions;
import shareschain.blockchain.Chain;
import shareschain.database.DB;
import shareschain.util.JSON;
import shareschain.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static shareschain.network.JSONResponses.ERROR_DISABLED;
import static shareschain.network.JSONResponses.ERROR_INCORRECT_REQUEST;
import static shareschain.network.JSONResponses.ERROR_NOT_ALLOWED;
import static shareschain.network.JSONResponses.LIGHT_CLIENT_DISABLED_API;
import static shareschain.network.JSONResponses.POST_REQUIRED;
import static shareschain.network.JSONResponses.REQUIRED_BLOCK_NOT_FOUND;
import static shareschain.network.JSONResponses.REQUIRED_LAST_BLOCK_NOT_FOUND;

public final class APIServlet extends HttpServlet {

    public abstract static class APIRequestHandler {

        private final List<String> parameterList;
        private final String fileParameter;
        private final Set<APITag> apiTagSet;

        protected APIRequestHandler(APITag[] apiTagSet, String... parameters) {
            this(null, apiTagSet, parameters);
        }

        protected APIRequestHandler(String fileParameter, APITag[] apiTagSet, String... origParameters) {
            List<String> parameters = new ArrayList<>();
            if (isChainSpecific()) {
                parameters.add("chain");
            }
            Collections.addAll(parameters, origParameters);
            if ((requirePassword() || parameters.contains("lastIndex")) && ! API.disableAdminPassword) {
                parameters.add("adminPassword");
            }
            if (allowRequiredBlockParameters()) {
                parameters.add("requireBlock");
                parameters.add("requireLastBlock");
            }
            this.parameterList = Collections.unmodifiableList(parameters);
            this.apiTagSet = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(apiTagSet)));
            this.fileParameter = fileParameter;
        }

        public final List<String> getParameterList() {
            return parameterList;
        }

        public final Set<APITag> getAPITags() {
            return apiTagSet;
        }

        public final String getFileParameter() {
            return fileParameter;
        }

        public boolean isIgnisOnly() {
            return false;
        }

        protected abstract JSONStreamAware processRequest(HttpServletRequest request) throws ShareschainExceptions;

        protected JSONStreamAware processRequest(HttpServletRequest request, HttpServletResponse response) throws ShareschainExceptions {
            return processRequest(request);
        }

        protected boolean requirePost() {
            return false;
        }

        protected boolean startDBTransaction() {
            return false;
        }

        protected boolean requirePassword() {
            return false;
        }

        protected boolean allowRequiredBlockParameters() {
            return true;
        }

        protected boolean requireBlockchain() {
            return true;
        }

        protected boolean requireFullClient() {
            return false;
        }

        protected boolean isChainSpecific() {
            return true;
        }
        
        protected boolean isTextArea(String parameter) {
            return false;
        }

        protected boolean isPassword(String parameter) {
            return false;
        }

    }

    private static final boolean enforcePost = Shareschain.getBooleanProperty("shareschain.apiServerEnforcePOST");
    static final Map<String,APIRequestHandler> apiRequestHandlers;
    static final Map<String,APIRequestHandler> disabledRequestHandlers;

    static {

        Map<String,APIRequestHandler> map = new HashMap<>();
        Map<String,APIRequestHandler> disabledMap = new HashMap<>();

        /**
         * 将API枚举类型中注册的路径地址，初始化
         */
        for (APIEnum api : APIEnum.values()) {
            if (!api.getName().isEmpty() && api.getHandler() != null) {
                map.put(api.getName(), api.getHandler());
            }
        }


        API.disabledAPIs.forEach(api -> {
            APIRequestHandler handler = map.remove(api);
            if (handler == null) {
                throw new RuntimeException("Invalid API in shareschain.disabledAPIs: " + api);
            }
            disabledMap.put(api, handler);
        });
        API.disabledAPITags.forEach(apiTag -> {
            Iterator<Map.Entry<String, APIRequestHandler>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, APIRequestHandler> entry = iterator.next();
                if (entry.getValue().getAPITags().contains(apiTag)) {
                    disabledMap.put(entry.getKey(), entry.getValue());
                    iterator.remove();
                }
            }
        });
        if (!API.disabledAPIs.isEmpty()) {
            Logger.logInfoMessage("Disabled APIs: " + API.disabledAPIs);
        }
        if (!API.disabledAPITags.isEmpty()) {
            Logger.logInfoMessage("Disabled APITags: " + API.disabledAPITags);
        }

        apiRequestHandlers = Collections.unmodifiableMap(map);
        disabledRequestHandlers = disabledMap.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(disabledMap);
    }

    public static APIRequestHandler getAPIRequestHandler(String requestType) {
        return apiRequestHandlers.get(requestType);
    }

    static void initClass() {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        process(req, resp);
    }

    private void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Set response values now in case we create an asynchronous context
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        resp.setContentType("text/plain; charset=UTF-8");

        JSONStreamAware response = JSON.emptyJSON;
        long startTime = System.currentTimeMillis();

        try {

            if (!API.isAllowed(req.getRemoteHost())) {
                response = ERROR_NOT_ALLOWED;
                return;
            }
            String requestType = req.getParameter("requestType");
            if (requestType == null) {
                response = ERROR_INCORRECT_REQUEST;
                return;
            }

            /**
             * 根据请求类型实例化request类
             */
            APIRequestHandler apiRequestHandler = apiRequestHandlers.get(requestType);
            if (apiRequestHandler == null) {
                if (disabledRequestHandlers.containsKey(requestType)) {
                    response = ERROR_DISABLED;
                } else {
                    response = ERROR_INCORRECT_REQUEST;
                }
                return;
            }

            if (apiRequestHandler.isChainSpecific()) {
                Chain chain = ParameterParser.getChain(req, false);
                if (chain != null) {
                    if (chain.getDisabledAPIs().contains(APIEnum.fromName(requestType))) {
                        response = ERROR_DISABLED;
                        return;
                    }
                }
            }

            if (Constants.isLightClient && apiRequestHandler.requireFullClient()) {
                response = LIGHT_CLIENT_DISABLED_API;
                return;
            }

            if (enforcePost && apiRequestHandler.requirePost() && !"POST".equals(req.getMethod())) {
                response = POST_REQUIRED;
                return;
            }

            if (apiRequestHandler.requirePassword()) {
                API.verifyPassword(req);
            }
            final long requireBlockId = apiRequestHandler.allowRequiredBlockParameters() ?
                    ParameterParser.getUnsignedLong(req, "requireBlock", false) : 0;
            final long requireLastBlockId = apiRequestHandler.allowRequiredBlockParameters() ?
                    ParameterParser.getUnsignedLong(req, "requireLastBlock", false) : 0;
            if (requireBlockId != 0 || requireLastBlockId != 0) {
                Shareschain.getBlockchain().readLock();
            }
            try {
                try {
                    if (apiRequestHandler.startDBTransaction()) {
                        DB.db.beginTransaction();
                    }
                    if (requireBlockId != 0 && !Shareschain.getBlockchain().hasBlock(requireBlockId)) {
                        response = REQUIRED_BLOCK_NOT_FOUND;
                        return;
                    }
                    if (requireLastBlockId != 0 && requireLastBlockId != Shareschain.getBlockchain().getLastBlock().getId()) {
                        response = REQUIRED_LAST_BLOCK_NOT_FOUND;
                        return;
                    }
                    /**
                     * 调用实例化类型，获取请求返回值
                     */
                    response = apiRequestHandler.processRequest(req, resp);
                    if (requireLastBlockId == 0 && requireBlockId != 0 && response instanceof JSONObject) {
                        ((JSONObject) response).put("lastBlock", Shareschain.getBlockchain().getLastBlock().getStringId());
                    }
                } finally {
                    if (apiRequestHandler.startDBTransaction()) {
                        DB.db.endTransaction();
                    }
                }
            } finally {
                if (requireBlockId != 0 || requireLastBlockId != 0) {
                    Shareschain.getBlockchain().readUnlock();
                }
            }
        } catch (ParameterExceptions e) {
            response = e.getErrorResponse();
        } catch (ShareschainExceptions | RuntimeException e) {
            Logger.logDebugMessage("Error processing API request", e);
            JSONObject json = new JSONObject();
            JSONData.putException(json, e);
            response = JSON.prepare(json);
        } catch (ExceptionInInitializerError err) {
            Logger.logErrorMessage("Initialization Error", err.getCause());
            response = ERROR_INCORRECT_REQUEST;
        } catch (Exception e) {
            Logger.logErrorMessage("Error processing request", e);
            response = ERROR_INCORRECT_REQUEST;
        } finally {
            // The response will be null if we created an asynchronous context
            if (response != null) {
                if (response instanceof JSONObject) {
                    ((JSONObject) response).put("requestProcessingTime", System.currentTimeMillis() - startTime);
                }
                try (Writer writer = resp.getWriter()) {
                    JSON.writeJSONString(response, writer);
                }
            }
        }

    }

}

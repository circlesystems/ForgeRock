package ai.circle.service;

import com.google.inject.assistedinject.Assisted;
import com.google.common.collect.ImmutableList;
import com.google.common.base.Strings;

import org.apache.commons.lang.StringEscapeUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.util.i18n.PreferredLocales;
import static org.forgerock.openam.auth.node.api.Action.send;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.TreeContext;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.ResourceBundle;

import ai.circle.CircleUtil;
import ai.circle.Crypto;

import org.json.*;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * This node Authorizes the usage of the Circle Service by getting a Token from
 * Circle Servers. The Token is added to "Sharedstate" and passed to the other
 * Circle Nodes
 */

@Node.Metadata(outcomeProvider = CircleAuthorizeNode.OutcomeProvider.class, //
        configClass = CircleAuthorizeNode.Config.class, tags = "basic authentication" //
)

public class CircleAuthorizeNode implements Node {

    private final static String TRUE_OUTCOME_ID = "authorizeTrue";
    private final static String FALSE_OUTCOME_ID = "authorizeFalse";
    private final String scriptName = "/js/authorize.js";

    public interface Config {

        @Attribute(order = 10, validators = { RequiredValueValidator.class })

        default String appKey() {
            return "";
        }

        @Attribute(order = 20, validators = { RequiredValueValidator.class })

        default String secretKey() {
            return "";
        }

        @Attribute(order = 30, validators = { RequiredValueValidator.class })
        default String customerCode() {
            return "";
        }

        @Attribute(order = 40, validators = { RequiredValueValidator.class })
        default String apiUrl() {
            return "https://api.gocircle.ai/api/token";
        }
    }

    private final Config config;

    /**
     * Create the node.
     * 
     * @param config The service config.
     */
    @Inject
    public CircleAuthorizeNode(@Assisted Config config) {
        this.config = config;
    }

    private static String tokenInstance = "";

    @Override
    public Action process(TreeContext context) {

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript

        if (result.isPresent()) {
            JsonValue newSharedState = context.sharedState.copy();
            String resultString = result.get();

            newSharedState.put("CircleToken", tokenInstance);
            newSharedState.put("CircleAppKey", config.appKey());

            tokenInstance = "";
            Boolean returnStatus = false;

            if (!resultString.isEmpty() && !resultString.equals(CircleUtil.OUT_PARAMETER)) {
                returnStatus = true;
            }

            return goTo(returnStatus).replaceSharedState(newSharedState).build();
        } else {

            String apiUrl = config.apiUrl();
            String customerCode = config.customerCode();
            String appKey = config.appKey();

            if (tokenInstance.equals("")) {
                tokenInstance = getToken(customerCode, appKey, apiUrl);
            }

            if (tokenInstance == null || tokenInstance.equals("")) {
                return goTo(false).build();
            }

            String circleNodeScript = CircleUtil.readFileString(scriptName);

            circleNodeScript = circleNodeScript.replace("$appKey$", config.appKey());
            circleNodeScript = circleNodeScript.replace("$token$", tokenInstance);

            // Additional javascript to authorize the user
            circleNodeScript += "const isAuthorized = await isAuthorizedNode();\n";
            circleNodeScript += "await autoSubmit();\n";
            circleNodeScript += "output.value = isAuthorized;\n";

            String clientSideScriptExecutorFunction = CircleUtil.createClientSideScriptExecutorFunction(
                    circleNodeScript, CircleUtil.OUT_PARAMETER, true, context.sharedState.toString());
            ScriptTextOutputCallback scriptAndSelfSubmitCallback = new ScriptTextOutputCallback(
                    clientSideScriptExecutorFunction);

            HiddenValueCallback hiddenValueCallback = new HiddenValueCallback(CircleUtil.OUT_PARAMETER);
            ImmutableList<Callback> callbacks = ImmutableList.of(scriptAndSelfSubmitCallback, hiddenValueCallback);

            return send(callbacks).build();
        }
    }

    private String getToken(String customerCode, String appKey, String apiUrl) {

        try {
            Random rndGen = new Random();

            String apiUrlParam = "customerId=" //
                    + customerCode //
                    + "&appKey=" + appKey //
                    + "&endUserId=userman" //
                    + "&nonce=" + String.valueOf(rndGen.nextInt());

            String signature = Crypto.hmac_sha256(config.secretKey(), apiUrlParam);
            URL url = new URL(apiUrl + "?" + apiUrlParam + "&signature=" + signature);

            // Build HTTP request
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            int status = conn.getResponseCode();
            if (status != 200) {
                return null;
            }

            String response = CircleUtil.readAllLines(conn.getInputStream());
            String json = StringEscapeUtils.unescapeJava(response);
            json = json.substring(1, json.length() - 1);

            JSONObject jret = new JSONObject(json);
            String token = jret.getString("Token");

            if (token != null) {
                return token;
            }

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleAuthorizeNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)),
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));
        }
    }
}
package ai.circle.service;

import ai.circle.CircleUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.security.auth.callback.Callback;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;
import static org.forgerock.openam.auth.node.api.Action.send;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * This node checks if there is a refresh token stored in Circle Service.
 */

@Node.Metadata(outcomeProvider = CircleVerifyTokenExistenceNode.OutcomeProvider.class, //
        configClass = CircleVerifyTokenExistenceNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleVerifyTokenExistenceNode implements Node {
    private final Config config;
    private final static String TRUE_OUTCOME_ID = "tokenExistTrue";
    private final static String FALSE_OUTCOME_ID = "tokenExistFalse";
    private final String scriptName = "/js/authorize.js";

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 10, validators = { RequiredValueValidator.class })
        default String tokenName() {
            return "";
        }
    }

    /**
     * Create the node.
     *
     * @param config The service config.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public CircleVerifyTokenExistenceNode(@Assisted Config config) throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript
        if (result.isPresent()) {

            String resultString = result.get();
            Boolean returnStatus = false;

            if (!resultString.equals(CircleUtil.OUT_PARAMETER) && !resultString.trim().isEmpty()) {
                returnStatus = true;

                // adds the refresh token to transientState for Circle Exchange Refresh Token
                // Node
                context.transientState.add("refresh_token", resultString);
            }
            return goTo(returnStatus).build();

        } else {

            String circleNodeScript = "";

            try {
                circleNodeScript = CircleUtil.readFileString(scriptName);

                JsonValue sharedState = context.sharedState.copy();

                String appKey = sharedState.get("CircleAppKey").toString();
                String appToken = sharedState.get("CircleToken").toString();
                String tokenName = config.tokenName();

                if (appToken == null || appToken.equals("")) {
                    return goTo(false).build();
                }
                circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
                circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

                String endString = String.format(" const savedToken = await getCircleSavedToken('%s')\n", tokenName);

                circleNodeScript += endString;
                circleNodeScript += "output.value = savedToken;\n";
                circleNodeScript += "await autoSubmit();\n";

            } catch (Exception e) {
                e.printStackTrace();
            }

            String clientSideScriptExecutorFunction = CircleUtil.createClientSideScriptExecutorFunction(
                    circleNodeScript, CircleUtil.OUT_PARAMETER, true, context.sharedState.toString());
            ScriptTextOutputCallback scriptAndSelfSubmitCallback = new ScriptTextOutputCallback(
                    clientSideScriptExecutorFunction);

            HiddenValueCallback hiddenValueCallback = new HiddenValueCallback(CircleUtil.OUT_PARAMETER);
            ImmutableList<Callback> callbacks = ImmutableList.of(scriptAndSelfSubmitCallback, hiddenValueCallback);

            return send(callbacks).build();
        }
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleVerifyTokenExistenceNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of( //
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString("tokenExistTrue")), //
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString("tokenExistFalse")));//
        }
    }
}
package ai.circle.service;

import ai.circle.CircleUtil;

import java.util.List;

import java.util.Optional;
import java.util.ResourceBundle;
import javax.security.auth.callback.Callback;
import javax.inject.Inject;

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
 * A node that reads the refresh token from the transient state {refresh_token}
 * and stores it into the Circle Service
 */

@Node.Metadata(outcomeProvider = CircleSaveTokenNode.OutcomeProvider.class, //
        configClass = CircleSaveTokenNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleSaveTokenNode implements Node {

    private final Config config;
    private final String scriptName = "/js/autorize.js";
    private final static String TRUE_OUTCOME_ID = "savedTrue";
    private final static String FALSE_OUTCOME_ID = "savedFalse";
    private static String refreshToken = "";

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
    public CircleSaveTokenNode(@Assisted Config config) throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        JsonValue newSharedState = context.sharedState.copy();

        if (!context.transientState.get("refresh_token").toString().isEmpty()) {
            refreshToken = context.transientState.get("refresh_token").toString();
            refreshToken = refreshToken.replace("\"", "");
        }

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript
        if (result.isPresent()) {
            String resultString = result.get();

            return goTo(Boolean.parseBoolean(resultString)).build();
        } else {
            String circleNodeScript = "";

            try {
                circleNodeScript = CircleUtil.CORE_SCRIPT;

                String appKey = newSharedState.get("CircleAppKey").toString();
                String appToken = newSharedState.get("CircleToken").toString();

                if (appToken == null || appToken.equals("")) {

                    return goTo(false).build();
                }

                circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
                circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

                String tokenName = config.tokenName();

                String endString = String.format(" const isSaved = await saveToken('%s','%s');\n" //
                        , tokenName, refreshToken);

                circleNodeScript += endString;
                circleNodeScript += "output.value = isSaved;\n";
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
        private static final String BUNDLE = CircleSaveTokenNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of( //
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString("savedTrue")), //
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString("savedFalse")));//
        }
    }
}
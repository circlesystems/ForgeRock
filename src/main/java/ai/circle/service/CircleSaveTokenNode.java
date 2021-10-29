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
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.forgerock.openam.auth.node.api.Action.send;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * A node that reads the refresh token from the transient state {refresh_token}
 * and stores it in the Circle Service
 */

@Node.Metadata(outcomeProvider = CircleSaveTokenNode.OutcomeProvider.class, //
        configClass = CircleSaveTokenNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleSaveTokenNode implements Node {
    private final Config config;
    private final static String TRUE_OUTCOME_ID = "savedTrue";
    private final static String FALSE_OUTCOME_ID = "savedFalse";
    private final static Logger logger = LoggerFactory.getLogger(CircleSaveTokenNode.class);

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
                NodeState newNodeState = context.getStateFor(this);

                String refreshToken = newNodeState.get("refresh_token").toString();
                refreshToken = refreshToken.replace("\"", "");

                String appKey = newNodeState.get("CircleAppKey").toString();
                String appToken = newNodeState.get("CircleToken").toString();

                if (appToken == null || appToken.equals("")) {
                    logger.error("No appKey or appToken ");
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
                logger.error("Error saving refresh token. ", e);
                throw new NodeProcessException(e);
            }

            ImmutableList<Callback> callbacks = CircleUtil.getScriptAndSelfSubmitCallback(circleNodeScript);

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
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)), //
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));//
        }
    }
}
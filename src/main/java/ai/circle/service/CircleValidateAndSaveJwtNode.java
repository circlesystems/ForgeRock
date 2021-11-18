package ai.circle.service;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.security.auth.callback.Callback;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.sm.annotations.adapters.Password;
import org.forgerock.util.i18n.PreferredLocales;
import org.json.JSONException;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

import static org.forgerock.openam.auth.node.api.Action.send;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.sm.RequiredValueValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.circle.CircleUtil;
import ai.circle.JwtToken;

/**
 * This node checks whether a JWT is stored in the Circle Service, whether the
 * token is valid, and whether it has not yet expired. If the token does not
 * exist, a new JWT is created and stored securely.
 */

@Node.Metadata(outcomeProvider = CircleValidateAndSaveJwtNode.OutcomeProvider.class, //
        configClass = CircleValidateAndSaveJwtNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleValidateAndSaveJwtNode implements Node {
    private final static String JWT_TOKEN_NAME = "circle-jwt";
    private final static Logger logger = LoggerFactory.getLogger(CircleValidateAndSaveJwtNode.class);
    private final Config config;
    private final static String TRUE_OUTCOME_ID = "isValidTrue";
    private final static String FALSE_OUTCOME_ID = "isValidFalse";
    private final static String scriptName = "/js/authorize.js";

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 10, validators = { RequiredValueValidator.class })
        @Password
        char[] secret();
    }

    /**
     * Create the node.
     *
     * @param config The service config.
     */
    @Inject
    public CircleValidateAndSaveJwtNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) {

        NodeState newNodeState = context.getStateFor(this);

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript
        if (result.isPresent()) {

            String jwtFromCircle = result.get();
            Boolean isTokenValid = false;
            String userName = "";

            JwtToken circleJwtToken = new JwtToken();

            try {
                isTokenValid = circleJwtToken.isTokenValid(jwtFromCircle, String.valueOf(config.secret()));
            } catch (Exception e) {
                logger.error("Error validating the JWT." + e.getMessage());
                goTo(false).build();
            }

            try {
                userName = circleJwtToken.userNameFromToken(jwtFromCircle);

            } catch (JSONException e) {
                logger.error("Error getting username from JWT");
                goTo(false).build();
            }

            newNodeState.putShared(USERNAME, userName.replace("\"", ""));

            return goTo(isTokenValid).build();

        } else {
            String appKey = newNodeState.get("CircleAppKey").toString();
            String appToken = newNodeState.get("CircleToken").toString();
            String circleNodeScript = CircleUtil.readFileString(scriptName);

            if (appToken == null || appToken.equals("")) {
                return goTo(false).build();
            }
            circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
            circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

            String endString = String.format(" const savedToken = await getCircleSavedToken('%s')\n", JWT_TOKEN_NAME);

            circleNodeScript += endString;
            circleNodeScript += "output.value = savedToken;\n";
            circleNodeScript += "await autoSubmit();\n";

            ImmutableList<Callback> callbacks = CircleUtil.getScriptAndSelfSubmitCallback(circleNodeScript);

            return send(callbacks).build();
        }

    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleValidateAndSaveJwtNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of( //
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)), //
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));//
        }
    }
}
package ai.circle.service;

import static org.forgerock.openam.auth.node.api.Action.send;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;

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
 * This node generates a JWT using the secret and stores it on Circle Service.
 */

@Node.Metadata(outcomeProvider = CircleGenerateSaveJwtNode.OutcomeProvider.class, //
        configClass = CircleGenerateSaveJwtNode.Config.class, tags = "basic authentication" //
)

public class CircleGenerateSaveJwtNode implements Node {
    private final static String JWT_TOKEN_NAME = "circle-jwt";
    private final static String TRUE_OUTCOME_ID = "savedTrue";
    private final static String FALSE_OUTCOME_ID = "savedFalse";
    private final static Logger logger = LoggerFactory.getLogger(CircleGenerateSaveJwtNode.class);

    public interface Config {

        @Attribute(order = 10, validators = { RequiredValueValidator.class })

        default String secret() {
            return "";
        }

        @Attribute(order = 20)

        default String expires() {
            return "365";
        }
    }

    private final Config config;

    /**
     * Create the node.
     *
     * @param config The service config.
     */
    @Inject
    public CircleGenerateSaveJwtNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) {

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        if (result.isPresent()) {
            String resultString = result.get();
            return goTo(Boolean.parseBoolean(resultString)).build();

        } else {

            // Get the user name from shared state
            NodeState newNodeState = context.getStateFor(this);

            // Get the Circle appKey and appToken for Circle API calls
            String appKey = newNodeState.get("CircleAppKey").toString();
            String appToken = newNodeState.get("CircleToken").toString();
            String userName = newNodeState.get("username").toString();
            String circleNodeScript = CircleUtil.CORE_SCRIPT;

            if (userName.isEmpty() || config.secret().isEmpty() || appKey.isEmpty()) {
                logger.error("Error: no username, secret or appKey.");
                return goTo(false).build();
            }

            // Generate the JWT Token with the username and secret
            JwtToken jwtToken = new JwtToken();
            String circleJwtToken = "";

            try {
                circleJwtToken = jwtToken.generateJwtToken(newNodeState.get("username").toString(), //
                        config.expires(), config.secret());
            } catch (Exception e) {
                logger.error("Error generating JWT. ");
                goTo(false).build();
            }

            // Add appKey and appToken to the javascript by replacing the
            // $appKey$ and $token$ strings
            circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
            circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

            // Save to JWT Token in Circle Service with the name circle-jwt
            String endString = String.format(" const isSaved = await saveToken('%s','%s');\n" //
                    , JWT_TOKEN_NAME, circleJwtToken);

            circleNodeScript += endString;
            circleNodeScript += "output.value = isSaved;\n";
            circleNodeScript += "await autoSubmit();\n";

            ImmutableList<Callback> callbacks = CircleUtil.getScriptAndSelfSubmitCallback(circleNodeScript);
            return send(callbacks).build();

        }

    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleGenerateSaveJwtNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)),
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));
        }
    }
}
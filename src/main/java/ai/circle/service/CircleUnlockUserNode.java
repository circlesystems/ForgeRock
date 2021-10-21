/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.util.i18n.PreferredLocales;

import static org.forgerock.openam.auth.node.api.Action.send;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.circle.CircleUtil;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;

/**
 * This node presents a screen with 2 inputs for entering the unlock codes. It
 * then reads the unlock codes from sharedState ({code 1} and {code 2}) and, if
 * the codes are correct, unlocks the user.
 */

@Node.Metadata(outcomeProvider = CircleUnlockUserNode.OutcomeProvider.class, //
        configClass = CircleUnlockUserNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleUnlockUserNode implements Node {

    private final Logger logger = LoggerFactory.getLogger(CircleUnlockUserNode.class);
    private final String scriptName = "/js/authorize.js";
    private final static String TRUE_OUTCOME_ID = "unlockedTrue";
    private final static String FALSE_OUTCOME_ID = "unlockedFalse";

    /**
     * Configuration for the node.
     */
    public interface Config {
    }

    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to
     * obtain instances of other classes from the plugin.
     *
     * @param config The service config.
     * @param realm  The realm the node is in.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public CircleUnlockUserNode(@Assisted Config config, @Assisted Realm realm) throws NodeProcessException {

    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        JsonValue newSharedState = context.sharedState.copy();

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript
        if (result.isPresent()) {
            String resultString = result.get();

            return goTo(Boolean.parseBoolean(resultString)).build();

        } else {

            String circleNodeScript = CircleUtil.readFileString(scriptName);
            String appKey = newSharedState.get("CircleAppKey").toString();
            String appToken = newSharedState.get("CircleToken").toString();

            // Circle OTP unlock codes
            String optCode1 = newSharedState.get("code 1").toString();
            String optCode2 = newSharedState.get("code 2").toString();

            circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
            circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

            circleNodeScript += "const isUnLocked = await circleUnlockUser(" + optCode1 + "," + optCode2 + ");\n";
            circleNodeScript += "output.value = isUnLocked;\n";
            circleNodeScript += "await autoSubmit();\n";

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
        private static final String BUNDLE = CircleUnlockUserNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)),
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));
        }
    }
}

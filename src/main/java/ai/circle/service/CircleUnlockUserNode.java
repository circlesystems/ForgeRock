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
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;

import static org.forgerock.openam.auth.node.api.Action.send;
import ai.circle.CircleUtil;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;

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
     */
    @Inject
    public CircleUnlockUserNode() {

    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        NodeState newNodeState = context.getStateFor(this);

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript
        if (result.isPresent()) {
            String resultString = result.get();

            return goTo(Boolean.parseBoolean(resultString)).build();

        } else {

            String scriptName = "/js/authorize.js";
            String circleNodeScript = CircleUtil.readFileString(scriptName);
            String appKey = newNodeState.get("CircleAppKey").toString();
            String appToken = newNodeState.get("CircleToken").toString();

            // Circle OTP unlock codes
            String optCode1 = newNodeState.get("code 1").toString();
            String optCode2 = newNodeState.get("code 2").toString();

            circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
            circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

            circleNodeScript += "const isUnLocked = await circleUnlockUser(" + optCode1 + "," + optCode2 + ");\n";
            circleNodeScript += "output.value = isUnLocked;\n";
            circleNodeScript += "await autoSubmit();\n";

            ImmutableList<Callback> callbacks = CircleUtil.getScriptAndSelfSubmitCallback(circleNodeScript);

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

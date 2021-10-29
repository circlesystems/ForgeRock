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
import org.forgerock.util.i18n.PreferredLocales;
import static org.forgerock.openam.auth.node.api.Action.send;

import ai.circle.CircleUtil;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;

/**
 * This node check if Circle Service is running on the local machine.
 */
@Node.Metadata(outcomeProvider = CircleRunningNode.OutcomeProvider.class, //
        configClass = CircleRunningNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleRunningNode implements Node {
    public final static String TRUE_OUTCOME_ID = "isRunningTrue";
    public final static String FALSE_OUTCOME_ID = "isRunningFalse";

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
    public CircleRunningNode() {

    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        if (result.isPresent()) {

            String resultString = result.get();
            return goTo(Boolean.parseBoolean(resultString)).build();

        } else {
            String scriptName = "/js/isCircleRunning.js";
            String circleNodeScript = CircleUtil.readFileString(scriptName);

            circleNodeScript += "await autoSubmit();\n";

            ImmutableList<Callback> callbacks = CircleUtil.getScriptAndSelfSubmitCallback(circleNodeScript);

            return send(callbacks).build();
        }
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleRunningNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)),
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));
        }
    }
}

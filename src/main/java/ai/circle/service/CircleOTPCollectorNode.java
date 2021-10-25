package ai.circle.service;

import static org.forgerock.openam.auth.node.api.Action.send;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;
import javax.security.auth.callback.NameCallback;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

//import org.forgerock.guava.common.base.Strings;

/**
 * This node collects the unlock codes and stores in sharedState.
 */
@Node.Metadata(outcomeProvider = CircleOTPCollectorNode.OutcomeProvider.class, //
        configClass = CircleOTPCollectorNode.Config.class, //
        tags = {"basic authentication"})

public class CircleOTPCollectorNode implements Node {

    public final static String TRUE_OUTCOME_ID = "codesColletedTrue";
    private final Logger logger = LoggerFactory.getLogger("amAuth");
    public interface Config {

    }

    /**
     * Constructs a new SetSessionPropertiesNode instance.
     */
    @Inject
    public CircleOTPCollectorNode() {
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        List<NameCallback> options = context.getCallbacks(NameCallback.class);

        if (options.isEmpty()) {
            ArrayList<NameCallback> callbacks2 = new ArrayList<>();
            callbacks2.add(new NameCallback("code 1"));
            callbacks2.add(new NameCallback("code 2"));
            return send(callbacks2).build();
        }
        JsonValue newSharedState = context.sharedState.copy();

        options.forEach(name -> newSharedState.put(name.getPrompt(), name.getName()));

        return goTo(true).replaceSharedState(newSharedState).build();
    }

    //TODO Outcome never used
    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(TRUE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleOTPCollectorNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)));
        }
    }

}
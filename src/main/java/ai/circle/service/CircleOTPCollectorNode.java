
package ai.circle.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.openam.auth.node.api.TreeContext;

import javax.inject.Inject;
import javax.security.auth.callback.NameCallback;

import static org.forgerock.openam.auth.node.api.Action.send;

import java.util.ArrayList;

//import org.forgerock.guava.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.circle.CircleUtil;

/**
 * This node collects the unlock codes and stores in sharedState.
 *
 */
@Node.Metadata(outcomeProvider = CircleOTPCollectorNode.OutcomeProvider.class, //
        configClass = CircleOTPCollectorNode.Config.class, //
        tags = { "basic authentication" })

public class CircleOTPCollectorNode implements Node {

    public interface Config {

    }

    private final Logger logger = LoggerFactory.getLogger("amAuth");
    public final static String TRUE_OUTCOME_ID = "codesColletedTrue";

    private final CircleOTPCollectorNode.Config config;

    /**
     * Constructs a new SetSessionPropertiesNode instance.
     * 
     * @param config Node configuration.
     */
    @Inject
    public CircleOTPCollectorNode(@Assisted CircleOTPCollectorNode.Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        List<NameCallback> options = context.getCallbacks(NameCallback.class);

        if (options.isEmpty()) {
            ArrayList<NameCallback> callbacks2 = new ArrayList<NameCallback>();
            callbacks2.add(new NameCallback("code 1"));
            callbacks2.add(new NameCallback("code 2"));
            return send(callbacks2).build();
        }
        JsonValue newSharedState = context.sharedState.copy();

        options.forEach(name -> {
            newSharedState.put(name.getPrompt(), name.getName());
        });

        return goTo(true).replaceSharedState(newSharedState).build();
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(TRUE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleOTPCollectorNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of( //
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)));
            // new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));//
        }
    }

}
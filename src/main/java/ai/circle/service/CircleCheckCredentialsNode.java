/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

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

import ai.circle.CircleUtil;

/**
 * This node checks in ForgeRock if the username and password are valid and
 * inserts the username into the sharedState.
 */
@Node.Metadata(outcomeProvider = CircleCheckCredentialsNode.OutcomeProvider.class, //
        configClass = CircleCheckCredentialsNode.Config.class, tags = "basic authentication")

public class CircleCheckCredentialsNode implements Node {
    private final static String TRUE_OUTCOME_ID = "hasCredentialsTrue";
    private final static String FALSE_OUTCOME_ID = "hasCredentialsFalse";
    private final static Logger logger = LoggerFactory.getLogger(CircleCheckCredentialsNode.class);

    private static String userPassword = "";
    private static String userName = "";

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 30, validators = { RequiredValueValidator.class })
        default String authenticateTokenEndpoint() {
            return "";
        }

    }

    private final Config config;

    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to
     * obtain instances of other classes from the plugin.
     *
     * @param config The service config.
     */
    @Inject
    public CircleCheckCredentialsNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        String authenticateEndPoint = config.authenticateTokenEndpoint();
        NodeState newNodeState = context.getStateFor(this);

        if (!newNodeState.get("password").toString().isEmpty()) {
            userPassword = newNodeState.get("password").toString();
            userName = newNodeState.get("username").toString();
        }

        if (userName.isEmpty() || userPassword.isEmpty()) {
            logger.error("No username or password found in shared / transient");
            return goTo(false).build();
        }

        // Perform user authentication for an access token

        String authId = CircleUtil.authenticateWithUsernamePassword(userName, userPassword, //
                authenticateEndPoint);

        return goTo(authId.isEmpty() ? false : true).build();
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleCheckCredentialsNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)),
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));
        }
    }
}

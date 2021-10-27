/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;

import ai.circle.CircleUtil;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.sm.RequiredValueValidator;

/**
 * This node does the whole OAuth2 flow starting with a username and password
 * and ends up with an access token and a refresh token.
 */

@Node.Metadata(outcomeProvider = CircleOAuthLoginNode.OutcomeProvider.class, //
        configClass = CircleOAuthLoginNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleOAuthLoginNode implements Node {
    private final static String TRUE_OUTCOME_ID = "hasRefreshTokenTrue";
    private final static String FALSE_OUTCOME_ID = "hasRefreshTokenFalse";
    private static String userPassword = "";
    private static String userName = "";

    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 10, validators = { RequiredValueValidator.class })
        default String clientID() {
            return "";
        }

        @Attribute(order = 20, validators = { RequiredValueValidator.class })
        default String clientSecret() {
            return "";
        }

        @Attribute(order = 30, validators = { RequiredValueValidator.class })
        default String authenticateTokenEndpoint() {
            return "";
        }

        @Attribute(order = 40, validators = { RequiredValueValidator.class })
        default String authorizeTokenEndpoint() {
            return "";
        }

        @Attribute(order = 50, validators = { RequiredValueValidator.class })
        default String accessTokenEndpoint() {
            return "";
        }

        @Attribute(order = 60, validators = { RequiredValueValidator.class })
        default String redirectUrl() {
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
    public CircleOAuthLoginNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        String authenticateEndPoint = config.authenticateTokenEndpoint();
        String authorizeEndPoint = config.authorizeTokenEndpoint();
        String refreshAccessTokenEndPoint = config.accessTokenEndpoint();
        String redirectUrl = config.redirectUrl();
        String clientID = config.clientID();
        String clientSecret = config.clientSecret();

        NodeState newNodeState = context.getStateFor(this);

        if (!newNodeState.get("password").toString().isEmpty()) {
            userPassword = newNodeState.get("password").toString();
            userName = newNodeState.get("username").toString();
        }

        // OAuth2 client authentication
        String authId = CircleUtil.authenticateWithUsernamePassword(userName, userPassword, //
                authenticateEndPoint);

        // OAuth2 client authorization
        String autorizeCode = CircleUtil.authorizeForAuthenticationCode(authId, authorizeEndPoint, //
                redirectUrl, config.clientID());

        // Get the refresh token from the authorization code.
        Map<String, String> tokens = CircleUtil.getForgeRockRefreshTokenFromAuthCode(autorizeCode, //
                redirectUrl, clientID, clientSecret, refreshAccessTokenEndPoint);

        newNodeState.putShared("refresh_token", tokens.get("refreshToken"));
        newNodeState.putShared("access_token", tokens.get("accessToken"));
        newNodeState.putTransient("refresh_token", tokens.get("refreshToken"));

        String newRefreshToken = tokens.get("refreshToken").toString();
        return goTo(newRefreshToken.isEmpty() ? false : true).build();
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);

    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleOAuthLoginNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)),
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID)));
        }
    }

}

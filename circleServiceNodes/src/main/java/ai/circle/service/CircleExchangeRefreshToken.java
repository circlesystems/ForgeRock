/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;

import org.forgerock.util.i18n.PreferredLocales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.circle.CircleUtil;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.sm.RequiredValueValidator;

/**
 * A node that checks to see if Circle Service is running
 */
@Node.Metadata(outcomeProvider = CircleExchangeRefreshToken.OutcomeProvider.class, //
        configClass = CircleExchangeRefreshToken.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleExchangeRefreshToken implements Node {

    private final Logger logger = LoggerFactory.getLogger(CircleExchangeRefreshToken.class);
    private final static String TRUE_OUTCOME_ID = "refreshToken";
    private final static String FALSE_OUTCOME_ID = "acessToken";

    // private final Config config;

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
        default String accessTokenEndpoint() {
            return "";
        }
    }

    private final Config config;

    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to
     * obtain instances of other classes from the plugin.
     *
     * @param config The service config.
     * @param realm  The realm the node is in.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public CircleExchangeRefreshToken(@Assisted Config config, @Assisted Realm realm) throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) {
        try {
            String clientSecret = config.clientSecret();
            String refreshAccessTokenEndPoint = config.accessTokenEndpoint();
            String clientID = config.clientID();

            String refreshToken = context.transientState.get("refresh_token").toString();
            refreshToken = refreshToken.replace("\"", "");

            HashMap<String, String> newAccessRefreshTokens = CircleUtil.getForgeRockAccessTokenFromRefreshToken(
                    clientID, clientSecret, refreshToken, refreshAccessTokenEndPoint);

            context.transientState.remove("refresh_token");
            context.transientState.remove("access_token");

            context.transientState.add("refresh_token", newAccessRefreshTokens.get("refreshToken").toString());
            context.transientState.put("refresh_token", newAccessRefreshTokens.get("refreshToken").toString());

            context.transientState.add("acess_token", newAccessRefreshTokens.get("accessToken").toString());

            Boolean returnStatus = false;
            if (!newAccessRefreshTokens.get("refreshToken").isEmpty()) {
                returnStatus = true;
            }
            return goTo(returnStatus).build();

        } catch (Exception e) {
            // TODO: handle exception
        }
        return null;
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo("refreshToken");
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleExchangeRefreshToken.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of( //
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString("refreshToken")));
        }
    }
}

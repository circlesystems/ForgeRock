/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.time.Instant;

import java.util.List;

import java.util.ResourceBundle;
import javax.inject.Inject;

import org.forgerock.json.JsonValue;
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

/**
 * This node holds the second unlock code into the transienteState
 * {oneTimePassword}
 */

@Node.Metadata(outcomeProvider = CircleOTPCodesNode.OutcomeProvider.class, //
        configClass = CircleOTPCodesNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleOTPCodesNode implements Node {
    private final Logger logger = LoggerFactory.getLogger(CircleOTPCodesNode.class);
    public final static String TRUE_OUTCOME_ID = "codesSavedTrue";

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
    public CircleOTPCodesNode(@Assisted Config config, @Assisted Realm realm) throws NodeProcessException {

    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        JsonValue newSharedState = context.sharedState.copy();
        JsonValue newtransientState = context.transientState.copy();

        try {
            String OTPCode1 = newSharedState.get("oneTimePassword1").toString();
            String OTPCode2 = newSharedState.get("oneTimePassword2").toString();

            long unixTime = Instant.now().getEpochSecond();
            newtransientState.put("oneTimePassword", OTPCode2.replace("\"", ""));
            newtransientState.put("oneTimePasswordTimestamp", unixTime);

            newSharedState.put("oneTimePassword1", OTPCode1);
            newSharedState.put("oneTimePassword2", OTPCode2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return goTo(true).replaceTransientState(newtransientState).replaceSharedState(newSharedState).build();
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(TRUE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleOTPCodesNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)));
        }
    }
}

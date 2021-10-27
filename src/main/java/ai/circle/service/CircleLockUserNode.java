/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;
import static org.forgerock.openam.auth.node.api.Action.send;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.sm.RequiredValueValidator;

import ai.circle.CircleUtil;
import ai.circle.RSAUtil;

/**
 * This node locks the user and stores the unlock codes into transientState.
 */

@Node.Metadata(outcomeProvider = CircleLockUserNode.OutcomeProvider.class, //
        configClass = CircleLockUserNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleLockUserNode implements Node {
    private final static Logger logger = LoggerFactory.getLogger(CircleUtil.class);
    public final static String TRUE_OUTCOME_ID = "lockedTrue";
    public final static String FALSE_OUTCOME_ID = "lockedFalse";

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 10, validators = { RequiredValueValidator.class })

        default String privateKey() {
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
    public CircleLockUserNode(@Assisted Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        NodeState newNodeState = context.getStateFor(this);

        // check if there is a result of javascript

        if (result.isPresent()) {
            String resultString = result.get();
            String privateKey = config.privateKey();

            if (!resultString.isEmpty()) {

                try {

                    JSONObject jsonObject = new JSONObject(resultString);

                    // get array of codes from json
                    JSONArray codesArray = jsonObject.getJSONArray("EncryptedUnlockCodes");
                    String otpCode1 = codesArray.getString(0);
                    String otpCode2 = codesArray.getString(1);

                    String decryptedCode1 = RSAUtil.decryptOtpCode(otpCode1, privateKey);
                    String decryptedCode2 = RSAUtil.decryptOtpCode(otpCode2, privateKey);

                    long unixTime = Instant.now().getEpochSecond();

                    newNodeState.putTransient("oneTimePassword", decryptedCode1);
                    newNodeState.putTransient("oneTimePasswordTimestamp", unixTime);

                    // save the codes in the sharedstate for Circle OTP Codes Holder
                    newNodeState.putShared("oneTimePassword1", decryptedCode1);
                    newNodeState.putShared("oneTimePassword2", decryptedCode2);

                    return goTo(decryptedCode1.isEmpty() ? false : true).build();

                } catch (JSONException e) {
                    logger.error("Error parsing JSON", e);
                }
            }

            return goTo(Boolean.parseBoolean(resultString)).build();

        } else {
            String scriptName = "/js/authorize.js";
            String circleNodeScript = CircleUtil.readFileString(scriptName);

            String appKey = newNodeState.get("CircleAppKey").toString();
            String appToken = newNodeState.get("CircleToken").toString();

            // Additional javascript to lock the user and retrieve the unlock codes.
            circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
            circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

            circleNodeScript += "const codes = await circleLockUser();\n";
            circleNodeScript += "output.value = codes;\n";
            circleNodeScript += "await autoSubmit();\n";

            ImmutableList<Callback> callbacks = CircleUtil.getScriptAndSelfSubmitCallback(circleNodeScript);

            return send(callbacks).build();
        }
    }

    private Action.ActionBuilder goTo(boolean outcome) {
        return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = CircleLockUserNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of( //
                    new Outcome(TRUE_OUTCOME_ID, bundle.getString(TRUE_OUTCOME_ID)), //
                    new Outcome(FALSE_OUTCOME_ID, bundle.getString(FALSE_OUTCOME_ID))); //
        }
    }
}

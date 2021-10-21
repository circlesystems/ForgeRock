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
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.util.i18n.PreferredLocales;
import static org.forgerock.openam.auth.node.api.Action.send;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.sm.RequiredValueValidator;

import ai.circle.CircleUtil;
import ai.circle.RSAUtil;

/**
 * A node for Circle Service user locking
 */
@Node.Metadata(outcomeProvider = CircleLockUserNode.OutcomeProvider.class, //
        configClass = CircleLockUserNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleLockUserNode implements Node {
    private final String scriptName = "/js/authorize.js";

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
     * @param realm  The realm the node is in.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public CircleLockUserNode(@Assisted Config config, @Assisted Realm realm) throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        Optional<String> result = context.getCallback(HiddenValueCallback.class).map(HiddenValueCallback::getValue)
                .filter(scriptOutput -> !Strings.isNullOrEmpty(scriptOutput));

        // check if there is a result of javascript

        if (result.isPresent()) {
            String resultString = result.get();
            String privateKey = config.privateKey();

            if (!resultString.isEmpty()) {

                try {
                    JsonValue newSharedState = context.sharedState.copy();
                    JsonValue newtransientState = context.transientState.copy();

                    JSONObject jsonObject = new JSONObject(resultString);

                    // get array of codes from json
                    JSONArray codesArray = jsonObject.getJSONArray("EncryptedUnlockCodes");
                    String otpCode1 = codesArray.getString(0);
                    String otpCode2 = codesArray.getString(1);

                    String decryptedCode1 = RSAUtil.decryptOtpCode(otpCode1, privateKey);
                    String decryptedCode2 = RSAUtil.decryptOtpCode(otpCode2, privateKey);

                    long unixTime = Instant.now().getEpochSecond();

                    newtransientState.put("oneTimePassword", decryptedCode1);
                    newtransientState.put("oneTimePasswordTimestamp", unixTime);

                    // save the codes in the sharedstate for Circle OTP Codes Holder
                    newSharedState.put("oneTimePassword1", decryptedCode1);
                    newSharedState.put("oneTimePassword2", decryptedCode2);

                    return goTo(true).replaceTransientState(newtransientState).replaceSharedState(newSharedState)
                            .build();

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            return goTo(Boolean.parseBoolean(resultString)).build();

        } else {
            String circleNodeScript = CircleUtil.readFileString(scriptName);
            JsonValue newSharedState = context.sharedState.copy();

            String appKey = newSharedState.get("CircleAppKey").toString();
            String appToken = newSharedState.get("CircleToken").toString();

            // Additional javascript to lock the user and retrieve the unlock codes.
            circleNodeScript = circleNodeScript.replace("\"$appKey$\"", appKey);
            circleNodeScript = circleNodeScript.replace("\"$token$\"", appToken);

            circleNodeScript += "const codes = await circleLockUser();\n";
            circleNodeScript += "output.value = codes;\n";
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

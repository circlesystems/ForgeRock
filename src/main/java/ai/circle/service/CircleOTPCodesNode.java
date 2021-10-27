/**
 * Copyright 2021 Circle
 */

package ai.circle.service;

import java.time.Instant;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This node holds the second unlock code into the transienteState
 * {oneTimePassword}
 */

@Node.Metadata(outcomeProvider = CircleOTPCodesNode.OutcomeProvider.class, //
        configClass = CircleOTPCodesNode.Config.class, //
        tags = { "basic authentication" }//
)
public class CircleOTPCodesNode extends SingleOutcomeNode {
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
     */
    @Inject
    public CircleOTPCodesNode() {

    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        NodeState newNodeState = context.getStateFor(this);

        try {
            String OTPCode1 = newNodeState.get("oneTimePassword1").toString();
            String OTPCode2 = newNodeState.get("oneTimePassword2").toString();

            long unixTime = Instant.now().getEpochSecond();
            newNodeState.putTransient("oneTimePassword", OTPCode2.replace("\"", ""));
            newNodeState.putTransient("oneTimePasswordTimestamp", unixTime);

            newNodeState.putShared("oneTimePassword1", OTPCode1);
            newNodeState.putShared("oneTimePassword2", OTPCode2);
        } catch (Exception e) {
            logger.error("Error saving unlock codes into sharedState and transientState", e);
        }
        return goToNext().build();
    }

}

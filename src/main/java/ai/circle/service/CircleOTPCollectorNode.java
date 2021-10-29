package ai.circle.service;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.security.auth.callback.NameCallback;
import static org.forgerock.openam.auth.node.api.Action.send;

import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;

/**
 * This node collects the unlock codes and stores in sharedState.
 */
@Node.Metadata(outcomeProvider = CircleOTPCollectorNode.OutcomeProvider.class, //
        configClass = CircleOTPCollectorNode.Config.class, //
        tags = { "basic authentication" })

public class CircleOTPCollectorNode extends SingleOutcomeNode {
    public final static String TRUE_OUTCOME_ID = "codesColletedTrue";

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

        NodeState newNodeState = context.getStateFor(this);

        List<NameCallback> options = context.getCallbacks(NameCallback.class);

        if (options.isEmpty()) {
            ArrayList<NameCallback> callbacks2 = new ArrayList<>();
            callbacks2.add(new NameCallback("code 1"));
            callbacks2.add(new NameCallback("code 2"));
            return send(callbacks2).build();
        }

        options.forEach(name -> newNodeState.putShared(name.getPrompt(), name.getName()));

        return goToNext().build();
    }

}
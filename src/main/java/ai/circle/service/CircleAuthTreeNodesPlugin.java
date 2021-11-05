package ai.circle.service;

import static java.util.Arrays.asList;
import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;

/**
 * Core nodes installed by default with no engine dependencies.
 */
public class CircleAuthTreeNodesPlugin extends AbstractNodeAmPlugin {

    /**
     * DI-enabled constructor.
     * 
     * @param serviceRegistry A service registry instance.
     * @return
     */
    @Inject
    public void InputCollectorNodePlugin(AnnotatedServiceRegistry serviceRegistry) {
    }

    @Override
    public String getPluginVersion() {
        return "1.0.1";
    }

    @Override
    public void onStartup() throws PluginException {
        for (Class<? extends Node> nodeClass : getNodes()) {
            pluginTools.startAuthNode(nodeClass);
        }
    }

    @Override
    protected Iterable<? extends Class<? extends Node>> getNodes() {
        return asList(CircleAuthorizeNode.class, //
                CircleExchangeRefreshToken.class, //
                CircleLockUserNode.class, //
                CircleOAuthLoginNode.class, //
                CircleOTPCodesNode.class, //
                CircleOTPCollectorNode.class, //
                CircleRunningNode.class, //
                CircleSaveTokenNode.class, //
                CircleUnlockUserNode.class, //
                CircleVerifyTokenExistenceNode.class);
    }
}

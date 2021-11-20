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
        return "1.2.2";
    }

    @Override
    public void onStartup() throws PluginException {
        for (Class<? extends Node> nodeClass : getNodes()) {
            pluginTools.startAuthNode(nodeClass);
        }
    }

    @Override
    public void upgrade(String fromVersion) throws PluginException {
        pluginTools.upgradeAuthNode(CircleAuthorizeNode.class);
        pluginTools.upgradeAuthNode(CircleGenerateSaveJwtNode.class);
        pluginTools.upgradeAuthNode(CircleLockUserNode.class);
        pluginTools.upgradeAuthNode(CircleValidateAndSaveJwtNode.class);
        pluginTools.upgradeAuthNode(CircleOTPCodesNode.class);
        pluginTools.upgradeAuthNode(CircleOTPCollectorNode.class);
        pluginTools.upgradeAuthNode(CircleRunningNode.class);
        pluginTools.upgradeAuthNode(CircleValidateAndSaveJwtNode.class);

    }

    @Override
    protected Iterable<? extends Class<? extends Node>> getNodes() {
        return asList(CircleAuthorizeNode.class, //
                CircleLockUserNode.class, //
                CircleOTPCodesNode.class, //
                CircleOTPCollectorNode.class, //
                CircleRunningNode.class, //
                CircleUnlockUserNode.class, //
                CircleGenerateSaveJwtNode.class, //
                CircleValidateAndSaveJwtNode.class);
    }
}

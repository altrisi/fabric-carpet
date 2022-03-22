package carpet.utils;

import carpet.network.CarpetClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public class FabricAPIHooks {

    public static final boolean WORLD_RENDER_EVENTS = hasMod("fabric-rendering-v1", "1.5.0");
    public static final boolean PERMISSIONS_API = hasMod("fabric-permissions-api-v0", "0.1-SNAPSHOT");

    private FabricAPIHooks() {
    }

    public static void initialize() {
        if (WORLD_RENDER_EVENTS) {
            WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
                if (false) {//(CarpetClient.shapes != null) { // likely won't need it.
                    CarpetClient.shapes.render(context.matrixStack(), context.camera(), context.tickDelta());
                }
            });
        }
    }

    private static boolean hasMod(String id, String minimumVersion) {
        return FabricLoader.getInstance().getModContainer(id).map(m -> {
        	try {
        		return m.getMetadata().getVersion().compareTo(SemanticVersion.parse(minimumVersion)) >= 0;
        	} catch (VersionParsingException e) {
        		return false;
        	}
        }).orElse(false);
    }
}

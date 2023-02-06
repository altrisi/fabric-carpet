package carpet.script.external;

import carpet.CarpetSettings;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.Validator;
import carpet.fakes.MinecraftServerInterface;
import carpet.logging.HUDController;
import carpet.script.CarpetScriptServer;
import carpet.script.utils.AppStoreManager;
import carpet.utils.BlockInfo;
import carpet.utils.CarpetProfiler;
import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;

import java.util.List;
import java.util.Map;

public class Carpet
{
    public static Map<String, Component> getScarpetHeaders()
    {
        return HUDController.scarpet_headers;
    }

    public static Map<String, Component> getScarpetFooters()
    {
        return HUDController.scarpet_footers;
    }

    public static void updateScarpetHUDs(final MinecraftServer server, final List<ServerPlayer> players)
    {
        HUDController.update_hud(server, players);
    }

    public static Component Messenger_compose(final Object... messages)
    {
        return Messenger.c(messages);
    }

    public static void Messenger_message(final CommandSourceStack source, final Object... messages)
    {
        Messenger.m(source, messages);
    }

    public static ThreadLocal<Boolean> getImpendingFillSkipUpdates()
    {
        return CarpetSettings.impendingFillSkipUpdates;
    }

    public static Map<SoundType, String> getSoundTypeNames()
    {
        return BlockInfo.soundName;
    }

    public static Map<MaterialColor, String> getMapColorNames()
    {
        return BlockInfo.mapColourName;
    }

    public static Map<Material, String> getMaterialNames()
    {
        return BlockInfo.materialName;
    }

    public static Runnable startProfilerSection(final String name)
    {
        final CarpetProfiler.ProfilerToken token = CarpetProfiler.start_section(null, name, CarpetProfiler.TYPE.GENERAL);
        return () -> CarpetProfiler.end_current_section(token);
    }

    public static void MinecraftServer_addScriptServer(final MinecraftServer server, final CarpetScriptServer scriptServer)
    {
        ((MinecraftServerInterface) server).addScriptServer(scriptServer);
    }

    public static class ScarpetAppStoreValidator extends Validator<String>
    {
        @Override
        public String validate(final CommandSourceStack source, final CarpetRule<String> currentRule, String newValue, final String stringInput)
        {
            if (newValue.equals(currentRule.value()))
            {
                // Don't refresh the local repo if it's the same (world change), helps preventing hitting rate limits from github when
                // getting suggestions. Pending is a way to invalidate the cache when it gets old, and investigating api usage further
                return newValue;
            }
            if (newValue.equalsIgnoreCase("none"))
            {
                AppStoreManager.setScarpetRepoLink(null);
                return newValue;
            }
            if (newValue.endsWith("/"))
            {
                newValue = newValue.replaceAll("/$", "");
            }
            AppStoreManager.setScarpetRepoLink("https://api.github.com/repos/" + newValue + "/");
            return newValue;
        }

        @Override
        public String description()
        {
            return "Appstore link should point to a valid github repository";
        }
    }
}
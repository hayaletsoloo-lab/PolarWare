package polar.ware.api;

import com.mojang.logging.LogUtils;
import polar.ware.features.cmd.Command;
import polar.ware.features.hud.HudElement;
import polar.ware.features.modules.Module;

import java.util.List;

public interface IAddon {
    void onInitialize();

    List<Module> getModules();

    List<Command> getCommands();

    List<HudElement> getHudElements();

    String getPackage();

    String getName();

    String getAuthor();

    String getRepo();

    String getVersion();

    default String getDescription() {
        return "";
    }

    default void onShutdown() {
        LogUtils.getLogger().info("Shutting down addon: " + getName());
    }
}
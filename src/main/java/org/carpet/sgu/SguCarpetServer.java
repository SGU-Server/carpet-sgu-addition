package org.carpet.sgu;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.logging.LoggerRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import org.carpet.sgu.command.PlayerSkinCommand;
import org.carpet.sgu.logger.BotsLogger;
import org.carpet.sgu.logger.ProjectileTraker;

import java.util.Map;

public class SguCarpetServer implements CarpetExtension, ModInitializer {
    @Override
    public void onInitialize() {
        // 手动注册拓展，确保在 Fabric 初始化阶段就接入 Carpet
        CarpetServer.manageExtension(this);
    }

    public String getName() {
        return "carpet-sgu-addition";
    }

    @Override
    public void onGameStarted() {
        // Register SguSettings class with Carpet
        CarpetServer.settingsManager.parseSettingsClass(SguSettings.class);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked", "removal"})
    public void registerCommands(CommandDispatcher dispatcher) {
        PlayerSkinCommand.register(dispatcher);
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return carpet.utils.Translations.getTranslationFromResourcePath(String.format("assets/carpet-sgu-addition/lang/%s.json", lang));
    }
    @Override
    public void registerLoggers()
    {
        BotsLogger.register();
        var logger = ProjectileTraker.create();
        LoggerRegistry.registerLogger(logger.getLogName(), logger);
    }
}

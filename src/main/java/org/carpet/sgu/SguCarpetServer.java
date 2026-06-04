package org.carpet.sgu;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.logging.LoggerRegistry;
import net.fabricmc.api.ModInitializer;
//? if <=1.21.6 {
import org.carpet.sgu.logger.ProjectileTraker;
//?}

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
    public Map<String, String> canHasTranslations(String lang) {
        return carpet.utils.Translations.getTranslationFromResourcePath(String.format("assets/carpet-sgu-addition/lang/%s.json", lang));
    }
    @Override
    public void registerLoggers()
    {
//? if <=1.21.6 {
        var logger = ProjectileTraker.create();
        LoggerRegistry.registerLogger(logger.getLogName(), logger);
//?}
    }
}

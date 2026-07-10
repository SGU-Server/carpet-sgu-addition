package org.carpet.sgu.logger;

import carpet.logging.HUDController;
import carpet.logging.HUDLogger;
import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.Messenger;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class BotsLogger {
    public static final String NAME = "bots";

    public static boolean __bots;

    private BotsLogger() {
    }

    public static void register() {
        try {
            Field field = BotsLogger.class.getField("__bots");
            LoggerRegistry.registerLogger(NAME, new HUDLogger(field, NAME, null, null));
            HUDController.register(BotsLogger::update);
        } catch (NoSuchFieldException exception) {
            throw new RuntimeException("Unable to register bots logger", exception);
        }
    }

    private static void update(MinecraftServer server) {
        if (!__bots || server == null || server.getPlayerList() == null) {
            return;
        }

        Logger logger = LoggerRegistry.getLogger(NAME);
        if (logger == null) {
            return;
        }

        logger.log(() -> buildHud(server));
    }

    private static Component[] buildHud(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        int bots = 0;

        for (ServerPlayer player : players) {
            if (player instanceof EntityPlayerMPFake) {
                bots++;
            }
        }

        int realPlayers = players.size() - bots;
        return new Component[] {
            Messenger.c("e Player:" + realPlayers + " ", "y Bots:" + bots)
        };
    }
}

package org.carpet.sgu.logger;

import carpet.logging.Logger;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class ProjectileTraker extends Logger {
    public static final String NAME = "projectileTraker";

    public static boolean projectileTraker = true;

    public final Map<UUID, UUID> entityTrackerMap = new HashMap<>();

    public int addNormalEntity(ServerPlayer player, Entity entity) {
        if (player != null && entity != null) {
            entityTrackerMap.put(player.getUUID(), entity.getUUID());
            return 0;
        }
        return 1;
    }

    public static Entity getEntity(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return null;
        }
        for (ServerLevel world : server.getAllLevels()) {
            Entity entity = world.getEntityInAnyDimension(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    public Entity getEntity(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return null;
        }
        UUID entityId = entityTrackerMap.get(player.getUUID());
        if (entityId == null) {
            return null;
        }
        return getEntity(server, entityId);
    }

    public ProjectileTraker(Field acceleratorField, String logName, String def, String[] options, boolean strictOptions) {
        super(acceleratorField, logName, def, options, strictOptions);
    }

    public static Logger create() {
        try {
            return new ProjectileTraker(
                ProjectileTraker.class.getField(NAME),
                NAME,
                "all",
                new String[] {"all", "pearlCannon"},
                true
            );
        } catch (Exception ignored) {}
        return null;
    }
}

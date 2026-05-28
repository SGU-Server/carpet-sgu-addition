//? if <=1.21.6 {
package org.carpet.sgu.logger;
import carpet.logging.Logger;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class ProjectileTraker extends Logger
{
    public static final String NAME = "projectileTraker";

    public static boolean projectileTraker = true;

    public final Map<UUID, UUID> entityTrackerMap = new HashMap<>();

    public int addNormalEntity(ServerPlayerEntity player, Entity entity) {
        if (player != null) {

            entityTrackerMap.put(player.getUuid(), entity.getUuid());
            return 0;
        }
        return 1;
    }

    @Nullable
    public static Entity getEntity(MinecraftServer server, UUID uuid) {
        for (var world : server.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    @Nullable
    public Entity getEntity(@NotNull ServerPlayerEntity player)
    {
        if(entityTrackerMap.containsKey(player.getUuid())){
            return getEntity(player.getServer(),entityTrackerMap.get(player.getUuid()));
        }else {
            return null;
        }
    }


    public ProjectileTraker(Field acceleratorField, String logName, String def, String[] options, boolean strictOptions)
    {
        super(acceleratorField, logName, def, options, strictOptions);
    }


    public static Logger create()
    {
        try {
            return new ProjectileTraker(ProjectileTraker.class.getField(NAME),NAME,"all",
                    new String[]{"all","pearlCannon"},true);
        }
        catch (Exception ignored){}
        return null;
    }
}

//?}




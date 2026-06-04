package org.carpet.sgu.carpetSguAddition.mixin;
import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.players.OldUsersConverter;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(value = EntityPlayerMPFake.class, remap = false)
public abstract class EntityPlayerMPFakeMixin extends ServerPlayer {
    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientOptions) {
        super(server, world, profile, clientOptions);
    }
    @Shadow
    private static Set<String> spawning;
    @ModifyExpressionValue(method = "createFake", at = @At(value = "INVOKE", target = "Lcarpet/patches/EntityPlayerMPFake;fetchGameProfile(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<GameProfile> modifyFetchedProfile(CompletableFuture<GameProfile> original, @Local(name = "gameprofile") GameProfile gameprofile) {
        if (!SguSettings.betterFakePlayerProcess) {
            return original;
        }
        return original.whenComplete((p, t) -> {
            if (t != null) {
                System.out.println("[SGU-DEBUG] fetchGameProfile failed exceptionally: " + t);
            } else {
                System.out.println("[SGU-DEBUG] fetchGameProfile completed. profile=" + (p == null ? "null" : p.name()));
            }
        }).thenApply(p -> {
            if (p == null) {
                System.out.println("[SGU-DEBUG] Profile is null, cannot merge properties!");
                return gameprofile;
            }
            return new GameProfile(gameprofile.id(), gameprofile.name(), p.properties());
        });
    }
    @ModifyVariable(
        method = "createFake",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/server/players/OldUsersConverter;convertMobOwnerIfNecessary(Lnet/minecraft/server/MinecraftServer;Ljava/lang/String;)Ljava/util/UUID;"
        ),
        ordinal = 0
    )
    private static java.util.UUID blockVanillaOfflineCheck(java.util.UUID originalUuid, String username, MinecraftServer server) {
        if (!SguSettings.betterFakePlayerProcess) {
            return originalUuid;
        }
        boolean lmsForcingOffline = false;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("carpet-lms-addition")) {
            try {
                Class<?> lmsClass = Class.forName("cn.nm.lms.carpetlmsaddition.bot.FakePlayerSpawner");
                java.lang.reflect.Method m = lmsClass.getMethod("shouldForceOfflineProfile", String.class);
                lmsForcingOffline = (Boolean) m.invoke(null, username);
            } catch (Exception e) {}
        }
        if (lmsForcingOffline) {
            return originalUuid;
        } else {
            return new java.util.UUID(0, 0);
        }
    }
    @Inject(method = "createFake", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;"), cancellable = true)
    private static void beforeSpawningAdd(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying, CallbackInfoReturnable<Boolean> cir, @Local(name = "gameprofile") LocalRef<GameProfile> gameprofileRef) {
        if (!SguSettings.betterFakePlayerProcess) {
            return;
        }
        java.util.UUID offlineUuid = UUIDUtil.createOfflinePlayerUUID(username);
        boolean lmsForcingOffline = false;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("carpet-lms-addition")) {
            try {
                Class<?> lmsClass = Class.forName("cn.nm.lms.carpetlmsaddition.bot.FakePlayerSpawner");
                java.lang.reflect.Method m = lmsClass.getMethod("shouldForceOfflineProfile", String.class);
                lmsForcingOffline = (Boolean) m.invoke(null, username);
            } catch (Exception e) {}
        }
        if (lmsForcingOffline) {
            return;
        }
        java.nio.file.Path playerDataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
        GameProfile onlineProfile = null;
        boolean onlineUserIsPresent = false;
        try {
            java.util.UUID resolved = OldUsersConverter.convertMobOwnerIfNecessary(server, username);
            if (resolved != null && !resolved.equals(offlineUuid)) {
                onlineProfile = new GameProfile(resolved, username);
                onlineUserIsPresent = true;
            }
        } catch (Exception e) {
            onlineProfile = null;
        }
        java.nio.file.Path offlinePath = playerDataDir.resolve(offlineUuid.toString() + ".dat");
        boolean offlineExists = java.nio.file.Files.exists(offlinePath);
        boolean onlineExists = false;
        java.nio.file.Path onlinePath = null;
        if (onlineUserIsPresent) {
            onlinePath = playerDataDir.resolve(onlineProfile.id().toString() + ".dat");
            onlineExists = java.nio.file.Files.exists(onlinePath);
        }
        GameProfile gameprofile = null;
        if (offlineExists && onlineExists) {
            try {
                long offlineTime = java.nio.file.Files.getLastModifiedTime(offlinePath).toMillis();
                long onlineTime = java.nio.file.Files.getLastModifiedTime(onlinePath).toMillis();
                if (offlineTime <= onlineTime) {
                    gameprofile = new GameProfile(offlineUuid, username);
                } else {
                    gameprofile = onlineProfile;
                }
            } catch (Exception e) {
                gameprofile = new GameProfile(offlineUuid, username);
            }
        } else if (offlineExists) {
            gameprofile = new GameProfile(offlineUuid, username);
        } else if (onlineExists) {
            gameprofile = onlineProfile;
        } else {
            if (onlineUserIsPresent) {
                gameprofile = onlineProfile;
            } else {
                if (!CarpetSettings.allowSpawningOfflinePlayers) {
                    cir.setReturnValue(false);
                    return;
                } else {
                    gameprofile = new GameProfile(offlineUuid, username);
                }
            }
        }
        gameprofileRef.set(gameprofile);
    }
}

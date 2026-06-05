package org.carpet.sgu.mixin;
import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.script.language.Sys;
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
import org.carpet.sgu.SguSettings;
import static org.carpet.sgu.compat.LmsIntegrationHelper.shouldForceOfflineProfile;
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
import static java.nio.file.Files.readAttributes;
import java.nio.file.attribute.BasicFileAttributes;


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
        return original.thenApply(p -> {
            if (p == null || p.name().isEmpty()) {
                System.out.println("p == null");
                return gameprofile;
            }
            System.out.println("test");
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
        boolean lmsForcingOffline = shouldForceOfflineProfile(username);
        if (lmsForcingOffline) {
            return originalUuid;
        } else {
            return new java.util.UUID(0, 0);
        }
    }

    /**
     * Directly queries Mojang servers via GameProfileRepository.findProfileByName,
     * bypassing UserCache / OldUsersConverter caches.
     * Returns null if the username doesn't correspond to a real online account,
     * or if the returned name doesn't match the input case-sensitively.
     */
    private static GameProfile resolveOnlineProfile(MinecraftServer server, String username) {
        try {
            var result = server.services().profileRepository().findProfileByName(username);
            if (result.isPresent()) {
                var nameAndId = result.get();
                // Strict case-sensitive comparison: Mojang API is case-insensitive,
                // but returns the canonical name. Only match if casing is exact.
                if (nameAndId.name().equals(username)) {
                    return new GameProfile(nameAndId.id(), nameAndId.name());
                }
            }
        } catch (Exception e) {
            // Lookup failed, return null
        }
        return null;
    }

    @Inject(method = "createFake", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;"), cancellable = true)
    private static void beforeSpawningAdd(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying, CallbackInfoReturnable<Boolean> cir, @Local(name = "gameprofile") LocalRef<GameProfile> gameprofileRef) {
        if (!SguSettings.betterFakePlayerProcess) {
            return;
        }
        java.util.UUID offlineUuid = UUIDUtil.createOfflinePlayerUUID(username);
        if (shouldForceOfflineProfile(username)) {
            return;
        }
        java.nio.file.Path playerDataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
        GameProfile onlineProfile = resolveOnlineProfile(server, username);
        boolean onlineUserIsPresent = (onlineProfile != null);
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
                long offlineTime = readAttributes(offlinePath, BasicFileAttributes.class).creationTime().toMillis();
                long onlineTime = readAttributes(onlinePath, BasicFileAttributes.class).creationTime().toMillis();
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

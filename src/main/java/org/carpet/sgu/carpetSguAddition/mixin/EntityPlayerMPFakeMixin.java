//? if <=1.21.6 {
package org.carpet.sgu.carpetSguAddition.mixin;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.UserCache;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(value = EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeMixin extends ServerPlayerEntity {

    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions) {
        super(server, world, profile, clientOptions);
    }

    @Shadow
    private static Set<String> spawning;

    @Shadow
    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(final String name) {
        return null;
    }

    @ModifyExpressionValue(method = "createFake", at = @At(value = "INVOKE", target = "Ljava/util/Optional;orElse(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object ensureProfileNonNull(Object original) {
        if (!SguSettings.betterFakePlayerProcess) {
            return original;
        }
        if (original == null) {
            return new GameProfile(new java.util.UUID(0, 0), ""); // Dummy profile just to bypass the null check
        }
        return original;
    }

    @ModifyExpressionValue(method = "lambda$createFake$2", at = @At(value = "INVOKE", target = "Ljava/util/Optional;get()Ljava/lang/Object;"), remap = false)
    private static Object modifyFetchedProfile(Object original, @Local(argsOnly = true) GameProfile finalGP) {
        if (!SguSettings.betterFakePlayerProcess) {
            return original;
        }
        GameProfile fetched = (GameProfile) original;
        GameProfile newProfile = new GameProfile(finalGP.getId(), finalGP.getName());
        newProfile.getProperties().putAll(fetched.getProperties());
        return newProfile;
    }

    @Inject(method = "createFake", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"), cancellable = true)
    private static void beforeSpawningAdd(String username, MinecraftServer server, Vec3d pos, double yaw, double pitch, RegistryKey<World> dimensionId, GameMode gamemode, boolean flying, CallbackInfoReturnable<Boolean> cir, @Local(name = "gameprofile") LocalRef<GameProfile> gameprofileRef, @Local(name = "finalGP") LocalRef<GameProfile> finalGPRef) {
        if (!SguSettings.betterFakePlayerProcess) {
            return;
        }

        java.util.UUID offlineUuid = Uuids.getOfflinePlayerUuid(username);
        
        // --- Compatibility Check ---
        // If the gameprofile is already assigned the offline UUID (e.g., by LMS or offline server mode),
        // we yield and skip SGU's complex file-checking override to respect the preceding logic.
        if (gameprofileRef.get() != null && gameprofileRef.get().getId().equals(offlineUuid)) {
            return;
        }

        java.nio.file.Path playerDataDir = server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA);

        GameProfile onlineProfile = null;
        boolean onlineUserIsPresent = false;

        UserCache.setUseRemote(true);
        try {
            if (server.getUserCache() != null) {
                onlineProfile = server.getUserCache().findByName(username).orElse(null);
            }
        } catch (Exception e) {
            onlineProfile = null;
        } finally {
            UserCache.setUseRemote(server.isDedicated() && server.isOnlineMode());
        }

        if (onlineProfile != null && onlineProfile.getName().equals(username)) {
            onlineUserIsPresent = true;
        } else {
            onlineProfile = null;
        }

        java.nio.file.Path offlinePath = playerDataDir.resolve(offlineUuid.toString() + ".dat");
        boolean offlineExists = java.nio.file.Files.exists(offlinePath);

        boolean onlineExists = false;
        java.nio.file.Path onlinePath = null;
        if (onlineUserIsPresent) {
            onlinePath = playerDataDir.resolve(onlineProfile.getId().toString() + ".dat");
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
        finalGPRef.set(gameprofile);
    }
}

//?}




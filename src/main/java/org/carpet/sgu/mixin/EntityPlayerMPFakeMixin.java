//? if <=1.21.6 {
package org.carpet.sgu.mixin;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import static org.carpet.sgu.compat.LmsIntegrationHelper.shouldForceOfflineProfile;
import org.carpet.sgu.SguSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import static java.nio.file.Files.readAttributes;
import java.nio.file.attribute.BasicFileAttributes;
import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import org.carpet.sgu.command.FakePlayerSkinManager;



@Mixin(value = EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeMixin extends ServerPlayerEntity {
    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions) {
        super(server, world, profile, clientOptions);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void applySavedSkin(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions, boolean shadow, CallbackInfo ci) {
        if (shadow) {
            return;
        }
        try {
            FakePlayerSkinManager.applySavedSkin(server, (EntityPlayerMPFake) (Object) this);
        } catch (java.io.IOException exception) {
            System.err.println("[carpet-sgu-addition] Failed to load saved skin for " + profile.getName() + ": " + exception.getMessage());
        }
    }
    @Shadow
    private static Set<String> spawning;
    @Shadow
    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(final String name) {
        return null;
    }
    @ModifyExpressionValue(method = "createFake", at = @At(value = "INVOKE", target = "Lcarpet/patches/EntityPlayerMPFake;fetchGameProfile(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Optional<GameProfile>> modifyFetchedProfile(CompletableFuture<Optional<GameProfile>> original, @Local(name = "finalGP") GameProfile finalGP) {
        if (!SguSettings.betterFakePlayerProcess) {
            return original;
        }
        return original.thenApply(p -> {
            if (p.isEmpty()) {
                return Optional.of(finalGP);
            }
            GameProfile fetched = p.get();
            GameProfile newProfile = new GameProfile(finalGP.getId(), finalGP.getName());
            newProfile.getProperties().putAll(fetched.getProperties());
            return Optional.of(newProfile);
        });
    }

    /**
     * Directly queries Mojang servers via GameProfileRepository, bypassing UserCache.
     * Returns null if the username doesn't correspond to a real online account,
     * or if the returned name doesn't match the input case-sensitively.
     */
    private static GameProfile resolveOnlineProfile(MinecraftServer server, String username) {
        GameProfile[] result = {null};
        try {
            server.getGameProfileRepo().findProfilesByNames(new String[]{username}, new com.mojang.authlib.ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile profile) {
                    if (profile.getName().equals(username)) {
                        result[0] = profile;
                    }
                }
                @Override
                public void onProfileLookupFailed(String profileName, Exception exception) {
                }
            });
        } catch (Exception e) {
            // Lookup failed, return null
        }
        return result[0];
    }

    @Inject(method = "createFake", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"), cancellable = true)
    private static void beforeSpawningAdd(String username, MinecraftServer server, Vec3d pos, double yaw, double pitch, RegistryKey<World> dimensionId, GameMode gamemode, boolean flying, CallbackInfoReturnable<Boolean> cir, @Local(name = "gameprofile") LocalRef<GameProfile> gameprofileRef, @Local(name = "finalGP") LocalRef<GameProfile> finalGPRef) {
        if (!SguSettings.betterFakePlayerProcess) {
            return;
        }

        java.util.UUID offlineUuid = Uuids.getOfflinePlayerUuid(username);
        if (shouldForceOfflineProfile(username)) {
            return;
        }

        java.nio.file.Path playerDataDir = server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA);

        GameProfile onlineProfile = resolveOnlineProfile(server, username);
        boolean onlineProfileIsPresent = (onlineProfile != null);
        java.nio.file.Path offlinePath = playerDataDir.resolve(offlineUuid.toString() + ".dat");
        boolean offlineExists = java.nio.file.Files.exists(offlinePath);
        boolean onlineExists = false;
        java.nio.file.Path onlinePath = null;
        if (onlineProfileIsPresent) {
            onlinePath = playerDataDir.resolve(onlineProfile.getId().toString() + ".dat");
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
            if (onlineProfileIsPresent) {
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

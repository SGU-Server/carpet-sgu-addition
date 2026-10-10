//? if <=1.21.6 {
package org.carpet.sgu.command;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.CommandHelper;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PlayerSkinCommand {
    private PlayerSkinCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("player")
                .requires(source -> CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                .then(argument("player", StringArgumentType.word())
                        .then(literal("skinset")
                                .then(argument("skinPlayer", StringArgumentType.word())
                                        .executes(PlayerSkinCommand::setSkin)))));
    }

    private static int setSkin(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        String fakePlayerName = StringArgumentType.getString(context, "player");
        String skinPlayerName = StringArgumentType.getString(context, "skinPlayer");
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(fakePlayerName);

        if (!(player instanceof EntityPlayerMPFake fakePlayer)) {
            source.sendError(Text.literal("Player " + fakePlayerName + " is not an active fake player"));
            return 0;
        }

        fetchSkinProfile(server, skinPlayerName).whenCompleteAsync((skinProfile, throwable) -> {
            if (throwable != null) {
                source.sendError(Text.literal("Failed to fetch the skin for " + skinPlayerName));
                return;
            }
            if (skinProfile == null) {
                source.sendError(Text.literal("Premium player " + skinPlayerName + " was not found"));
                return;
            }
            if (server.getPlayerManager().getPlayer(fakePlayerName) != fakePlayer) {
                source.sendError(Text.literal("Fake player " + fakePlayerName + " is no longer online"));
                return;
            }

            GameProfile currentProfile = fakePlayer.getGameProfile();
            try {
                FakePlayerSkinManager.remember(server, fakePlayerName, skinPlayerName, skinProfile);
                FakePlayerSkinManager.applySavedSkin(server, fakePlayer);
            } catch (java.io.IOException exception) {
                source.sendError(Text.literal("Failed to save the skin for " + fakePlayerName + ": " + exception.getMessage()));
                return;
            }
            refreshPlayerInfo(server, fakePlayer, currentProfile.getId());
            source.sendFeedback(() -> Text.literal("Set " + fakePlayerName + "'s skin to " + skinPlayerName), false);
        }, server);

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<GameProfile> fetchSkinProfile(MinecraftServer server, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            GameProfile[] profile = {null};
            server.getGameProfileRepo().findProfilesByNames(new String[]{playerName}, new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile result) {
                    profile[0] = result;
                }

                @Override
                public void onProfileLookupFailed(String profileName, Exception exception) {
                }
            });
            if (profile[0] == null) {
                return null;
            }
            ProfileResult result = server.getSessionService().fetchProfile(profile[0].getId(), true);
            return result == null ? null : result.profile();
        }, Util.getIoWorkerExecutor());
    }

    private static void refreshPlayerInfo(MinecraftServer server, ServerPlayerEntity fakePlayer, java.util.UUID profileId) {
        var chunkManager = fakePlayer.getWorld().getChunkManager();
        chunkManager.unloadEntity(fakePlayer);
        try {
            server.getPlayerManager().sendToAll(new PlayerRemoveS2CPacket(List.of(profileId)));
            server.getPlayerManager().sendToAll(PlayerListS2CPacket.entryFromPlayer(List.of(fakePlayer)));
        } finally {
            // Re-pairing recreates the client player entity, which otherwise keeps its old cached player-list entry.
            chunkManager.loadEntity(fakePlayer);
        }
    }
}
//?}

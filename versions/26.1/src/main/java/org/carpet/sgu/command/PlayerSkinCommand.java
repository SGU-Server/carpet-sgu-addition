package org.carpet.sgu.command;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.CommandHelper;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.carpet.sgu.mixin.PlayerProfileAccessor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PlayerSkinCommand {
    private PlayerSkinCommand() {
    }

    public static void register(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(literal("player")
                .requires(source -> CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                .then(argument("player", StringArgumentType.word())
                        .then(literal("skinset")
                                .then(argument("skinPlayer", StringArgumentType.word())
                                        .executes(PlayerSkinCommand::setSkin)))));
    }

    private static int setSkin(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        net.minecraft.commands.CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String fakePlayerName = StringArgumentType.getString(context, "player");
        String skinPlayerName = StringArgumentType.getString(context, "skinPlayer");
        ServerPlayer player = server.getPlayerList().getPlayerByName(fakePlayerName);

        if (!(player instanceof EntityPlayerMPFake fakePlayer)) {
            source.sendFailure(Component.literal("Player " + fakePlayerName + " is not an active fake player"));
            return 0;
        }

        fetchSkinProfile(server, skinPlayerName).whenCompleteAsync((skinProfile, throwable) -> {
            if (throwable != null) {
                source.sendFailure(Component.literal("Failed to fetch the skin for " + skinPlayerName));
                return;
            }
            if (skinProfile == null) {
                source.sendFailure(Component.literal("Premium player " + skinPlayerName + " was not found"));
                return;
            }
            if (server.getPlayerList().getPlayerByName(fakePlayerName) != fakePlayer) {
                source.sendFailure(Component.literal("Fake player " + fakePlayerName + " is no longer online"));
                return;
            }

            GameProfile currentProfile = fakePlayer.getGameProfile();
            GameProfile replacement = new GameProfile(
                    currentProfile.id(),
                    currentProfile.name(),
                    skinProfile.properties()
            );
            ((PlayerProfileAccessor) fakePlayer).carpet_sgu_addition$setGameProfile(replacement);
            refreshPlayerInfo(server, fakePlayer, currentProfile.id());
            source.sendSuccess(() -> Component.literal("Set " + fakePlayerName + "'s skin to " + skinPlayerName), false);
        }, server);

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<GameProfile> fetchSkinProfile(MinecraftServer server, String playerName) {
        return CompletableFuture.supplyAsync(() -> server.services().profileRepository()
                .findProfileByName(playerName)
                .flatMap(profile -> server.services().profileResolver().fetchById(profile.id()))
                .orElse(null), Util.nonCriticalIoPool());
    }

    private static void refreshPlayerInfo(MinecraftServer server, ServerPlayer fakePlayer, java.util.UUID profileId) {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(profileId)));
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakePlayer)));
    }
}

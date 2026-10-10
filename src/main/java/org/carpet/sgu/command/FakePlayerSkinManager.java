//? if <=1.21.6 {
package org.carpet.sgu.command;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.carpet.sgu.mixin.PlayerProfileAccessor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FakePlayerSkinManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "carpet-sgu-addition-fake-player-skins.json";

    private FakePlayerSkinManager() {
    }

    public static synchronized void remember(
            MinecraftServer server,
            String fakePlayerName,
            String skinPlayerName,
            GameProfile skinProfile
    ) throws IOException {
        StoredSkin storedSkin = StoredSkin.from(skinPlayerName, skinProfile);
        if (storedSkin.properties.stream().noneMatch(property -> "textures".equals(property.name))) {
            throw new IOException("The resolved profile did not contain a skin texture");
        }

        Path path = getPath(server);
        SkinFile skinFile = read(path);
        skinFile.skins.put(key(fakePlayerName), storedSkin);
        write(path, skinFile);
    }

    public static synchronized boolean applySavedSkin(MinecraftServer server, EntityPlayerMPFake fakePlayer) throws IOException {
        Path path = getPath(server);
        StoredSkin storedSkin = read(path).skins.get(key(fakePlayer.getGameProfile().getName()));
        if (storedSkin == null) {
            return false;
        }
        if (storedSkin.properties == null) {
            throw new IOException("Saved skin entry has no properties");
        }

        GameProfile currentProfile = fakePlayer.getGameProfile();
        GameProfile replacement = new GameProfile(currentProfile.getId(), currentProfile.getName());
        for (StoredProperty storedProperty : storedSkin.properties) {
            if (storedProperty == null || storedProperty.name == null || storedProperty.value == null) {
                throw new IOException("Saved skin entry contains an invalid property");
            }
            Property property = storedProperty.toProperty();
            replacement.getProperties().put(property.name(), property);
        }
        ((PlayerProfileAccessor) fakePlayer).carpet_sgu_addition$setGameProfile(replacement);
        return true;
    }

    private static Path getPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
    }

    private static String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private static SkinFile read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new SkinFile();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            SkinFile skinFile = GSON.fromJson(reader, SkinFile.class);
            if (skinFile == null) {
                throw new IOException("Skin file is empty");
            }
            if (skinFile.skins == null) {
                skinFile.skins = new LinkedHashMap<>();
            }
            return skinFile;
        } catch (JsonParseException exception) {
            throw new IOException("Skin file contains invalid JSON", exception);
        }
    }

    private static void write(Path path, SkinFile skinFile) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(
                temporary,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            GSON.toJson(skinFile, writer);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class SkinFile {
        private Map<String, StoredSkin> skins = new LinkedHashMap<>();
    }

    private static final class StoredSkin {
        private String skinPlayer;
        private List<StoredProperty> properties = new ArrayList<>();

        private static StoredSkin from(String skinPlayerName, GameProfile skinProfile) {
            StoredSkin storedSkin = new StoredSkin();
            storedSkin.skinPlayer = skinPlayerName;
            for (Property property : skinProfile.getProperties().values()) {
                storedSkin.properties.add(StoredProperty.from(property));
            }
            return storedSkin;
        }
    }

    private static final class StoredProperty {
        private String name;
        private String value;
        private String signature;

        private static StoredProperty from(Property property) {
            StoredProperty storedProperty = new StoredProperty();
            storedProperty.name = property.name();
            storedProperty.value = property.value();
            storedProperty.signature = property.signature();
            return storedProperty;
        }

        private Property toProperty() {
            return signature == null ? new Property(name, value) : new Property(name, value, signature);
        }
    }
}
//?}

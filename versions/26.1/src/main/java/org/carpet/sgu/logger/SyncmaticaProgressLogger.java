package org.carpet.sgu.logger;

import carpet.logging.HUDLogger;
import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import carpet.utils.Messenger;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SyncmaticaProgressLogger extends HUDLogger {
    public static final String NAME = "syncmaticaProgress";

    public static boolean syncmaticaProgress = true;

    private static final int PLACEMENT_REFRESH_INTERVAL_TICKS = 100;
    private static final int BLOCKS_PER_HUD_UPDATE = 2000;

    private final Map<UUID, ScanState> scanStates = new HashMap<>();

    public SyncmaticaProgressLogger(Field acceleratorField, String logName, String def, String[] options, boolean strictOptions) {
        super(acceleratorField, logName, def, options, strictOptions);
    }

    public static Logger create() {
        try {
            return new SyncmaticaProgressLogger(
                SyncmaticaProgressLogger.class.getField(NAME),
                NAME,
                null,
                null,
                true
            );
        } catch (Exception ignored) {}
        return null;
    }

    public static void updateHud(MinecraftServer server) {
        Logger logger = LoggerRegistry.getLogger(NAME);
        if (!(logger instanceof SyncmaticaProgressLogger syncmaticaProgressLogger)) {
            return;
        }

        logger.log((option, player) -> syncmaticaProgressLogger.getHudMessage((ServerPlayer) player));
    }

    private Component[] getHudMessage(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        long tick = level.getServer().getTickCount();
        ScanState state = getScanState(player, level, tick);
        state.scan(level, BLOCKS_PER_HUD_UPDATE);

        Progress progress = state.progress();
        return new Component[] {
            Messenger.c(
                "w " + progress.name(),
                "w  Done: ",
                progress.totalBlocks() == 0 ? "r n/a" : "l " + progress.donePercent(),
                "w  Wrong: ",
                progress.totalBlocks() == 0 ? "r n/a" : "r " + progress.wrongPercent()
            )
        };
    }

    private ScanState getScanState(ServerPlayer player, ServerLevel level, long tick) {
        UUID playerId = player.getUUID();
        ScanState cached = scanStates.get(playerId);
        if (cached != null && tick < cached.nextPlacementRefreshTick()) {
            return cached;
        }

        try {
            String dimension = level.dimension().identifier().toString();
            SyncmaticaAccess access = SyncmaticaAccess.create();
            if (access == null) {
                return cache(playerId, ScanState.message("Syncmatica server context not found", tick));
            }

            Object placement = access.findNearestPlacement(player.blockPosition(), dimension);
            if (placement == null) {
                return cache(playerId, ScanState.message("No shared schematic in this dimension", tick));
            }

            String key = access.getPlacementKey(placement, dimension);
            if (cached != null && cached.hasKey(key)) {
                cached.refreshPlacementAt(tick);
                return cached;
            }

            return cache(playerId, ScanState.create(access, placement, dimension, tick));
        } catch (Exception e) {
            return cache(playerId, ScanState.message("Read failed: " + e.getClass().getSimpleName(), tick));
        }
    }

    private ScanState cache(UUID playerId, ScanState state) {
        scanStates.put(playerId, state);
        return state;
    }

    private static CompoundTag readNbt(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            return NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            return new CompoundTag();
        }
    }

    private static List<String> readPalette(ListTag paletteTags) {
        List<String> palette = new ArrayList<>(paletteTags.size());
        for (int i = 0; i < paletteTags.size(); i++) {
            palette.add(paletteTags.getCompoundOrEmpty(i).getStringOr("Name", "minecraft:air"));
        }
        return palette;
    }

    private static BlockPos readBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
    }

    private static int getPackedIndex(long[] data, int index, int bits) {
        int bitIndex = index * bits;
        int arrayIndex = bitIndex >> 6;
        int startBit = bitIndex & 63;
        if (arrayIndex >= data.length) {
            return -1;
        }

        long value = data[arrayIndex] >>> startBit;
        int endBit = startBit + bits;
        if (endBit > 64 && arrayIndex + 1 < data.length) {
            value |= data[arrayIndex + 1] << (64 - startBit);
        }
        return (int) (value & ((1L << bits) - 1L));
    }

    private static BlockPos transform(BlockPos origin, BlockPos relative, String mirror, String rotation) {
        int x = relative.getX();
        int y = relative.getY();
        int z = relative.getZ();

        if ("FRONT_BACK".equals(mirror)) {
            z = -z;
        } else if ("LEFT_RIGHT".equals(mirror)) {
            x = -x;
        }

        int rotatedX = x;
        int rotatedZ = z;
        if ("CLOCKWISE_90".equals(rotation)) {
            rotatedX = -z;
            rotatedZ = x;
        } else if ("CLOCKWISE_180".equals(rotation)) {
            rotatedX = -x;
            rotatedZ = -z;
        } else if ("COUNTERCLOCKWISE_90".equals(rotation)) {
            rotatedX = z;
            rotatedZ = -x;
        }

        return origin.offset(rotatedX, y, rotatedZ);
    }

    private static class ScanState {
        private final String key;
        private final String name;
        private final BlockPos origin;
        private final String rotation;
        private final String mirror;
        private final List<RegionScan> regions;
        private final int totalBlocks;
        private int regionIndex;
        private Counts currentCounts;
        private Progress progress;
        private long nextPlacementRefreshTick;

        private ScanState(
            String key,
            String name,
            BlockPos origin,
            String rotation,
            String mirror,
            List<RegionScan> regions,
            int totalBlocks,
            long tick
        ) {
            this.key = key;
            this.name = name;
            this.origin = origin;
            this.rotation = rotation;
            this.mirror = mirror;
            this.regions = regions;
            this.totalBlocks = totalBlocks;
            this.currentCounts = new Counts(totalBlocks);
            this.progress = Progress.fromCounts(name, currentCounts);
            refreshPlacementAt(tick);
        }

        static ScanState create(SyncmaticaAccess access, Object placement, String dimension, long tick) throws ReflectiveOperationException {
            String name = access.getName(placement);
            Path litematicFile = access.getLitematicFile(placement);
            if (!Files.isReadable(litematicFile)) {
                return message(name + " litematic file is not readable", tick);
            }

            CompoundTag root = readNbt(litematicFile);
            CompoundTag regionsTag = root.getCompoundOrEmpty("Regions");
            if (regionsTag.isEmpty()) {
                return message(name + " has no Regions", tick);
            }

            List<RegionScan> regions = new ArrayList<>();
            int totalBlocks = 0;
            for (String regionName : regionsTag.keySet()) {
                RegionScan region = RegionScan.create(regionsTag.getCompoundOrEmpty(regionName));
                if (region.totalBlocks() > 0) {
                    regions.add(region);
                    totalBlocks += region.totalBlocks();
                }
            }

            return new ScanState(
                access.getPlacementKey(placement, dimension),
                name,
                access.getPosition(placement),
                access.getRotation(placement),
                access.getMirror(placement),
                regions,
                totalBlocks,
                tick
            );
        }

        static ScanState message(String message, long tick) {
            return new ScanState(message, message, BlockPos.ZERO, "NONE", "NONE", List.of(), 0, tick);
        }

        boolean hasKey(String key) {
            return this.key.equals(key);
        }

        long nextPlacementRefreshTick() {
            return nextPlacementRefreshTick;
        }

        void refreshPlacementAt(long tick) {
            nextPlacementRefreshTick = tick + PLACEMENT_REFRESH_INTERVAL_TICKS;
        }

        void scan(ServerLevel level, int budget) {
            if (regions.isEmpty() || totalBlocks == 0) {
                return;
            }

            int remaining = budget;
            while (remaining > 0) {
                RegionScan region = regions.get(regionIndex);
                remaining -= region.scan(level, origin, rotation, mirror, currentCounts, remaining);

                if (!region.isComplete()) {
                    break;
                }

                regionIndex++;
                if (regionIndex >= regions.size()) {
                    progress = Progress.fromCounts(name, currentCounts);
                    currentCounts = new Counts(totalBlocks);
                    regionIndex = 0;
                    for (RegionScan scan : regions) {
                        scan.reset();
                    }
                    break;
                }
            }
        }

        Progress progress() {
            return progress;
        }
    }

    private static class RegionScan {
        private final List<String> palette;
        private final long[] blockStates;
        private final BlockPos regionPosition;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int stepX;
        private final int stepY;
        private final int stepZ;
        private final int bits;
        private final int volume;
        private final int totalBlocks;
        private int cursor;

        private RegionScan(
            List<String> palette,
            long[] blockStates,
            BlockPos regionPosition,
            BlockPos size,
            int bits,
            int totalBlocks
        ) {
            this.palette = palette;
            this.blockStates = blockStates;
            this.regionPosition = regionPosition;
            this.sizeX = Math.abs(size.getX());
            this.sizeY = Math.abs(size.getY());
            this.sizeZ = Math.abs(size.getZ());
            this.stepX = size.getX() < 0 ? -1 : 1;
            this.stepY = size.getY() < 0 ? -1 : 1;
            this.stepZ = size.getZ() < 0 ? -1 : 1;
            this.bits = bits;
            this.volume = sizeX * sizeY * sizeZ;
            this.totalBlocks = totalBlocks;
        }

        static RegionScan create(CompoundTag region) {
            List<String> palette = readPalette(region.getListOrEmpty("BlockStatePalette"));
            long[] blockStates = region.getLongArray("BlockStates").orElse(new long[0]);
            BlockPos regionPosition = readBlockPos(region.getCompoundOrEmpty("Position"));
            BlockPos size = readBlockPos(region.getCompoundOrEmpty("Size"));
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int volume = Math.abs(size.getX()) * Math.abs(size.getY()) * Math.abs(size.getZ());
            int totalBlocks = 0;

            if (!palette.isEmpty() && blockStates.length > 0) {
                for (int index = 0; index < volume; index++) {
                    int paletteIndex = getPackedIndex(blockStates, index, bits);
                    if (paletteIndex >= 0 && paletteIndex < palette.size() && !"minecraft:air".equals(palette.get(paletteIndex))) {
                        totalBlocks++;
                    }
                }
            }

            return new RegionScan(palette, blockStates, regionPosition, size, bits, totalBlocks);
        }

        int totalBlocks() {
            return totalBlocks;
        }

        int scan(ServerLevel level, BlockPos origin, String rotation, String mirror, Counts counts, int budget) {
            int processed = 0;
            while (cursor < volume && processed < budget) {
                scanOne(level, origin, rotation, mirror, counts, cursor);
                cursor++;
                processed++;
            }
            return processed;
        }

        private void scanOne(ServerLevel level, BlockPos origin, String rotation, String mirror, Counts counts, int index) {
            int paletteIndex = getPackedIndex(blockStates, index, bits);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                return;
            }

            String expected = palette.get(paletteIndex);
            if ("minecraft:air".equals(expected)) {
                return;
            }

            int x = index % sizeX;
            int z = (index / sizeX) % sizeZ;
            int y = index / (sizeX * sizeZ);
            BlockPos relative = new BlockPos(
                regionPosition.getX() + x * stepX,
                regionPosition.getY() + y * stepY,
                regionPosition.getZ() + z * stepZ
            );
            BlockPos worldPos = transform(origin, relative, mirror, rotation);
            if (!level.isLoaded(worldPos)) {
                counts.unloaded++;
                return;
            }

            String actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(worldPos).getBlock()).toString();
            if (expected.equals(actual)) {
                counts.done++;
            } else if ("minecraft:air".equals(actual)) {
                counts.missing++;
            } else {
                counts.wrong++;
            }
        }

        boolean isComplete() {
            return cursor >= volume;
        }

        void reset() {
            cursor = 0;
        }
    }

    private static class Counts {
        final int total;
        int done;
        int wrong;
        int missing;
        int unloaded;

        Counts(int total) {
            this.total = total;
        }
    }

    private record Progress(String name, int totalBlocks, int done, int wrong) {
        static Progress fromCounts(String name, Counts counts) {
            return new Progress(name, counts.total, counts.done, counts.wrong);
        }

        String donePercent() {
            return percent(done);
        }

        String wrongPercent() {
            return percent(wrong);
        }

        private String percent(int value) {
            return "%.1f%%".formatted(totalBlocks == 0 ? 0.0 : value * 100.0 / totalBlocks);
        }
    }

    private static class SyncmaticaAccess {
        private final Object context;
        private final Method getSyncmaticManager;
        private final Method getLitematicFolder;
        private final Method getAll;
        private final Method getDimension;
        private final Method getPosition;
        private final Method getName;
        private final Method getHash;
        private final Method getRotation;
        private final Method getMirror;

        private SyncmaticaAccess(Object context) throws ReflectiveOperationException {
            this.context = context;
            this.getSyncmaticManager = context.getClass().getMethod("getSyncmaticManager");
            this.getLitematicFolder = context.getClass().getMethod("getLitematicFolder");

            Object manager = this.getSyncmaticManager.invoke(context);
            this.getAll = manager.getClass().getMethod("getAll");
            Class<?> placementClass = Class.forName("ch.endte.syncmatica.data.ServerPlacement");
            this.getDimension = placementClass.getMethod("getDimension");
            this.getPosition = placementClass.getMethod("getPosition");
            this.getName = placementClass.getMethod("getName");
            this.getHash = placementClass.getMethod("getHash");
            this.getRotation = placementClass.getMethod("getRotation");
            this.getMirror = placementClass.getMethod("getMirror");
        }

        static SyncmaticaAccess create() {
            try {
                Class<?> syncmatica = Class.forName("ch.endte.syncmatica.Syncmatica");
                Method getContext = syncmatica.getMethod("getContext", Identifier.class);
                Object context = getContext.invoke(null, Identifier.fromNamespaceAndPath("syncmatica", "server_context"));
                return context == null ? null : new SyncmaticaAccess(context);
            } catch (Throwable ignored) {
                return null;
            }
        }

        Object findNearestPlacement(BlockPos playerPos, String dimension) throws ReflectiveOperationException {
            Object manager = getSyncmaticManager.invoke(context);
            Collection<?> placements = (Collection<?>) getAll.invoke(manager);
            Object nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Object placement : placements) {
                if (!dimension.equals(getDimension.invoke(placement))) {
                    continue;
                }

                BlockPos position = (BlockPos) getPosition.invoke(placement);
                long dx = (long) position.getX() - playerPos.getX();
                long dy = (long) position.getY() - playerPos.getY();
                long dz = (long) position.getZ() - playerPos.getZ();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearestDistance) {
                    nearest = placement;
                    nearestDistance = distance;
                }
            }
            return nearest;
        }

        String getPlacementKey(Object placement, String dimension) throws ReflectiveOperationException {
            return dimension + ":" + getHash.invoke(placement) + ":" + getPosition(placement) + ":" + getRotation(placement) + ":" + getMirror(placement);
        }

        String getName(Object placement) throws ReflectiveOperationException {
            Object name = getName.invoke(placement);
            return name == null ? "Unnamed" : name.toString();
        }

        Path getLitematicFile(Object placement) throws ReflectiveOperationException {
            Path folder = (Path) getLitematicFolder.invoke(context);
            UUID hash = (UUID) getHash.invoke(placement);
            return folder.resolve(hash + ".litematic");
        }

        BlockPos getPosition(Object placement) throws ReflectiveOperationException {
            return (BlockPos) getPosition.invoke(placement);
        }

        String getRotation(Object placement) throws ReflectiveOperationException {
            Object rotation = getRotation.invoke(placement);
            return rotation == null ? "NONE" : rotation.toString();
        }

        String getMirror(Object placement) throws ReflectiveOperationException {
            Object mirror = getMirror.invoke(placement);
            return mirror == null ? "NONE" : mirror.toString();
        }
    }
}

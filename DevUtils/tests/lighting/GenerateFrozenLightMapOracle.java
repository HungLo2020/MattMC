package net.minecraft.world.level.lighting;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.world.level.chunk.DataLayer;

/** Records calls into actual Frozen classes; no map/sampler reimplementation. */
public final class GenerateFrozenLightMapOracle {
    private static final long[] KEYS = {0, 1, 42, -1, Long.MIN_VALUE, Long.MAX_VALUE,
        0x7fffff00000fffffL, 0x800000fffff00000L, 1L << 40, -(1L << 40)};
    private static void verify(String name, String expected) throws Exception {
        try (InputStream stream = GenerateFrozenLightMapOracle.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(expected))
                throw new IllegalStateException("Unexpected Frozen class: " + name);
        }
    }
    private static int identity(DataLayer layer, ArrayList<DataLayer> layers) {
        if (layer == null) return 0;
        for (int i = 0; i < layers.size(); i++) if (layers.get(i) == layer) return i + 1;
        layers.add(layer); return layers.size();
    }
    public static void main(String[] args) throws Exception {
        verify("net/minecraft/world/level/lighting/DataLayerStorageMap", "c45af72af27f8c1f9b59559ffbea4e2d6fa883360f5dee96f9907e5634b41053");
        verify("net/minecraft/world/level/lighting/BlockLightSectionStorage$BlockDataLayerStorageMap", "6021e65afff709e708f1d0e0f58cfd7a1bb006513f9f4d27866bf6abc12e18b8");
        verify("net/minecraft/world/level/lighting/SkyLightSectionStorage$SkyDataLayerStorageMap", "bd4022509c070db9d49fd05573c29ab3028463d8003303995fd5b47b8763f4c2");
        verify("net/minecraft/world/level/chunk/DataLayer", "fa57ffc1904d3844c390aeccff1cdfe8f88f7f3b1fa62b15a1d1e59b31b3f795");
        int perKind = 2048;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x4c4d5031); out.writeInt(perKind * 2);
            for (int kind = 0; kind < 2; kind++) {
                var maps = new ArrayList<DataLayerStorageMap<?>>();
                maps.add(kind == 0 ? new BlockLightSectionStorage.BlockDataLayerStorageMap(new Long2ObjectOpenHashMap<>())
                    : new SkyLightSectionStorage.SkyDataLayerStorageMap(new Long2ObjectOpenHashMap<>(), new Long2IntOpenHashMap(), Integer.MAX_VALUE));
                var layers = new ArrayList<DataLayer>();
                for (int value : new int[]{3,7,-9,31}) layers.add(new DataLayer(value));
                var random = new Random(0x4c4d41504f524143L + kind);
                for (int step = 0; step < perKind; step++) {
                    int op = random.nextInt(kind == 0 ? 9 : 14), index = random.nextInt(maps.size());
                    long key = KEYS[random.nextInt(KEYS.length)];
                    int arg = 0, arg2 = 0, result = 0, sample = 0, error = 0;
                    // Fixed prefix exposes sentinel, stale cache, copy reset and null membership.
                    if (step < 20) {
                        int[] ops = {0,1,2,6,1,4,1,0,1,0,1,5,1,3,1,5,1,0,2,1};
                        op = ops[step]; index = step >= 6 ? 1 : 0;
                        key = step < 7 ? Long.MAX_VALUE : step >= 17 ? 42 : 1;
                    }
                    DataLayerStorageMap<?> map = maps.get(index);
                    DataLayer returned = null;
                    switch (op) {
                        case 0 -> {
                            arg = random.nextInt(layers.size()+1);
                            if (step == 0 || step == 7) arg = 1;
                            if (step == 9) arg = 2;
                            if (step == 17) arg = 0;
                            map.setLayer(key, arg == 0 ? null : layers.get(arg-1));
                        }
                        case 1 -> { returned = map.getLayer(key); result = identity(returned,layers); }
                        case 2 -> result = map.hasLayer(key) ? 1 : 0;
                        case 3 -> { returned = map.removeLayer(key); result = identity(returned,layers); }
                        case 4 -> { maps.add(map.copy()); result = maps.size()-1; }
                        case 5 -> map.clearCache();
                        case 6 -> map.disableCache();
                        case 7 -> {
                            try { returned = map.copyDataLayer(key); result = identity(returned,layers); }
                            catch (NullPointerException expected) { error = 1; }
                        }
                        case 8 -> {
                            arg = random.nextInt(layers.size())+1;
                            arg2 = random.nextInt(65)-32;
                            layers.get(arg-1).fill(arg2); result = layers.get(arg-1).get(0,0,0);
                        }
                        case 9 -> result = ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).topSections.get(key);
                        case 10 -> {
                            arg = random.nextInt();
                            result = ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).topSections.put(key,arg);
                        }
                        case 11 -> result = ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).topSections.remove(key);
                        case 12 -> {
                            arg = random.nextInt();
                            var sky = (SkyLightSectionStorage.SkyDataLayerStorageMap)map;
                            sky.currentLowestY = arg; sky.topSections.defaultReturnValue(arg);
                        }
                        case 13 -> {
                            // Constructor/copy resets the map default to currentLowestY.
                            arg = random.nextInt();
                            ((SkyLightSectionStorage.SkyDataLayerStorageMap)map).topSections.defaultReturnValue(arg);
                        }
                        default -> throw new AssertionError(op);
                    }
                    if (returned != null) sample = returned.get(0,0,0);
                    out.writeInt(kind); out.writeInt(op); out.writeInt(index); out.writeLong(key);
                    out.writeInt(arg); out.writeInt(arg2); out.writeInt(result); out.writeInt(sample); out.writeInt(error);
                }
            }
        }
        System.out.println("Recorded 4096 actual Frozen map/cache/identity/copy/sky-default operations");
    }
}

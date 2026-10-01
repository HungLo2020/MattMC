package net.minecraft.world.level.chunk;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

final class PalettePackingFixtures {
    static final int[] CARDINALITIES = {
        1, 2, 3, 16, 17, 32, 64, 128, 256, 257, 512, 1024, 2048, 4096
    };

    static PalettedContainer<BlockState> states(int cardinality, long seed, String pattern) {
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var value = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY.byId(71), strategy);
        var random = new Random(seed);
        int total = Block.BLOCK_STATE_REGISTRY.size();
        for (int i = 0; i < 4096; i++) {
            int id =
                    pattern.equals("runs")
                            ? (i / 32) % cardinality
                            : pattern.equals("cycle")
                                    ? i % cardinality
                                    : random.nextInt(cardinality);
            var state = Block.BLOCK_STATE_REGISTRY.byId((id * 37 + 71) % total);
            value.getAndSetUnchecked(i & 15, i >> 8, (i >> 4) & 15, state);
        }
        return value;
    }

    static <T> Strategy<T> owner(PalettedContainer<T> source) {
        try {
            var f = PalettedContainer.class.getDeclaredField("strategy");
            f.setAccessible(true);
            return (Strategy<T>) f.get(source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static <T> PalettedContainerRO.PackedData<T> nativePack(
            PalettedContainer<T> source, Strategy<T> strategy) {
        source.acquire();
        try {
            var d = source.dataForNativeScan();
            return NativePalettePacking.pack(d.storage(), d.palette(), owner(source), strategy);
        } finally {
            source.release();
        }
    }

    static List<com.google.gson.JsonObject> records() {
        try (var stream =
                PalettePackingFixtures.class.getResourceAsStream(
                        "/worldgen/heightmap/chunks.json")) {
            var root =
                    com.google.gson.JsonParser.parseReader(
                                    new java.io.InputStreamReader(
                                            Objects.requireNonNull(stream),
                                            java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            var records = new ArrayList<com.google.gson.JsonObject>();
            for (var v : root.getAsJsonArray("chunks")) records.add(v.getAsJsonObject());
            return records;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static List<PalettedContainer<BlockState>> recorded(com.google.gson.JsonObject record) {
        var out = new ArrayList<PalettedContainer<BlockState>>();
        for (var item : record.getAsJsonArray("sections")) {
            var s = item.getAsJsonObject();
            var palette = new ArrayList<BlockState>();
            for (var state : s.getAsJsonArray("palette"))
                palette.add(
                        BlockState.CODEC
                                .parse(com.mojang.serialization.JsonOps.INSTANCE, state)
                                .result()
                                .orElseThrow());
            var data = s.getAsJsonArray("data");
            long[] words = new long[data.size()];
            for (int i = 0; i < words.length; i++) words[i] = data.get(i).getAsLong();
            var packed =
                    new PalettedContainerRO.PackedData<>(
                            palette,
                            words.length == 0
                                    ? Optional.empty()
                                    : Optional.of(java.util.stream.LongStream.of(words)));
            out.add(
                    PalettedContainer.unpack(
                                    Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                                    packed)
                            .result()
                            .orElseThrow());
        }
        return out;
    }

    static <T> PalettedContainer<T> custom(
            Strategy<T> strategy,
            Configuration config,
            net.minecraft.util.BitStorage storage,
            Palette<T> palette) {
        try {
            var c =
                    PalettedContainer.class.getDeclaredConstructor(
                            Strategy.class,
                            Configuration.class,
                            net.minecraft.util.BitStorage.class,
                            Palette.class);
            c.setAccessible(true);
            return (PalettedContainer<T>) c.newInstance(strategy, config, storage, palette);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static long checksum(PalettedContainerRO.PackedData<?> data) {
        long sum = data.bitsPerEntry();
        for (var value : data.paletteEntries())
            sum =
                    sum * 31
                            + (value instanceof BlockState state
                                    ? Block.BLOCK_STATE_REGISTRY.getId(state)
                                    : System.identityHashCode(value));
        if (data.storage().isPresent())
            sum = data.storage().get().reduce(sum, (a, v) -> a * 31 + v);
        return sum;
    }
}

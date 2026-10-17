package net.minecraft.world.level.chunk;

import java.util.*;
import net.minecraft.core.IdMapper;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;

final class PaletteDistinctFixtures {
    static PalettedContainer<String> biomes(int cardinality, long seed, String pattern) {
        var map = new IdMapper<String>();
        for (int i = 0; i < 128; i++) map.add("biome_" + i);
        var value = new PalettedContainer<>(map.byId(0), Strategy.createForBiomes(map));
        var random = new Random(seed);
        for (int i = 0; i < 64; i++) {
            int id = pattern.equals("runs") ? i / Math.max(1, 64 / cardinality) % cardinality
                : pattern.equals("cycle") ? i % cardinality : random.nextInt(cardinality);
            value.getAndSetUnchecked(i & 3, i >> 4, (i >> 2) & 3, map.byId(id));
        }
        return value;
    }

    static List<com.google.gson.JsonObject> records() {
        try (var stream = PaletteDistinctFixtures.class.getResourceAsStream("/worldgen/palette_distinct/biomes.json")) {
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var records = new ArrayList<com.google.gson.JsonObject>();
            for (var value : root.getAsJsonArray("chunks")) records.add(value.getAsJsonObject());
            return records;
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    static List<PalettedContainer<String>> recorded(String dimension) {
        var names = new TreeSet<String>();
        for (var record : records()) for (var s : record.getAsJsonArray("sections"))
            for (var name : s.getAsJsonObject().getAsJsonArray("palette")) names.add(name.getAsString());
        var map = new IdMapper<String>();
        names.forEach(map::add);
        var canonical = new HashMap<String, String>();
        for (var name : map) canonical.put(name, name);
        var owner = Strategy.createForBiomes(map);
        var out = new ArrayList<PalettedContainer<String>>();
        for (var record : records()) {
            if (!dimension.equals("all") && !record.get("dimension").getAsString().equals(dimension)) continue;
            for (var item : record.getAsJsonArray("sections")) {
                var s = item.getAsJsonObject();
                var palette = new ArrayList<String>();
                for (var name : s.getAsJsonArray("palette")) palette.add(canonical.get(name.getAsString()));
                var data = s.getAsJsonArray("data");
                long[] words = new long[data.size()];
                for (int i = 0; i < words.length; i++) words[i] = data.get(i).getAsLong();
                var packed = new PalettedContainerRO.PackedData<>(palette,
                    words.length == 0 ? Optional.empty() : Optional.of(java.util.stream.LongStream.of(words)));
                out.add(PalettedContainer.unpack(owner, packed).result().orElseThrow());
            }
        }
        return out;
    }

    static long identityCode(Object value) {
        return value instanceof BlockState state ? net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state)
            : value == null ? -1 : value.toString().hashCode();
    }

    static <T> void compare(PalettedContainer<T> source) {
        var expected = new ArrayList<T>();
        var actual = new ArrayList<T>();
        JavaPaletteDistinct.getAll(source, expected::add);
        source.getAll(actual::add);
        org.junit.jupiter.api.Assertions.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) org.junit.jupiter.api.Assertions.assertSame(expected.get(i), actual.get(i));
    }
}

package net.sodium.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;

/** Optional bounded observation of immutable meshing inputs, never renderer policy. */
public final class SectionInputDiagnostics {
    private static final long SELECTED = Long.getLong("mattmc.dev.graphicsAuditSectionInputs", Long.MIN_VALUE);
    private static final AtomicInteger EMITTED = new AtomicInteger();
    private SectionInputDiagnostics() {}

    public static void observe(BlockGetter slice, int x, int y, int z) {
        long key = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
        if (key != SELECTED) return;
        int sequence = EMITTED.getAndIncrement();
        if (sequence >= 8) return;
        JsonObject snapshot = snapshot(slice, x, y, z);
        snapshot.addProperty("sequence", sequence);
        System.err.println("section-input-snapshot " + snapshot);
    }

    static JsonObject snapshot(BlockGetter slice, int x, int y, int z) {
        JsonObject result = new JsonObject();
        result.addProperty("sectionKey", SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        result.addProperty("originX", x);
        result.addProperty("originY", y);
        result.addProperty("originZ", z);
        result.addProperty("order", "y-z-x; offsets -1..16 inclusive");
        var paletteIds = new LinkedHashMap<String, Integer>();
        JsonArray palette = new JsonArray();
        JsonArray states = new JsonArray();
        var position = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 16; dy++) for (int dz = -1; dz <= 16; dz++) for (int dx = -1; dx <= 16; dx++) {
            String state = slice.getBlockState(position.set(x + dx, y + dy, z + dz)).toString();
            Integer index = paletteIds.get(state);
            if (index == null) {
                index = paletteIds.size();
                paletteIds.put(state, index);
                palette.add(state);
            }
            states.add(index);
        }
        result.add("palette", palette);
        result.add("states", states);
        return result;
    }
}

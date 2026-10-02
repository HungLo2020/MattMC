package net.minecraft.world.level.levelgen.blending;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.world.level.ChunkPos;

final class BlendingFixtures {
    record Grid(Long2ObjectOpenHashMap<BlendingData> data, int x, int z, int size) {
        Blender production() { return new Blender(data, new Long2ObjectOpenHashMap<>()); }
        JavaBlender original() { return new JavaBlender(data, new Long2ObjectOpenHashMap<>()); }
    }
    static BlendingData data(double[] heights) {
        return BlendingData.unpack(new BlendingData.Packed(0, 16, Optional.of(heights)));
    }
    static Grid grid(String name, long seed) {
        var random = new Random(seed); var map = new Long2ObjectOpenHashMap<BlendingData>();
        int radius = switch(name) { case "minimum" -> 1; case "sparse" -> 2; case "border", "far", "mixed" -> 3; default -> 7; };
        boolean reversed = (seed & 1) != 0;
        // Same chunk-ring selection/order as Blender.of. A west old-terrain
        // half-plane places queries on the new side of a real seam.
        var keys = new ArrayList<ChunkPos>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            if (x*x+z*z <= (radius+1)*(radius+1) && (name.equals("mixed") ? x <= 0 : x < 0)) keys.add(new ChunkPos(x,z));
        if (reversed) Collections.reverse(keys);
        for (var key : keys) {
            double[] heights = new double[16];
            for (int i = 0; i < heights.length; i++) heights[i] = name.equals("sparse") && i%2==0
                ? Double.MAX_VALUE : 32 + random.nextInt(192) + (seed%3==0 ? .5 : 0);
            if (name.equals("seam")) {
                // Original calculateData with old x<0/new x>=0: only EAST
                // columns (4,1..3) and NORTH_EAST corner (4,0) are read.
                // Their outside indices are 12..15; interior old chunks have
                // no new neighbor and leave all heights at NO_VALUE.
                for (int i=0;i<16;i++) if(key.x != -1 || i < 12) heights[i]=Double.MAX_VALUE;
            }
            map.put(key.toLong(), data(heights));
        }
        int x = name.equals("seam") ? 0 : name.equals("far") ? 128 : name.equals("mixed") ? 1 : 4;
        return new Grid(map, x, name.equals("seam") ? 0 : -2, name.equals("wide") ? 9 : 5);
    }
    static void original(JavaBlender blender, int firstX, int firstZ, int size, double[] alpha, double[] offset) {
        JavaBlendingGrid.fill(blender, firstX, firstZ, size - 1, alpha, offset);
    }

    static List<String> names() { return List.of("seam", "minimum", "sparse", "border", "mixed", "large", "wide", "far"); }
}

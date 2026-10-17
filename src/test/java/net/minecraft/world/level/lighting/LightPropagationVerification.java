package net.minecraft.world.level.lighting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LightTerrainFixtures;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import static net.minecraft.world.level.lighting.LightPropagationFixtures.*;

/** Opt-in benchmark of light propagation on Rust against Java
 * (-Dmattmc.lighting.javaPropagation=true), on saved FULL overworld terrain and NOISE-filled nether terrain. Timed:
 * the production light-engine calls (section statuses, light sources,
 * checkBlock and every runLightUpdates, including the Rust boundary, section
 * callbacks and installs). Terrain generation and engine construction are
 * untimed setup. */
public final class LightPropagationVerification {
    private static final int SIZE = 6;

    /** Initial lighting of the whole grid, in shuffled batches as the light thread receives chunks. */
    private static long lightGrid(World world, Side side, long seed) {
        var random = new Random(seed);
        var order = new ArrayList<>(world.chunks.values());
        order.sort((a, b) -> Long.compare(a.getPos().toLong(), b.getPos().toLong()));
        Collections.shuffle(order, random);
        long work = 0;
        for (int at = 0; at < order.size(); ) {
            var chunks = order.subList(at, Math.min(order.size(), at + 1 + random.nextInt(4)));
            at += chunks.size();
            for (var chunk : chunks) initialize(side.engine(), chunk);
            work += side.engine().runLightUpdates();
            for (var chunk : chunks) enable(side.engine(), chunk);
            for (var chunk : chunks) side.engine().propagateLightSources(chunk.getPos());
            work += side.engine().runLightUpdates();
        }
        return work;
    }

    private static long sample(World world, Side side, long seed) {
        var random = new Random(seed);
        long sum = 0;
        int min = world.chunks.values().stream().mapToInt(c -> c.getPos().getMinBlockX()).min().orElseThrow();
        int minZ = world.chunks.values().stream().mapToInt(c -> c.getPos().getMinBlockZ()).min().orElseThrow();
        for (int i = 0; i < 512; i++) {
            var pos = new BlockPos(min + random.nextInt(SIZE * 16), world.minY + random.nextInt(world.height), minZ + random.nextInt(SIZE * 16));
            for (LightLayer layer : LightLayer.values()) sum = sum * 31 + side.engine().getLayerListener(layer).getLightValue(pos);
        }
        return sum;
    }

    /** A self-reverting edit: block, original state. */
    private record Edit(BlockPos pos, BlockState state) {}

    private static List<Edit> edits(World world, long seed) {
        var random = new Random(seed);
        var edits = new ArrayList<Edit>();
        int min = world.chunks.values().stream().mapToInt(c -> c.getPos().getMinBlockX()).min().orElseThrow();
        int minZ = world.chunks.values().stream().mapToInt(c -> c.getPos().getMinBlockZ()).min().orElseThrow();
        BlockState[] kinds = {Blocks.TORCH.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState()};
        while (edits.size() < 96) {
            int x = min + 16 + random.nextInt((SIZE - 2) * 16), z = minZ + 16 + random.nextInt((SIZE - 2) * 16);
            ProtoChunk chunk = world.chunk(x >> 4, z >> 4);
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
            BlockState kind = kinds[edits.size() % kinds.length];
            // Torches and stone go on the surface; digging opens the top block; glowstone lights caves.
            int y = kind.isAir() ? surface - 1 : kind.is(Blocks.GLOWSTONE) ? world.minY + 8 + random.nextInt(Math.max(1, surface - world.minY - 16)) : surface;
            if (chunk.getBlockState(new BlockPos(x, y, z)) == kind) continue;
            edits.add(new Edit(new BlockPos(x, y, z), kind));
        }
        return edits;
    }

    /** {@code main(mode, case[, quick])}: mode {@code java} or {@code native};
     * cases {@code terrain}/{@code nether} (initial lighting) and {@code edits} (on lit terrain). */
    public static void main(String[] args) {
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        if (!List.of("native", "java").contains(mode) || !List.of("terrain", "nether", "edits").contains(name)) throw new IllegalArgumentException();
        LightTerrainFixtures.load();
        try {
            boolean rust = mode.equals("native");
            NativeLightPropagation.setEnabled(rust);
            boolean sky = !name.equals("nether");
            var world = new World(sky ? corpus() : LightTerrainFixtures.grid(NoiseGeneratorSettings.NETHER, 7_340_013L, -3, 2, SIZE));
            for (var chunk : world.chunks.values()) chunk.initializeLightSources();
            long passes = NativeLightPropagation.PASSES.get(), rejected = NativeLightPropagation.REJECTED.get();
            if (!name.equals("edits")) {
                PlayerChunkDistancesVerification.measure(mode, name, quick, () -> Side.of(world, sky, rust),
                    side -> lightGrid(world, side, 11L) * 31 + sample(world, side, 5L));
            } else {
                var edits = edits(world, 3L);
                PlayerChunkDistancesVerification.measure(mode, name, quick, () -> {
                    Side side = Side.of(world, sky, rust);
                    lightGrid(world, side, 11L);
                    return side;
                }, side -> {
                    var engines = List.of(side.engine());
                    long work = 0;
                    for (Edit edit : edits) {
                        BlockState original = world.chunk(edit.pos().getX() >> 4, edit.pos().getZ() >> 4).getBlockState(edit.pos());
                        set(world, engines, edit.pos(), edit.state());
                        work = work * 31 + side.engine().runLightUpdates();
                        set(world, engines, edit.pos(), original);
                        work = work * 31 + side.engine().runLightUpdates();
                    }
                    return work * 31 + sample(world, side, 5L);
                });
            }
            long used = NativeLightPropagation.PASSES.get() - passes;
            if (rust != (used > 0) || NativeLightPropagation.REJECTED.get() != rejected) {
                throw new IllegalStateException("Route not taken: " + used + " Rust passes");
            }
            System.out.println("LIGHT_PROPAGATION_ROUTE case=" + name + " mode=" + mode + " rust_passes=" + used);
        } finally {
            NativeLightPropagation.setEnabled(true);
            LightTerrainFixtures.close();
        }
    }
}

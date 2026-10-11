import com.seibel.distanthorizons.api.enums.config.EDhApiGrassSideRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.vulkanic.world.DistantHorizonsSemanticCollector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Locale;

/** Current before/after production publication; Frozen has no Rust collector API. */
public final class VerifyDhColumnPublication {
    private static final long KEY = -991002L;
    private static final MethodHandle ADD;
    private static IClientLevelWrapper level;
    static {
        try {
            Method method = VerifyDhQuads.class.getDeclaredMethod("add", LodQuadBuilder.class, short[].class);
            method.setAccessible(true);
            ADD = MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    private static long run(short[][] input, int rounds) throws Throwable {
        long checksum = 0;
        for (int i = 0; i < rounds; i++) {
            try (var builder = new LodQuadBuilder(true, level);
                 var column = new LodBufferContainer(KEY, new DhBlockPos(4, 5, 6))) {
                for (short[] row : input) ADD.invokeExact(builder, row);
                builder.mergeQuads();
                checksum += builder.getCurrentOpaqueQuadsCount() * 31L + builder.getCurrentTransparentQuadsCount();
                if (column.makeAndUploadBuffersAsync(builder).join() != column || !column.renderDataReady()
                    || column.rustSemanticColumnGeneration() <= 0) throw new IllegalStateException("No published column");
                checksum++;
            }
            if (DistantHorizonsSemanticCollector.hasColumn(KEY)) throw new IllegalStateException("Column owner leaked");
        }
        return checksum;
    }
    public static void main(String[] args) throws Throwable {
        int seed = Integer.parseInt(args[0]), quads = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]);
        boolean grass = Boolean.parseBoolean(args[3]);
        if (seed < 0 || seed > 2 || quads <= 0 || rounds <= 0) throw new IllegalArgumentException("Invalid workload");
        Method setup = VerifyDhQuads.class.getDeclaredMethod("setup"); setup.setAccessible(true); setup.invoke(null);
        var sourceLevel = VerifyDhQuads.class.getDeclaredField("LEVEL"); sourceLevel.setAccessible(true);
        level = (IClientLevelWrapper) sourceLevel.get(null);
        Config.Client.Advanced.Debugging.rendererMode.setWithoutFiringEvents(EDhApiRendererMode.DEFAULT);
        Config.Client.Advanced.Debugging.debugRendering.setWithoutFiringEvents(EDhApiDebugRendering.OFF);
        Config.Client.Advanced.Debugging.showOverlappingQuadErrors.setWithoutFiringEvents(false);
        Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.setWithoutFiringEvents(0);
        Config.Client.Advanced.Graphics.Quality.grassSideRendering.setWithoutFiringEvents(EDhApiGrassSideRendering.FADE_TO_DIRT);
        if (!DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()) throw new IllegalStateException("Wrong route");
        Method inputs = VerifyDhQuads.class.getDeclaredMethod("input", int.class, int.class); inputs.setAccessible(true);
        short[][] values = (short[][]) inputs.invoke(null, seed, quads);
        if (grass) for (short[] row : values) if (row[6] != 12) row[6] = 13;
        for (int i = 0; i < 5; i++) run(values, rounds);
        long start = System.nanoTime(), checksum = run(values, rounds), elapsed = System.nanoTime() - start;
        System.out.printf(Locale.ROOT, "RESULT ns=%d checksum=%d quads=%d rounds=%d%n", elapsed, checksum, quads, rounds);
    }
}

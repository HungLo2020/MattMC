import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Saved native point owners and stock block facts, with deterministic material
 * bindings/colors. Hydration is outside timing; no gameplay/FPS claim follows. */
public final class VerifyDhPrimaryConversion {
    static MethodHandle owner, open, column, fill, openMaterials;
    static void verifyReleasedContext() throws Exception {
        if (openMaterials == null || !VerifyDhConversionPolicy.resolvedWorld) return;
        var type = Class.forName("com.seibel.distanthorizons.core.dataObjects.transformers.RenderColumnConversionContext");
        var root = type.getDeclaredField("ROOT"); root.setAccessible(true);
        var context = ((ThreadLocal<?>)root.get(null)).get();
        for (var field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("position")) continue;
            field.setAccessible(true);
            if (!field.getType().isPrimitive() && field.get(context) != null)
                throw new AssertionError("Worker retained " + field.getName());
            if (field.getName().equals("active") && field.getBoolean(context)) throw new AssertionError("Worker remained active");
        }
    }
    static void setupReader() throws Exception {
        try {
            var ownerClass = Class.forName("net.minecraft.world.level.chunk.NativeDhSource");
            var readerClass = Class.forName("net.minecraft.world.level.chunk.NativeDhConversionReader");
            var lookup = MethodHandles.publicLookup();
            owner = lookup.unreflect(FullDataSourceV2.class.getMethod("nativeSource"))
                .asType(MethodType.methodType(Object.class, FullDataSourceV2.class));
            open = lookup.unreflect(ownerClass.getMethod("conversionReader"))
                .asType(MethodType.methodType(AutoCloseable.class, Object.class));
            column = lookup.unreflect(readerClass.getMethod("column", int.class))
                .asType(MethodType.methodType(LongList.class, AutoCloseable.class, int.class));
        } catch (ClassNotFoundException absentInFrozen) {}
        if (VerifyDhConversionPolicy.convert.getParameterCount() == 14) {
            var type = Class.forName("net.vulkanic.world.NativeDhConversionScope");
            openMaterials = MethodHandles.publicLookup().unreflect(type.getMethod("create",
                com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper.class, FullDataSourceV2.class))
                .asType(MethodType.methodType(AutoCloseable.class,
                    com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper.class, FullDataSourceV2.class));
        }
    }
    static List<FullDataSourceV2> sources(Path manifest) throws Throwable {
        var result = new ArrayList<FullDataSourceV2>();
        var data = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        var mappingField = FullDataSourceV2.class.getDeclaredField("mapping"); mappingField.setAccessible(true);
        for (var item : data.getAsJsonArray("rows")) {
            var row = item.getAsJsonObject(); var dto = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
            dto.pos = com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)(row.get("level").getAsInt()+6),row.get("x").getAsInt(),row.get("z").getAsInt());
            dto.dataFormatVersion = (byte)row.get("version").getAsInt(); dto.compressionModeValue = (byte)row.get("compression").getAsInt();
            for (var field : row.getAsJsonObject("fields").entrySet())
                FullDataSourceV2DTO.class.getField(field.getKey()).set(dto, ByteArrayList.wrap(Files.readAllBytes(
                    manifest.getParent().resolve(field.getValue().getAsJsonObject().get("file").getAsString()))));
            try (dto) {
                var source = dto.createUnitTestDataSource();
                // CPU fixture only: retain saved point storage and map its real
                // IDs to nine deterministic stock states on both implementations.
                if (VerifyDhConversionPolicy.resolvedWorld) {
                    int maximum = 0;
                    try (var reader = open == null ? null : (AutoCloseable)open.invokeExact((Object)owner.invokeExact(source))) {
                        for (int c = 0; c < 4096; c++) {
                            var points = reader == null ? source.getColumnAtRelPos(c/64,c%64) : (LongList)column.invokeExact(reader,c);
                            if (points != null) for (int i = 0; i < points.size(); i++) maximum = Math.max(maximum,(int)(points.getLong(i)&0x7fffffffL));
                        }
                    }
                    if (maximum >= 1_048_576) throw new IllegalArgumentException("Unbounded fixture material IDs");
                    var mapping = new FullDataPointIdMap(source.getPos());
                    var bindingsField = FullDataPointIdMap.class.getDeclaredField("blockBiomePairList"); bindingsField.setAccessible(true);
                    @SuppressWarnings("unchecked") var bindings = (ArrayList<com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair>)bindingsField.get(mapping);
                    for (int id = 0; id <= maximum; id++) bindings.add(com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair.get(
                        VerifyDhConversionPolicy.blocks[id % VerifyDhConversionPolicy.blocks.length], VerifyDhConversionPolicy.biome));
                    mappingField.set(source, mapping);
                } else {
                mappingField.set(source, new FullDataPointIdMap(source.getPos()) {
                    @Override public IBiomeWrapper getBiomeWrapper(int id) { return VerifyDhConversionPolicy.biome; }
                    @Override public IBlockStateWrapper getBlockStateWrapper(int id) {
                        return VerifyDhConversionPolicy.blocks[id % VerifyDhConversionPolicy.blocks.length];
                    }
                });
                }
                result.add(source);
            }
        }
        return result;
    }
    static long run(List<FullDataSourceV2> sources, ColumnRenderSource render, ColumnArrayView view, LongList output) throws Throwable {
        long hash = 1;
        for (var source : sources) try (var reader = open == null ? null : (AutoCloseable)open.invokeExact((Object)owner.invokeExact(source));
            var materials = openMaterials == null ? null : (AutoCloseable)openMaterials.invokeExact(VerifyDhConversionPolicy.level, source)) {
            if (openMaterials != null && VerifyDhConversionPolicy.resolvedWorld && materials == null) throw new AssertionError("Native material scope was not admitted");
            for (int i = 0; i < 4096; i++) {
                var input = reader == null ? source.getColumnAtRelPos(i / 64, i % 64) : (LongList)column.invokeExact(reader,i);
                if (input == null) { hash *= 31; continue; }
                int length = input.size();
                if (length <= 0) { hash *= 31; continue; }
                if (fill == null) Arrays.fill(((LongArrayList)output).elements(), 0, length, 0L);
                else { int ignored = (int)fill.invokeExact(output, 0, length, 0, 0L); }
                if (VerifyDhConversionPolicy.convert.getParameterCount() == 6)
                    VerifyDhConversionPolicy.convert.invoke(null, VerifyDhConversionPolicy.level, source, 1, 2, view, input);
                else if (VerifyDhConversionPolicy.convert.getParameterCount() == 13) VerifyDhConversionPolicy.convert.invoke(null, VerifyDhConversionPolicy.level, source, render, 0, 0, 1, 2, view, input, false, null, null, null);
                else VerifyDhConversionPolicy.convert.invoke(null, VerifyDhConversionPolicy.level, source, render, 0, 0, 1, 2, view, input, false, null, null, null, materials);
                for (int p = 0; p < length; p++) hash = 31 * hash + output.getLong(p);
            }
        }
        return hash;
    }
    static void fullSources(List<FullDataSourceV2> sources) throws Throwable {
        var bean = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        var results = new ColumnRenderSource[sources.size()];
        long expected = 0;
        try {
            for (int round = 0; round < 12; round++) {
                long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
                for (int i = 0; i < results.length; i++) results[i] =
                    com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer
                        .transformFullDataToRenderSource(sources.get(i), VerifyDhConversionPolicy.level);
                long elapsed = System.nanoTime() - start;
                bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
                verifyReleasedContext();
                // Complete production outputs are retained through timing. Hash
                // every output slot afterwards, then close outside measurement.
                long hash = 1;
                for (var result : results) {
                    if (result == null) throw new AssertionError("Production conversion declined");
                    for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++)
                        for (int p = 0; p < result.verticalDataCount; p++) hash = 31 * hash + result.getDataPoint(x, z, p);
                }
                if (round == 0) expected = hash;
                else if (expected != hash) throw new AssertionError("Changing production outputs");
                if (round >= 4) VerifyDhConversionPolicy.REPORT.println("SAMPLE " + (round-4) + " " + elapsed + " " + hash + " " + bytes);
                for (int i = 0; i < results.length; i++) {
                    if (VerifyDhConversionPolicy.renderClose != null) VerifyDhConversionPolicy.renderClose.invoke(results[i]);
                    results[i] = null;
                }
            }
        } finally {
            for (var result : results) if (result != null && VerifyDhConversionPolicy.renderClose != null)
                VerifyDhConversionPolicy.renderClose.invoke(result);
            for (var source : sources) source.close();
        }
    }
    public static void main(String[] args) throws Throwable {
        VerifyDhConversionPolicy.resolvedWorld = args.length > 2 && args[2].equals("resolved-world");
        VerifyDhConversionPolicy.stock = true; VerifyDhConversionPolicy.setup(); setupReader();
        var sources = sources(Path.of(args[0]));
        var quality = com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.blocksToIgnore;
        quality.setWithoutFiringEvents(com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid.NON_COLLIDING);
        com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.setWithoutFiringEvents(true);
        com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(true);
        com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(128);
        if (args.length > 3 && args[3].equals("full-source")) {
            VerifyDhConversionPolicy.REPORT.println("WORKLOAD full-source");
            fullSources(sources); return;
        }
        LongList output = new LongArrayList(new long[4096]);
        if (args.length > 1 && args[1].equals("native")) try {
            var type = Class.forName("net.vulkanic.world.NativeDhRenderSource");
            output = (LongList)type.getConstructor(int.class).newInstance(1);
            fill = MethodHandles.publicLookup().unreflect(type.getMethod("span", LongList.class, int.class, int.class, int.class, long.class));
        } catch (ClassNotFoundException absentInFrozen) {}
        VerifyDhConversionPolicy.REPORT.println("STORAGE reader=" + (open != null) + " output=" + (fill != null));
        VerifyDhConversionPolicy.REPORT.println("MATERIALS scope=" + (openMaterials != null && VerifyDhConversionPolicy.resolvedWorld));
        var view = (ColumnArrayView)VerifyDhConversionPolicy.view.newInstance(output, 4096, 0, 4096);
        var render = ColumnRenderSource.createEmpty(sources.getFirst().getPos(), 1, -64);
        var bean = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean(); bean.setThreadAllocatedMemoryEnabled(true);
        long expected = 0;
        try {
            for (int round = 0; round < 12; round++) {
                long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
                long hash = run(sources, render, view, output), elapsed = System.nanoTime() - start;
                bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
                verifyReleasedContext();
                if (round == 0) expected = hash; else if (expected != hash) throw new AssertionError("Changing output");
                if (round >= 4) VerifyDhConversionPolicy.REPORT.println("SAMPLE " + (round-4) + " " + elapsed + " " + hash + " " + bytes);
            }
        } finally {
            for (var source : sources) source.close();
            if (output instanceof AutoCloseable closeable) closeable.close();
            if (VerifyDhConversionPolicy.renderClose != null) VerifyDhConversionPolicy.renderClose.invoke(render);
        }
    }
}

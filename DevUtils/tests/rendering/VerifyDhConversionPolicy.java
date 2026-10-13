import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.*;
import java.util.*;

/** Identical production-loop observer compiled against Current, predecessor and
 * unchanged Frozen. Trace comparison includes ordered getters and failed writes. */
public final class VerifyDhConversionPolicy {
    static final java.io.PrintStream REPORT = System.out;
    static final List<String> trace = new ArrayList<>();
    static boolean recording, ceiling, end, stock, resolvedWorld;
    static int failAt = -1, events;
    static final class Injected extends RuntimeException {}
    static void event(String value) {
        if (!recording) return;
        trace.add(value);
        if (events++ == failAt) throw new Injected();
    }
    static Object zero(Method m) {
        if (m.getReturnType() == boolean.class) return false;
        if (m.getReturnType() == byte.class) return (byte)0;
        if (m.getReturnType() == int.class) return 0;
        if (m.getReturnType() == long.class) return 0L;
        return null;
    }
    static <T> T proxy(Class<T> type, InvocationHandler body) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, body));
    }
    static final int[] colors = {0xff223344, 0, 0x80223344, 0xffeeeeee, 0x80334455, 0x88334455, 0x00112233, 0xff998877, 0xff223344};
    static final IBlockStateWrapper[] blocks = new IBlockStateWrapper[colors.length];
    static IClientLevelWrapper level;
    static IBiomeWrapper biome;
    static Method convert;
    static Constructor<?> view;
    static Method renderClose;
    static int id(Object block) {
        for (int i = 0; i < blocks.length; i++) if (blocks[i] == block) return i;
        throw new AssertionError();
    }
    static HashSet<IBlockStateWrapper> set(String label, int... values) {
        HashSet<IBlockStateWrapper> result = resolvedWorld ? new HashSet<>() : new HashSet<IBlockStateWrapper>() {
            @Override public boolean contains(Object value) { if (recording) event(label + ":" + id(value)); return super.contains(value); }
        };
        for (int i : values) result.add(blocks[i]);
        return result;
    }
    static void setup() throws Exception {
        for (int i = 0; i < blocks.length; i++) {
            final int n = i;
            blocks[i] = proxy(IBlockStateWrapper.class, (p,m,a) -> {
                String name = m.getName();
                if (name.equals("hashCode")) return n + 1;
                if (name.equals("equals")) return p == a[0];
                if (name.equals("toString")) return "block" + n;
                if (recording) event(name + ":" + n);
                return switch(name) {
                    case "isSolid" -> n == 0 || n == 5 || n == 8;
                    case "isLiquid" -> n == 4;
                    case "getOpacity" -> n == 0 || n == 8 ? 16 : 0;
                    case "getMaterialId" -> (byte)(n == 4 ? 12 : n == 8 ? 3 : 2);
                    case "getSerialString" -> "minecraft:policy_" + n;
                    default -> zero(m);
                };
            });
        }
        if (stock) stockBlocks();
        biome = proxy(IBiomeWrapper.class, (p,m,a) -> switch(m.getName()) {
            case "hashCode" -> 42; case "equals" -> p == a[0];
            case "getSerialString" -> { event("biomeSerial"); yield "minecraft:plains"; }
            default -> zero(m);
        });
        var ignored = set("ignored", 1, 7); var caves = set("cave", 1);
        var factory = proxy(IWrapperFactory.class, (p,m,a) -> switch(m.getName()) {
            case "getRendererIgnoredBlocks" -> { event("ignoredSet"); yield ignored; }
            case "getRendererIgnoredCaveBlocks" -> { event("caveSet"); yield caves; }
            case "deserializeBlockStateWrapperOrGetDefault" -> blocks[3];
            case "getDelayedSetupComplete" -> true;
            default -> zero(m);
        });
        SingletonInjector.INSTANCE.bind(IWrapperFactory.class, factory);
        var dimension = proxy(IDimensionTypeWrapper.class, (p,m,a) -> {
            if (m.getName().equals("isTheEnd")) { event("end"); return end; }
            return zero(m);
        });
        level = proxy(IClientLevelWrapper.class, (p,m,a) -> switch(m.getName()) {
            case "getMinHeight" -> { event("minHeight"); yield -64; }
            case "hasCeiling" -> { event("ceiling"); yield ceiling; }
            case "getDimensionType" -> { event("dimension"); yield dimension; }
            case "getDhIdentifier" -> "policy-world";
            case "getBlockColor" -> {
                int n = id(a[3]); if (recording) event("color:" + n + ":" + ((DhBlockPos)a[0]).getY()); yield colors[n];
            }
            default -> zero(m);
        });
        if (resolvedWorld) {
            level = DhResolvedColorFixture.create(blocks, colors);
            biome = com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper.EMPTY_WRAPPER;
        }
        Class<?> transformer = Class.forName("com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer");
        for (Method m : transformer.getDeclaredMethods()) if (m.getName().equals("setRenderColumnView")) { convert = m; convert.setAccessible(true); }
        for (var constructor : ColumnArrayView.class.getConstructors()) if (constructor.getParameterCount() == 4) view = constructor;
        try { renderClose = ColumnRenderSource.class.getMethod("close"); } catch (NoSuchMethodException ignoredMethod) {}
    }
    static void stockBlocks() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        var field = unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object world = unsafeType.getMethod("allocateInstance", Class.class).invoke(field.get(null), net.minecraft.server.level.ServerLevel.class);
        var registryField = net.minecraft.world.level.Level.class.getDeclaredField("registryAccess"); registryField.setAccessible(true);
        registryField.set(world, net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var wrapper = proxy(ILevelWrapper.class, (p,m,a) -> m.getName().equals("getWrappedMcObject") ? world : zero(m));
        var states = new net.minecraft.world.level.block.state.BlockState[]{
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.STRUCTURE_VOID.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),
            net.minecraft.world.level.block.Blocks.ANDESITE.defaultBlockState()};
        for (int i = 0; i < blocks.length; i++) blocks[i] = com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.fromBlockState(states[i], wrapper);
    }
    static long point(int id, int bottom, int height, int sky, int block) {
        return id | ((long)height & 4095) << 32 | ((long)bottom & 4095) << 44 | (long)sky << 56 | (long)block << 60;
    }
    static long[] points(int test) {
        if (test == 0) return new long[]{point(0, 10, 2, 15, 0), point(8, 8, 2, 0, 7)};
        if (test == 1) return new long[]{point(0, 20, 2, 0, 0), point(1, 10, 10, 0, 0), point(8, 0, 10, 0, 0)};
        if (test == 2) return new long[]{point(2, 12, 1, 7, 3), point(6, 11, 1, 5, 5), point(0, 10, 1, 0, 0), point(4, 0, 10, 3, 4)};
        if (test == 3) return new long[]{point(3, 11, 1, 15, 0), point(3, 10, 0, 0, 0), point(0, 0, 10, 0, 0)};
        var random = new Random(test);
        var result = new long[1 + random.nextInt(24)];
        int top = test % 19 == 0 ? 8100 : 200;
        for (int i = 0; i < result.length; i++) {
            int height = random.nextInt(7); top -= height;
            result[i] = point(random.nextInt(blocks.length), top, height, random.nextInt(3) == 0 ? 15 : 0, random.nextInt(16));
        }
        return result;
    }
    static void run(int test, int failure, boolean semantic) throws Exception {
        recording = false; failAt = -1;
        Config.Client.Advanced.Graphics.Quality.blocksToIgnore.setWithoutFiringEvents(EDhApiBlocksToAvoid.values()[test % EDhApiBlocksToAvoid.values().length]);
        Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.setWithoutFiringEvents((test & 2) == 0);
        Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents((test & 4) == 0);
        Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(128);
        ceiling = test % 7 == 0; end = test % 11 == 0;
        var source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6, 0, 0));
        for (var block : blocks) source.mapping.addIfNotPresentAndGetId(biome, block);
        var input = new LongArrayList(points(test)) {
            @Override public int size() { event("size"); return super.size(); }
            @Override public long getLong(int i) { event("point:" + i); return super.getLong(i); }
        };
        var output = new LongArrayList(new long[32]) {
            @Override public long getLong(int i) { event("outputGet:" + i); return super.getLong(i); }
            @Override public long set(int i, long value) { event("outputSet:" + i + ":" + value); return super.set(i,value); }
        };
        var outputView = (ColumnArrayView)view.newInstance(output, 32, 0, 32);
        var render = ColumnRenderSource.createEmpty(source.getPos(), 32, -64);
        int[] materials = new int[32]; byte[] variants = new byte[32]; long[] positions = new long[32];
        trace.clear(); events = 0; failAt = failure; recording = true;
        String error = "none";
        try {
            if (convert.getParameterCount() == 6) convert.invoke(null, level, source, 1, 2, outputView, input);
            else if (convert.getParameterCount() == 13) convert.invoke(null, level, source, render, 0, 0, 1, 2, outputView, input, semantic, materials, variants, positions);
            else convert.invoke(null, level, source, render, 0, 0, 1, 2, outputView, input, semantic, materials, variants, positions, null);
        } catch (InvocationTargetException e) { error = e.getCause().getClass().getSimpleName() + ":" + e.getCause().getMessage(); }
        finally { recording = false; source.close(); if (renderClose != null) renderClose.invoke(render); }
        REPORT.println("CASE " + test + " " + failure + " " + error + " " + Arrays.toString(output.toLongArray())
            + " " + Arrays.toString(materials) + " " + Arrays.toString(variants) + " " + Arrays.toString(positions) + " " + trace);
    }
    static void benchmark(java.nio.file.Path manifest) throws Exception {
        var inputs = new ArrayList<LongArrayList>();
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(manifest)).getAsJsonObject();
        for (var element : json.getAsJsonArray("rows")) {
            var row = element.getAsJsonObject();
            var dto = com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
            dto.pos = DhSectionPos.encode((byte)(row.get("level").getAsInt() + 6), row.get("x").getAsInt(), row.get("z").getAsInt());
            dto.dataFormatVersion = (byte)row.get("version").getAsInt(); dto.compressionModeValue = (byte)row.get("compression").getAsInt();
            for (var field : row.getAsJsonObject("fields").entrySet())
                dto.getClass().getField(field.getKey()).set(dto, it.unimi.dsi.fastutil.bytes.ByteArrayList.wrap(
                    java.nio.file.Files.readAllBytes(manifest.getParent().resolve(field.getValue().getAsJsonObject().get("file").getAsString()))));
            try (dto; var source = dto.createUnitTestDataSource()) {
                for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
                    var original = source.getColumnAtRelPos(x,z); if (original == null || original.isEmpty()) continue;
                    var points = new LongArrayList(original.size());
                    for (int i = 0; i < original.size(); i++) {
                        long point = original.getLong(i);
                        points.add((point & 0xffff_ffff_0000_0000L) | ((point & 0x7fff_ffffL) % blocks.length));
                    }
                    inputs.add(points);
                }
            }
        }
        Config.Client.Advanced.Graphics.Quality.blocksToIgnore.setWithoutFiringEvents(EDhApiBlocksToAvoid.NON_COLLIDING);
        Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.setWithoutFiringEvents(true);
        Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(true);
        Config.Client.Advanced.Graphics.Culling.caveCullingHeight.setWithoutFiringEvents(128);
        ceiling = false; end = false; recording = false;
        int capacity = inputs.stream().mapToInt(LongArrayList::size).max().orElse(1);
        var output = new LongArrayList(new long[capacity]);
        var outputView = (ColumnArrayView)view.newInstance(output, capacity, 0, capacity);
        var source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte)6, 0, 0));
        for (var block : blocks) source.mapping.addIfNotPresentAndGetId(biome, block);
        var render = ColumnRenderSource.createEmpty(source.getPos(), 1, -64);
        var bean = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        REPORT.println("WORKLOAD " + inputs.size() + " columns " + inputs.stream().mapToInt(LongArrayList::size).sum() + " points");
        long expected = 0;
        try {
            for (int round = 0; round < 12; round++) {
                long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime(), hash = 1;
                for (var input : inputs) {
                    Arrays.fill(output.elements(), 0);
                    if (convert.getParameterCount() == 6) convert.invoke(null, level, source, 1, 2, outputView, input);
                    else if (convert.getParameterCount() == 13) convert.invoke(null, level, source, render, 0, 0, 1, 2, outputView, input, false, null, null, null);
                    else convert.invoke(null, level, source, render, 0, 0, 1, 2, outputView, input, false, null, null, null, null);
                    for (int i = 0; i < input.size(); i++) hash = 31 * hash + output.getLong(i);
                }
                long elapsed = System.nanoTime() - start;
                allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                if (round == 0) expected = hash; else if (hash != expected) throw new AssertionError("Changing output");
                if (round >= 4) REPORT.println("SAMPLE " + (round - 4) + " " + elapsed + " " + hash + " " + allocated);
            }
        } finally { source.close(); if (renderClose != null) renderClose.invoke(render); }
    }
    public static void main(String[] args) throws Exception {
        stock = args.length > 2 && Boolean.parseBoolean(args[2]);
        setup();
        if (args[0].equals("benchmark")) { benchmark(java.nio.file.Path.of(args[1])); return; }
        boolean semantic = Boolean.parseBoolean(args[0]);
        for (int test = 0; test < 128; test++) {
            run(test, -1, semantic);
            for (int failure : new int[]{0, 3, 8, 16, 32, 64}) run(test, failure, semantic);
        }
    }
}

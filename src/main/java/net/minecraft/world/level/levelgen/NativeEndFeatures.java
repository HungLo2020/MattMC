package net.minecraft.world.level.levelgen;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.*;

/** End geometry/decisions run in Rust; actual world effects stay on the caller's
 * thread, through the same Java methods as the original features. Each nested
 * invocation owns its callback state. No descriptors or world state are cached. */
public final class NativeEndFeatures {
    private static final java.lang.invoke.MethodHandle PLACE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_end_features_place",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle GENERATION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_end_features_generation",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final ThreadLocal<Call> CURRENT = new ThreadLocal<>();
    private static final class CellAction {
        static final java.lang.invoke.MethodHandle HANDLE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_end_platform_action",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), Linker.Option.critical(false));
    }
    private static volatile boolean enabled = !Boolean.getBoolean("mattmc.worldgen.javaEndFeatures")
        && !Boolean.getBoolean("mattmc.worldgen.javaDecoration");
    private static final class EffectCallbacks {
        static final Arena ARENA = Arena.ofAuto();
        static final MemorySegment[] STUBS = new MemorySegment[3];
        static {
            try {
                String[] names = {"spikeEffect", "gatewayEffect", "platformEffect"};
                for (int i = 0; i < names.length; i++) STUBS[i] = Linker.nativeLinker().upcallStub(
                    MethodHandles.lookup().findStatic(NativeEndFeatures.class, names[i], MethodType.methodType(int.class, int.class, MemorySegment.class, int.class)),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), ARENA);
            } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
    }
    private NativeEndFeatures() {}
    static void setEnabled(boolean value) { enabled = value; }

    private static boolean admitted(ServerLevelAccessor level, BlockPos origin) {
        return enabled && level != null && origin != null && (level.getClass() == ServerLevel.class || level.getClass() == WorldGenRegion.class)
            && (origin.getClass() == BlockPos.class || origin.getClass() == BlockPos.MutableBlockPos.class);
    }
    public static boolean spikes(SpikeFeature owner, FeaturePlaceContext<SpikeConfiguration> context) {
        if (context == null || context.getClass() != FeaturePlaceContext.class || context.config() == null) return false;
        if (!admitted(context.level(), context.origin()) || owner.getClass() != SpikeFeature.class
            || context.config().getClass() != SpikeConfiguration.class) return false;
        return run(0, new Call(context.level(), context.origin(), false, owner, context), false);
    }
    public static boolean gateway(EndGatewayFeature owner, FeaturePlaceContext<EndGatewayConfiguration> context) {
        if (context == null || context.getClass() != FeaturePlaceContext.class || context.config() == null) return false;
        var origin = context.origin();
        if (!admitted(context.level(), origin) || owner.getClass() != EndGatewayFeature.class
            || context.config().getClass() != EndGatewayConfiguration.class
            || origin.getX() == Integer.MIN_VALUE || origin.getX() == Integer.MAX_VALUE
            || origin.getZ() == Integer.MIN_VALUE || origin.getZ() == Integer.MAX_VALUE
            || (long)origin.getY() - 2 < Integer.MIN_VALUE || (long)origin.getY() + 2 > Integer.MAX_VALUE) return false;
        return run(1, new Call(context.level(), origin, false, null, context), false);
    }
    public static boolean platform(ServerLevelAccessor level, BlockPos origin, boolean destroy) {
        if (!admitted(level, origin)) return false;
        return run(2, new Call(level, origin, destroy, null, null), destroy);
    }
    private static boolean run(int kind, Call call, boolean destroy) {
        Call previous = CURRENT.get();
        CURRENT.set(call);
        int result;
        try {
            int flags = (call.origin instanceof BlockPos.MutableBlockPos ? 1 : 0) | (destroy ? 2 : 0);
            var region = kind <= 1 && flags == 0 ? NativeDecoration.endRegion(call.level) : null;
            result = 0;
            if (region != null) {
                try (var arena = Arena.ofConfined()) {
                    var materials = arena.allocate(32 * 4L, 4);
                    if (kind == 1) {
                        for (int i = 2; i <= 4; i++) materials.setAtIndex(ValueLayout.JAVA_INT, i, Block.getId(state(i)));
                    } else {
                        for (int i = 1; i <= 2; i++) materials.setAtIndex(ValueLayout.JAVA_INT, i, Block.getId(state(i)));
                        for (int i = 16; i < 32; i++) materials.setAtIndex(ValueLayout.JAVA_INT, i, Block.getId(state(i)));
                    }
                    result = (int)GENERATION.invokeExact(kind, call.origin.getX(), call.origin.getY(), call.origin.getZ(),
                        region.words(), (int)(region.words().byteSize() / 8), region.out(), (int)(region.out().byteSize() / 8),
                        materials, NativeDecoration.endCallback());
                }
            }
            // A declined generation binding has performed no callbacks or effects.
            if (result == 0) result = (int)PLACE.invokeExact(kind, call.origin.getX(), call.origin.getY(), call.origin.getZ(), flags, EffectCallbacks.STUBS[kind]);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Native End feature invocation failed", failure); }
        finally { CURRENT.set(previous); }
        if (call.failure != null) throw NativeEndFeatures.<RuntimeException>propagate(call.failure);
        if (result != 1) throw new IllegalStateException("Native End feature returned " + result);
        return true;
    }

    /** Preserve even a checked throwable supplied by an extension through a
     * Java effect. It must not escape through the native upcall trampoline. */
    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException propagate(Throwable failure) throws E { throw (E)failure; }

    private static final class Call {
        final ServerLevelAccessor level;
        final BlockPos origin;
        final SpikeFeature spikes;
        final FeaturePlaceContext<?> context;
        Iterator<SpikeFeature.EndSpike> iterator;
        SpikeFeature.EndSpike current;
        BlockPos.MutableBlockPos bodyPosition, cagePosition;
        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos gatewayCenter;
        Throwable failure;
        Call(ServerLevelAccessor level, BlockPos origin, boolean destroy, SpikeFeature spikes, FeaturePlaceContext<?> context) {
            this.level = level; this.origin = origin; this.spikes = spikes; this.context = context;
        }
        int next(MemorySegment args) {
            if (iterator == null) {
                var config = (SpikeConfiguration)context.config();
                var list = config.getSpikes();
                if (list.isEmpty()) list = SpikeFeature.getSpikesForLevel(context.level());
                iterator = list.iterator();
            }
            if (!iterator.hasNext()) return 0;
            current = iterator.next();
            bodyPosition = new BlockPos.MutableBlockPos();
            cagePosition = new BlockPos.MutableBlockPos();
            // Custom descriptors keep their original virtual method calls. Very
            // large/overflowing boxes retain BlockPos's Java iterator behavior.
            if (current == null || current.getClass() != SpikeFeature.EndSpike.class || !representable(current, level.getMinY())) {
                spikes.placeSpikeIfMatchingJava(level, context.random(), (SpikeConfiguration)context.config(), current, origin);
                return 2;
            }
            int[] values = {current.getCenterX(), current.getCenterZ(), current.getRadius(), current.getHeight(), current.isGuarded() ? 1 : 0,
                origin.getX(), origin.getZ()};
            for (int i = 0; i < values.length; i++) args.setAtIndex(ValueLayout.JAVA_INT, i, values[i]);
            return 1;
        }
    }
    private static boolean representable(SpikeFeature.EndSpike spike, int minY) {
        long x = spike.getCenterX(), z = spike.getCenterZ(), radius = spike.getRadius(), top = (long)spike.getHeight() + 10;
        // Below the air-clearing range, Java advances its body iterator through
        // skipped final cells. A write observer can retain that mutable cursor;
        // keep the original iterator instead of exposing its last written cell.
        if (top <= 65) return false;
        if (x - radius < Integer.MIN_VALUE || x - radius > Integer.MAX_VALUE || x + radius < Integer.MIN_VALUE || x + radius > Integer.MAX_VALUE
            || z - radius < Integer.MIN_VALUE || z - radius > Integer.MAX_VALUE || z + radius < Integer.MIN_VALUE || z + radius > Integer.MAX_VALUE
            || top < Integer.MIN_VALUE || top > Integer.MAX_VALUE) return false;
        long width = Math.abs(radius) * 2 + 1, height = Math.abs(top - minY) + 1;
        // Java's normalized iterator multiplies dimensions as signed ints.
        return width <= Integer.MAX_VALUE && height <= Integer.MAX_VALUE
            && width <= Integer.MAX_VALUE / width && height <= Integer.MAX_VALUE / (width * width);
    }
    private static BlockState state(int material) {
        return switch (material) {
            case 1 -> Blocks.OBSIDIAN.defaultBlockState();
            case 2 -> Blocks.AIR.defaultBlockState();
            case 3 -> Blocks.BEDROCK.defaultBlockState();
            case 4 -> Blocks.END_GATEWAY.defaultBlockState();
            default -> {
                if (material < 16 || material > 31) throw new IllegalArgumentException("End material " + material);
                int bits = material - 16;
                yield Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, (bits & 1) != 0)
                    .setValue(IronBarsBlock.SOUTH, (bits & 2) != 0).setValue(IronBarsBlock.WEST, (bits & 4) != 0).setValue(IronBarsBlock.EAST, (bits & 8) != 0);
            }
        };
    }
    /** End-only operation transported by the existing generation synchronizer.
     * It flushes metadata/events before this call and refreshes owners after it. */
    static int generationEffect(MemorySegment memory, int count) {
        var words = memory.reinterpret(count * 8L);
        int op = (int)words.getAtIndex(ValueLayout.JAVA_LONG, 0);
        if (op == 0) return 0;
        try (var arena = Arena.ofConfined()) {
            var args = arena.allocate((count - 1) * 4L, 4);
            for (int i = 1; i < count; i++) args.setAtIndex(ValueLayout.JAVA_INT, i - 1, (int)words.getAtIndex(ValueLayout.JAVA_LONG, i));
            int result = effect(op, args, count - 1);
            for (int i = 1; i < count; i++) words.setAtIndex(ValueLayout.JAVA_LONG, i, args.getAtIndex(ValueLayout.JAVA_INT, i - 1));
            return result;
        }
    }
    private static void write(Call call, int x, int y, int z, int material) {
        BlockPos pos;
        if (call.spikes != null) pos = (material >= 16 ? call.cagePosition : call.bodyPosition).set(x, y, z);
        else if (call.context == null) pos = call.position;
        else if (material == 4) {
            call.position.set(x, y, z);
            pos = call.gatewayCenter = new BlockPos(x, y, z);
        }
        else pos = call.position.set(x, y, z);
        call.level.setBlock(pos, state(material), 3);
    }
    private static int failed(Call call, Throwable failure) {
        if (call != null && call.failure == null) call.failure = failure;
        return -1;
    }
    private static int spikeEffect(int op, MemorySegment memory, int count) {
        if (op != 9) return effect(op, memory, count);
        Call call = CURRENT.get();
        if (call == null) return -1;
        try {
            var args = memory.reinterpret(count * 4L);
            for (int i = 0; i < count; i += 4) {
                int material = args.getAtIndex(ValueLayout.JAVA_INT, i + 3);
                var cursor = material >= 16 ? call.cagePosition : call.bodyPosition;
                cursor.set(args.getAtIndex(ValueLayout.JAVA_INT, i), args.getAtIndex(ValueLayout.JAVA_INT, i + 1), args.getAtIndex(ValueLayout.JAVA_INT, i + 2));
                call.level.setBlock(cursor, state(material), 3);
            }
            return 0;
        } catch (Throwable failure) { return failed(call, failure); }
    }
    private static int gatewayEffect(int op, MemorySegment memory, int count) {
        if (op != 9) return effect(op, memory, count);
        Call call = CURRENT.get();
        if (call == null) return -1;
        try {
            var args = memory.reinterpret(count * 4L);
            for (int i = 0; i < count; i += 4) {
                int x = args.getAtIndex(ValueLayout.JAVA_INT, i), y = args.getAtIndex(ValueLayout.JAVA_INT, i + 1), z = args.getAtIndex(ValueLayout.JAVA_INT, i + 2);
                int material = args.getAtIndex(ValueLayout.JAVA_INT, i + 3);
                // Java advances the frame iterator even when this write gets
                // an immutable centre copy. Observers may retain its cursor.
                call.position.set(x, y, z);
                BlockPos pos = material == 4 ? call.gatewayCenter = new BlockPos(x, y, z) : call.position;
                call.level.setBlock(pos, state(material), 3);
            }
            return 0;
        } catch (Throwable failure) { return failed(call, failure); }
    }
    private static void platformCell(Call call, MemorySegment args) throws Throwable {
        var desired = state(args.getAtIndex(ValueLayout.JAVA_INT, 3));
        boolean matches = call.level.getBlockState(call.position.set(args.getAtIndex(ValueLayout.JAVA_INT, 0),
            args.getAtIndex(ValueLayout.JAVA_INT, 1), args.getAtIndex(ValueLayout.JAVA_INT, 2))).is(desired.getBlock());
        int action = (int)CellAction.HANDLE.invokeExact(matches ? 1 : 0, args.getAtIndex(ValueLayout.JAVA_INT, 4));
        if ((action & 2) != 0) call.level.destroyBlock(call.position, true, null);
        if ((action & 1) != 0) call.level.setBlock(call.position, desired, 3);
    }
    private static int platformEffect(int op, MemorySegment memory, int count) {
        if (op != 10) return effect(op, memory, count);
        Call call = CURRENT.get();
        if (call == null) return -1;
        try { platformCell(call, memory.reinterpret(count * 4L)); return 0; }
        catch (Throwable failure) { return failed(call, failure); }
    }
    private static int effect(int op, MemorySegment memory, int count) {
        Call call = CURRENT.get();
        if (call == null) return -1;
        try {
            var args = memory.reinterpret(count * 4L);
            if (op == 1) { args.setAtIndex(ValueLayout.JAVA_INT, 0, call.level.getMinY()); return 0; }
            if (op == 2) return call.next(args);
            if (op == 8) {
                args.setAtIndex(ValueLayout.JAVA_INT, 0, call.origin.getX()); args.setAtIndex(ValueLayout.JAVA_INT, 1, call.origin.getY());
                args.setAtIndex(ValueLayout.JAVA_INT, 2, call.origin.getZ()); return 0;
            }
            if (op == 9) {
                // Arguments only: every original Java write still runs in
                // order, observing the world left by earlier callbacks. The
                // first Throwable stops the group and escapes with its prefix.
                for (int i = 0; i < count; i += 4) write(call,
                    args.getAtIndex(ValueLayout.JAVA_INT, i), args.getAtIndex(ValueLayout.JAVA_INT, i + 1),
                    args.getAtIndex(ValueLayout.JAVA_INT, i + 2), args.getAtIndex(ValueLayout.JAVA_INT, i + 3));
                return 0;
            }
            int x = args.getAtIndex(ValueLayout.JAVA_INT, 0), y = args.getAtIndex(ValueLayout.JAVA_INT, 1), z = args.getAtIndex(ValueLayout.JAVA_INT, 2);
            switch (op) {
                case 3 -> write(call, x, y, z, args.getAtIndex(ValueLayout.JAVA_INT, 3));
                case 4 -> { return call.level.getBlockState(call.position.set(x, y, z)).is(state(args.getAtIndex(ValueLayout.JAVA_INT, 3)).getBlock()) ? 1 : 0; }
                case 5 -> call.level.destroyBlock(call.position, true, null);
                case 6 -> call.spikes.placeCrystalJava(call.level, call.context.random(), (SpikeConfiguration)call.context.config(), call.current);
                case 7 -> {
                    if (call.gatewayCenter == null) call.gatewayCenter = new BlockPos(x, y, z);
                    var config = (EndGatewayConfiguration)call.context.config();
                    config.getExit().ifPresent(exit -> {
                        if (call.level.getBlockEntity(call.gatewayCenter) instanceof TheEndGatewayBlockEntity gateway) gateway.setExitPosition(exit, config.isExitExact());
                    });
                }
                case 10 -> platformCell(call, args);
                default -> throw new IllegalArgumentException("End operation " + op);
            }
            return 0;
        } catch (Throwable failure) { if (call.failure == null) call.failure = failure; return -1; }
    }
}

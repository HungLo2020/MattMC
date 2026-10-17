package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.core.Cursor3D;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.NativeCollisionGeometry;
import net.minecraft.world.phys.AABB;

/** Ordered world collisions over primary section owners, with no copied cell data. */
public final class NativeBlockCollisionScan {
    private NativeBlockCollisionScan() {}
    /** Query admission is fixed once, before the per-cell traversal begins. */
    public static boolean supports(Cursor3D cursor, AABB box, net.minecraft.world.phys.shapes.CollisionContext context) {
        int x=cursor.width(), y=cursor.height(), z=cursor.depth();
        return net.minecraft.world.phys.shapes.CollisionContext.hasDefaultShapeLookup(context)
            && x>0 && y>0 && z>0 && x<=128 && y<=128 && z<=128 && (long)x*y*z<=32768
            && (long)cursor.originX()+x<=Integer.MAX_VALUE && (long)cursor.originY()+y<=Integer.MAX_VALUE
            && (long)cursor.originZ()+z<=Integer.MAX_VALUE
            && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
            && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }
    private static final class Holder {
        static final MethodHandle SCAN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_collision_scan",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            Linker.Option.critical(false));
        static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    }
    private static final class Scratch {
        Cursor3D previousCursor;
        AABB previousBounds;
        final Arena arena = Arena.ofAuto();
        final MemorySegment cursor = arena.allocate(44, 4), bounds = arena.allocate(48, 8);
    }
    /** High word: excluded cells. Low 16 bits: StateID + 1; bit 16: resolved static hit. */
    public static long next(BlockGetter getter, Cursor3D cursor, AABB box, net.minecraft.core.BlockPos.MutableBlockPos position) {
        if (getter.getClass() != LevelChunk.class) return 0;
        var chunk = (LevelChunk)getter;
        var level = chunk.getLevel();
        if (level == null || (level.getClass() != net.minecraft.client.multiplayer.ClientLevel.class
            && level.getClass() != net.minecraft.server.level.ServerLevel.class)
            || chunk.levelHeightAccessor != level || level.isDebug()) return 0;
        if (!NativeCollisionGeometry.ready()) return 0;
        var inputs = chunk.nativeCollisionSections(false);
        if (inputs == null) return 0;
        var scratch = Holder.SCRATCH.get();
        if (scratch.previousCursor != cursor || scratch.previousBounds != box) {
            scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 0, cursor.originX());
            scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 1, cursor.originY());
            scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 2, cursor.originZ());
            scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 3, cursor.width());
            scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 4, cursor.height());
            scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 5, cursor.depth());
            scratch.bounds.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, box.minX);
            scratch.bounds.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, box.minY);
            scratch.bounds.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, box.minZ);
            scratch.bounds.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, box.maxX);
            scratch.bounds.setAtIndex(ValueLayout.JAVA_DOUBLE, 4, box.maxY);
            scratch.bounds.setAtIndex(ValueLayout.JAVA_DOUBLE, 5, box.maxZ);
            scratch.previousCursor = cursor; scratch.previousBounds = box;
        }
        scratch.cursor.setAtIndex(ValueLayout.JAVA_INT, 6, cursor.index() - 1);
        try {
            long result = call(inputs, scratch);
            if (result == -1) {
                var fresh = chunk.nativeCollisionSections(true);
                result = fresh != null && fresh != inputs ? call(fresh, scratch) : 0;
                if (result == -1) result = 0;
            }
            int skipped = (int)(result >>> 32);
            if (skipped > 0 && scratch.cursor.getAtIndex(ValueLayout.JAVA_INT, 10) != 0) position.set(scratch.cursor.getAtIndex(ValueLayout.JAVA_INT, 7),
                scratch.cursor.getAtIndex(ValueLayout.JAVA_INT, 8), scratch.cursor.getAtIndex(ValueLayout.JAVA_INT, 9));
            return result;
        }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot scan native collision cells", e); }
        finally { Reference.reachabilityFence(chunk); }
    }
    private static long call(NativeChunkInputs inputs, Scratch scratch) throws Throwable {
        try { return (long)Holder.SCAN.invokeExact(inputs.stageOwner(), scratch.cursor, scratch.bounds); }
        finally { Reference.reachabilityFence(inputs); }
    }

}

package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.AABB;

/** Bulk integer box extraction; original snapshots, coordinates and objects stay in Java. */
final class NativeVoxelBoxes {
    static final int MIN_CELLS = 512;
    static final int MAX_CELLS = 65536;
    private static final MethodHandle EXTRACT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_boxes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final ThreadLocal<Scratch> SCRATCH = new ThreadLocal<>();

    private NativeVoxelBoxes() {}

    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(1024 * 8, 8);
        final MemorySegment boxes;
        final int[] endpoints;
        boolean inUse;

        Scratch(int length) {
            int capacity = Integer.highestOneBit(length - 1) << 1;
            endpoints = new int[capacity];
            boxes = arena.allocate(capacity * 4L, 4);
        }
    }

    private static Scratch acquire(BitSetDiscreteVoxelShape grid) {
        int nx=grid.xSize, ny=grid.ySize, nz=grid.zSize;
        long cells=(long)nx*ny*nz;
        if (nx<1 || ny<1 || nz<1 || nx>256 || ny>256 || nz>256
                || cells<MIN_CELLS || cells>MAX_CELLS || grid.storage.length()>MAX_CELLS) return null;
        Scratch scratch=SCRATCH.get();
        if (scratch!=null && scratch.inUse) return null;
        int length=nx*ny*((nz+1)/2)*6;
        if (scratch==null || scratch.endpoints.length<length) {
            scratch=new Scratch(length);SCRATCH.set(scratch);
        }
        scratch.inUse=true;
        return scratch;
    }

    private static int extract(Scratch scratch, BitSetDiscreteVoxelShape snapshot) {
        long[] words=snapshot.storage.toLongArray();
        MemorySegment.copy(MemorySegment.ofArray(words),0,scratch.words,0,words.length*8L);
        int length=snapshot.xSize*snapshot.ySize*((snapshot.zSize+1)/2)*6;
        int count;
        try {
            count=(int)EXTRACT.invokeExact(scratch.words,words.length,snapshot.xSize,snapshot.ySize,snapshot.zSize,scratch.boxes,length);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native voxel box extraction failed",e); }
        if (count<0 || count>length/6) throw new IllegalStateException("Invalid native voxel box count: "+count);
        MemorySegment.copy(scratch.boxes,0,MemorySegment.ofArray(scratch.endpoints),0,count*24L);
        return count;
    }

    /** The input is the original caller's fresh clone, never the live grid. */
    static boolean emit(BitSetDiscreteVoxelShape snapshot, DiscreteVoxelShape.IntLineConsumer consumer) {
        if (consumer==null) return false;
        Scratch scratch=acquire(snapshot);
        if (scratch==null) return false;
        try {
            consume(scratch.endpoints,extract(scratch,snapshot),consumer);
            return true;
        } finally { scratch.inUse=false; }
    }

    private static void consume(int[] endpoints,int count,DiscreteVoxelShape.IntLineConsumer consumer) {
        for (int i=0;i<count*6;i+=6) {
            consumer.consume(endpoints[i],endpoints[i+1],endpoints[i+2],endpoints[i+3],endpoints[i+4],endpoints[i+5]);
        }
    }

    /** Complete built-in toAabbs caller, avoiding two intermediate callback layers. */
    static List<AABB> toAabbs(VoxelShape source) {
        // A custom VoxelShape may override forAllBoxes; it must retain virtual dispatch.
        if ((source.getClass()!=ArrayVoxelShape.class && source.getClass()!=CubeVoxelShape.class)
                || source.shape.getClass()!=BitSetDiscreteVoxelShape.class) return null;
        Scratch scratch=acquire((BitSetDiscreteVoxelShape)source.shape);
        if (scratch==null) return null;
        try {
            List<AABB> result=Lists.newArrayList();
            var x=source.getCoords(net.minecraft.core.Direction.Axis.X);
            var y=source.getCoords(net.minecraft.core.Direction.Axis.Y);
            var z=source.getCoords(net.minecraft.core.Direction.Axis.Z);
            // Preserve original list -> coordinates -> clone -> traversal order.
            var snapshot=new BitSetDiscreteVoxelShape(source.shape);
            int count=extract(scratch,snapshot);
            int[] b=scratch.endpoints;
            for (int i=0;i<count*6;i+=6) {
                result.add(new AABB(x.getDouble(b[i]),y.getDouble(b[i+1]),z.getDouble(b[i+2]),
                        x.getDouble(b[i+3]),y.getDouble(b[i+4]),z.getDouble(b[i+5])));
            }
            return result;
        } finally { scratch.inUse=false; }
    }
}

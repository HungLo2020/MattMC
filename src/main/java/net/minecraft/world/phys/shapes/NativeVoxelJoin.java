package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.BitSet;
import net.minecraft.util.NativeLibraryLoader;

/** One bulk occupancy join; coordinates and object ownership remain in Java. */
final class NativeVoxelJoin {
    static final int MIN_CELLS = 512;
    static final int MAX_CELLS = 262144;
    private static final BooleanOp[] OPS = {BooleanOp.FALSE,BooleanOp.NOT_OR,BooleanOp.ONLY_SECOND,BooleanOp.NOT_FIRST,
            BooleanOp.ONLY_FIRST,BooleanOp.NOT_SECOND,BooleanOp.NOT_SAME,BooleanOp.NOT_AND,BooleanOp.AND,BooleanOp.SAME,
            BooleanOp.SECOND,BooleanOp.CAUSES,BooleanOp.FIRST,BooleanOp.CAUSED_BY,BooleanOp.OR,BooleanOp.TRUE};
    private static final MethodHandle JOIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_join",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private NativeVoxelJoin() {}
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment a = arena.allocate(4096*8,8), b = arena.allocate(4096*8,8), out = arena.allocate(4096*8,8);
        final MemorySegment sizes = arena.allocate(6*4,4), maps = arena.allocate(1536*4,4), bounds = arena.allocate(6*4,4);
        final int[] dimensions = new int[6], mapping = new int[1536], limits = new int[6];
        boolean inUse;
    }
    static boolean trustedCoordinates(DoubleList list) {
        if(list==null)return false;
        var type=list.getClass();
        return type==DoubleArrayList.class || type==CubePointRange.class || type==DoubleLists.Singleton.class;
    }
    private static boolean trustedMerger(IndexMerger merger) {
        var type=merger.getClass();
        return type==DiscreteCubeMerger.class || type==IndirectMerger.class
                || (type==IdenticalMerger.class && trustedCoordinates(merger.getList()))
                || (type==NonOverlappingMerger.class && ((NonOverlappingMerger)merger).nativeCompatible());
    }
    static <T extends DiscreteVoxelShape> BitSetDiscreteVoxelShape join(T first,T second,
            IndexMerger x,IndexMerger y,IndexMerger z,BooleanOp op) {
        return join(first,second,x,y,z,op,null);
    }
    static <T extends DiscreteVoxelShape> BitSetDiscreteVoxelShape join(T first,T second,
            IndexMerger x,IndexMerger y,IndexMerger z,BooleanOp op,BitSetDiscreteVoxelShape target) {
        if (first==null || second==null || x==null || y==null || z==null || op==null) return null;
        if (first.getClass()!=BitSetDiscreteVoxelShape.class || second.getClass()!=BitSetDiscreteVoxelShape.class) return null;
        int truth=-1;
        for(int i=0;i<OPS.length;i++)if(op==OPS[i]){truth=i;break;}
        if(truth<0 || !trustedMerger(x) || !trustedMerger(y) || !trustedMerger(z)) return null;
        int nx=target==null?x.size()-1:target.xSize, ny=target==null?y.size()-1:target.ySize, nz=target==null?z.size()-1:target.zSize;
        long count=(long)nx*ny*nz;
        if(nx<1 || ny<1 || nz<1 || nx>256 || ny>256 || nz>256 || count<MIN_CELLS || count>MAX_CELLS
                || first.xSize>256 || first.ySize>256 || first.zSize>256 || second.xSize>256 || second.ySize>256 || second.zSize>256
                || (long)first.xSize*first.ySize*first.zSize>MAX_CELLS || (long)second.xSize*second.ySize*second.zSize>MAX_CELLS) return null;
        var a=(BitSetDiscreteVoxelShape)first;var b=(BitSetDiscreteVoxelShape)second;
        if(a.storage.length()>MAX_CELLS || b.storage.length()>MAX_CELLS) return null;
        var scratch=SCRATCH.get();if(scratch.inUse)return null;scratch.inUse=true;
        try {
            var aw=a.storage.toLongArray();var bw=b.storage.toLongArray();
            MemorySegment.copy(MemorySegment.ofArray(aw),0,scratch.a,0,aw.length*8L);
            MemorySegment.copy(MemorySegment.ofArray(bw),0,scratch.b,0,bw.length*8L);
            var dims=scratch.dimensions;dims[0]=first.xSize;dims[1]=first.ySize;dims[2]=first.zSize;
            dims[3]=second.xSize;dims[4]=second.ySize;dims[5]=second.zSize;
            MemorySegment.copy(MemorySegment.ofArray(dims),0,scratch.sizes,0,24);
            collect(x,scratch.mapping,0);collect(y,scratch.mapping,nx*2);collect(z,scratch.mapping,(nx+ny)*2);
            MemorySegment.copy(MemorySegment.ofArray(scratch.mapping),0,scratch.maps,0,(nx+ny+nz)*8L);
            int length=((int)count+63)/64;
            int status=(int)JOIN.invokeExact(scratch.a,aw.length,scratch.b,bw.length,scratch.sizes,scratch.maps,
                    nx,ny,nz,truth,scratch.out,length,scratch.bounds);
            if(status!=0)throw new IllegalStateException("Invalid native voxel join result: "+status);
            long[] words=new long[length];
            MemorySegment.copy(scratch.out,0,MemorySegment.ofArray(words),0,length*8L);
            MemorySegment.copy(scratch.bounds,0,MemorySegment.ofArray(scratch.limits),0,24);
            var result=target==null?new BitSetDiscreteVoxelShape(nx,ny,nz):target;
            result.storage.or(BitSet.valueOf(words));
            var bounds=scratch.limits;
            result.xMin=bounds[0];result.yMin=bounds[1];result.zMin=bounds[2];
            result.xMax=bounds[3];result.yMax=bounds[4];result.zMax=bounds[5];
            return result;
        } catch(RuntimeException|Error e){throw e;}
        catch(Throwable e){throw new IllegalStateException("Native voxel join failed",e);}
        finally{scratch.inUse=false;}
    }
    private static void collect(IndexMerger merger,int[] mapping,int offset) {
        merger.forMergedIndexes((a,b,k)->{mapping[offset+k*2]=a;mapping[offset+k*2+1]=b;return true;});
    }
}

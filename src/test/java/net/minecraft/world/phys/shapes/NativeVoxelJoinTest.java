package net.minecraft.world.phys.shapes;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVoxelJoinTest {
    static final BooleanOp[] OPS={BooleanOp.FALSE,BooleanOp.NOT_OR,BooleanOp.ONLY_SECOND,BooleanOp.NOT_FIRST,
        BooleanOp.ONLY_FIRST,BooleanOp.NOT_SECOND,BooleanOp.NOT_SAME,BooleanOp.NOT_AND,BooleanOp.AND,BooleanOp.SAME,
        BooleanOp.SECOND,BooleanOp.CAUSES,BooleanOp.FIRST,BooleanOp.CAUSED_BY,BooleanOp.OR,BooleanOp.TRUE};
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    record Pair(VoxelShape a,VoxelShape b) {}
    static VoxelShape grid(int x,int y,int z,long seed,String pattern,boolean cube,double shift) {
        var grid=new BitSetDiscreteVoxelShape(x,y,z);var random=new Random(seed);
        for(int i=0;i<x;i++)for(int j=0;j<y;j++)for(int k=0;k<z;k++) {
            boolean full=switch(pattern){case "full"->true;case "sparse"->random.nextInt(16)==0;
                case "checker"->((i+j+k)&1)==0;case "shell"->i==0||j==0||k==0||i==x-1||j==y-1||k==z-1;
                case "empty"->false;default->random.nextBoolean();};
            if(full)grid.fill(i,j,k);
        }
        if(cube)return new CubeVoxelShape(grid);
        return new ArrayVoxelShape(grid,coords(x,shift),coords(y,shift),coords(z,shift));
    }
    static DoubleList coords(int n,double shift){double[] values=new double[n+1];for(int i=0;i<=n;i++)values[i]=(double)i/n+shift;return DoubleArrayList.wrap(values);}
    static Pair fixture(String kind,int n,long seed,String pattern) {
        var a=grid(n,n,n,seed,pattern,kind.equals("cube"),0);
        if(kind.equals("anisotropic"))return new Pair(grid(n,n/2,n*2,seed,pattern,false,0),grid(n,n/2,n*2,seed+137,pattern,false,0));
        if(kind.equals("cube"))return new Pair(a,grid(n,n/2,n,seed+137,pattern,true,0));
        return new Pair(a,grid(n,n,n,seed+137,pattern,false,kind.equals("indirect")?.013:kind.equals("disjoint")?2:0));
    }
    static IndexMerger[] mergers(Pair pair,BooleanOp op) {
        boolean first=op.apply(true,false),second=op.apply(false,true);
        var x=Shapes.createIndexMerger(1,pair.a.getCoords(Direction.Axis.X),pair.b.getCoords(Direction.Axis.X),first,second);
        var y=Shapes.createIndexMerger(x.size()-1,pair.a.getCoords(Direction.Axis.Y),pair.b.getCoords(Direction.Axis.Y),first,second);
        var z=Shapes.createIndexMerger((x.size()-1)*(y.size()-1),pair.a.getCoords(Direction.Axis.Z),pair.b.getCoords(Direction.Axis.Z),first,second);
        return new IndexMerger[]{x,y,z};
    }
    static void equal(DiscreteVoxelShape a,DiscreteVoxelShape b) {
        assertEquals(a.getXSize(),b.getXSize());assertEquals(a.getYSize(),b.getYSize());assertEquals(a.getZSize(),b.getZSize());
        assertEquals(a.isEmpty(),b.isEmpty());
        for(var axis:Direction.Axis.values()){assertEquals(a.firstFull(axis),b.firstFull(axis));assertEquals(a.lastFull(axis),b.lastFull(axis));}
        if(a instanceof BitSetDiscreteVoxelShape x && b instanceof BitSetDiscreteVoxelShape y){
            assertArrayEquals(x.storage.toLongArray(),y.storage.toLongArray());assertEquals(x.storage.size(),y.storage.size());}
        for(int x=0;x<a.getXSize();x++)for(int y=0;y<a.getYSize();y++)for(int z=0;z<a.getZSize();z++)assertEquals(a.isFull(x,y,z),b.isFull(x,y,z));
    }
    static void equal(VoxelShape a,VoxelShape b) {
        assertEquals(a.getClass(),b.getClass());equal(a.shape,b.shape);
        for(var axis:Direction.Axis.values()){
            var x=a.getCoords(axis);var y=b.getCoords(axis);assertEquals(x.size(),y.size());
            for(int i=0;i<x.size();i++)assertEquals(Double.doubleToRawLongBits(x.getDouble(i)),Double.doubleToRawLongBits(y.getDouble(i)));}
    }
    static void compare(Pair pair,BooleanOp op,boolean nativeRequired) {
        var merges=mergers(pair,op);var aBefore=((BitSetDiscreteVoxelShape)pair.a.shape).storage.toLongArray();var bBefore=((BitSetDiscreteVoxelShape)pair.b.shape).storage.toLongArray();
        var original=JavaVoxelJoin.join(pair.a.shape,pair.b.shape,merges[0],merges[1],merges[2],op);
        var direct=NativeVoxelJoin.join(pair.a.shape,pair.b.shape,merges[0],merges[1],merges[2],op);
        if(nativeRequired)assertNotNull(direct,"Native bypassed");
        if(direct!=null)equal(original,direct);
        equal(original,BitSetDiscreteVoxelShape.join(pair.a.shape,pair.b.shape,merges[0],merges[1],merges[2],op));
        if(!op.apply(false,false))equal(JavaShapeJoin.joinUnoptimized(pair.a,pair.b,op),Shapes.joinUnoptimized(pair.a,pair.b,op));
        assertArrayEquals(aBefore,((BitSetDiscreteVoxelShape)pair.a.shape).storage.toLongArray());assertArrayEquals(bBefore,((BitSetDiscreteVoxelShape)pair.b.shape).storage.toLongArray());
    }
    @Test void allBooleanTablesMergerKindsPatternsAndWidths() {
        int cases=0;
        for(var kind:List.of("identical","cube","indirect","disjoint","anisotropic"))
            for(int n:new int[]{8,12,16})for(var pattern:List.of("random","full","sparse","checker","shell"))for(int seed=0;seed<3;seed++)
                for(var op:OPS){compare(fixture(kind,n,seed*1977L,pattern),op,true);cases++;}
        System.out.println("VOXEL_PARITY fixtures="+cases+" all_truth_tables=16");
    }
    @Test void boundarySizesEmptySentinelsPaddingAndInputBitsOutsideDimensions() {
        for(int n:new int[]{1,2,4,7,8,17,32})for(var op:OPS)compare(fixture("identical",n,71,"empty"),op,n>=8);
        var a=grid(17,9,7,71,"random",false,0);var b=grid(17,9,7,137,"random",false,0);
        var pair=new Pair(a,b);for(var op:OPS)compare(pair,op,true);
        ((BitSetDiscreteVoxelShape)a.shape).storage.set(17*9*7+31);
        for(var op:OPS)compare(pair,op,true);
        // Zero dimensions and unsupported size use the literal Java branch.
        var zero=new BitSetDiscreteVoxelShape(0,4,8);var m=new IdenticalMerger(coords(8,0));
        var emptyJoin=NativeVoxelJoin.join(zero,zero,m,m,m,BooleanOp.OR);assertNotNull(emptyJoin);
        equal(JavaVoxelJoin.join(zero,zero,m,m,m,BooleanOp.OR),emptyJoin);
        var huge=new BitSetDiscreteVoxelShape(65,65,65);
        assertNull(NativeVoxelJoin.join(huge,huge,new IdenticalMerger(coords(65,0)),m,m,BooleanOp.OR));
    }
    @Test void customOperatorsShapesAndCoordinateCallbacksRetainOriginalTrace() {
        var pair=fixture("identical",8,71,"random");var m=mergers(pair,BooleanOp.OR);
        var events=new ArrayList<String>();BooleanOp observed=(a,b)->{events.add(a+":"+b);return a||b;};
        var original=JavaVoxelJoin.join(pair.a.shape,pair.b.shape,m[0],m[1],m[2],observed);var expected=List.copyOf(events);events.clear();
        var migrated=BitSetDiscreteVoxelShape.join(pair.a.shape,pair.b.shape,m[0],m[1],m[2],observed);
        assertEquals(expected,events);equal(original,migrated);
        var coordinates=new it.unimi.dsi.fastutil.doubles.AbstractDoubleList(){
            public int size(){events.add("size");return 9;}
            public double getDouble(int i){events.add("get:"+i);return i/8.0;}};
        var custom=new IdenticalMerger(coordinates);events.clear();
        original=JavaVoxelJoin.join(pair.a.shape,pair.b.shape,custom,m[1],m[2],BooleanOp.OR);expected=List.copyOf(events);events.clear();
        migrated=BitSetDiscreteVoxelShape.join(pair.a.shape,pair.b.shape,custom,m[1],m[2],BooleanOp.OR);assertEquals(expected,events);equal(original,migrated);
        var customMerger=new IndexMerger(){public int size(){return 9;}public DoubleList getList(){return coords(8,0);}
            public boolean forMergedIndexes(IndexConsumer c){events.add("merge");for(int i=0;i<8;i++)if(!c.merge(i,i,i))return false;return true;}};
        assertNull(NativeVoxelJoin.join(pair.a.shape,pair.b.shape,customMerger,m[1],m[2],BooleanOp.OR));
        var subset=new SubShape(pair.a.shape,0,0,0,8,8,8);
        assertNull(NativeVoxelJoin.join(subset,pair.b.shape,m[0],m[1],m[2],BooleanOp.OR));
    }
    static List<Pair> registeredPairs(int probeSize) {
        var out=new ArrayList<Pair>();var probe=grid(probeSize,probeSize,probeSize,71,"shell",false,0);
        for(var state:Block.BLOCK_STATE_REGISTRY){
            var collision=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
            if(collision.isEmpty()||collision.shape.getClass()!=BitSetDiscreteVoxelShape.class)continue;
            out.add(new Pair(collision,probe));
        }
        return out;
    }
    @Test void registeredCollisionShapesAndPublicOptimizeBoxesCollisionIntegration() {
        int states=0,nativeCount=0;var pairs=registeredPairs(8);
        for(var pair:pairs){
            var a=JavaShapeJoin.joinUnoptimized(pair.a,pair.b,BooleanOp.OR);var b=Shapes.joinUnoptimized(pair.a,pair.b,BooleanOp.OR);equal(a,b);states++;
            var maps=mergers(pair,BooleanOp.OR);
            if(NativeVoxelJoin.join(pair.a.shape,pair.b.shape,maps[0],maps[1],maps[2],BooleanOp.OR)!=null)nativeCount++;
            if(states%257==0){assertEquals(a.toAabbs(),b.toAabbs());equal(a.optimize(),b.optimize());
                var box=new net.minecraft.world.phys.AABB(.1,.2,.3,.8,.9,.95);
                for(var axis:Direction.Axis.values())for(double distance:new double[]{-.75,-.1,0,.1,.75})
                    assertEquals(Double.doubleToRawLongBits(a.collide(axis,box,distance)),Double.doubleToRawLongBits(b.collide(axis,box,distance)));}
        }
        assertTrue(nativeCount>0);System.out.println("VOXEL_REGISTERED_PARITY nonempty_bitset_states="+states+" native_global_joins="+nativeCount);
    }
    @Test void floatingCoordinateEpsilonAndShortcutIdentityPreserved() {
        var pair=fixture("identical",8,71,"random");
        for(double offset:new double[]{-1e-7,-Math.nextUp(1e-7),0,Math.nextDown(1e-7),1e-7,Math.nextUp(1e-7),.013,2})
            for(var op:List.of(BooleanOp.OR,BooleanOp.AND,BooleanOp.ONLY_FIRST,BooleanOp.NOT_SAME)){
                var b=new ArrayVoxelShape(pair.b.shape,coords(8,offset),coords(8,offset),coords(8,offset));compare(new Pair(pair.a,b),op,false);}
        assertSame(pair.a,Shapes.joinUnoptimized(pair.a,pair.a,BooleanOp.OR));assertSame(Shapes.empty(),Shapes.joinUnoptimized(pair.a,pair.a,BooleanOp.NOT_SAME));
        assertSame(pair.a,Shapes.joinUnoptimized(pair.a,Shapes.empty(),BooleanOp.OR));
        assertThrows(IllegalArgumentException.class,()->Shapes.joinUnoptimized(pair.a,pair.b,BooleanOp.TRUE));
    }
    @Test void nullInternalArgumentsPreserveOriginalExceptionsAndCallbacks() {
        var pair=fixture("identical",8,71,"random");var m=mergers(pair,BooleanOp.OR);
        for(int missing=0;missing<6;missing++) {
            var a=missing==0?null:pair.a.shape;var b=missing==1?null:pair.b.shape;
            var x=missing==2?null:m[0];var y=missing==3?null:m[1];var z=missing==4?null:m[2];var op=missing==5?null:BooleanOp.OR;
            assertNull(NativeVoxelJoin.join(a,b,x,y,z,op));
            var original=assertThrows(NullPointerException.class,()->JavaVoxelJoin.join(a,b,x,y,z,op));
            var migrated=assertThrows(NullPointerException.class,()->BitSetDiscreteVoxelShape.join(a,b,x,y,z,op));
            assertEquals(original.getMessage(),migrated.getMessage());
        }
        var nullCoords=new IdenticalMerger(null);
        assertNull(NativeVoxelJoin.join(pair.a.shape,pair.b.shape,nullCoords,m[1],m[2],BooleanOp.OR));
    }
    @Test void independentThreadsScratchOwnershipAndGc() throws Exception {
        var executor=Executors.newFixedThreadPool(4);try{var futures=new ArrayList<Future<?>>();
            for(int thread=0;thread<4;thread++){final int seed=thread;futures.add(executor.submit(()->{for(int i=0;i<30;i++)compare(fixture("indirect",8,seed*1977L+i,"random"),BooleanOp.OR,true);}));}
            System.gc();for(var future:futures)future.get();
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(30,TimeUnit.SECONDS));}
    }
}

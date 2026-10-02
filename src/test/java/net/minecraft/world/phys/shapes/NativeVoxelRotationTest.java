package net.minecraft.world.phys.shapes;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.*;
import java.util.concurrent.*;
import net.math.OctahedralGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVoxelRotationTest {
    @BeforeAll static void bootstrap() { NativeVoxelBoxesTest.bootstrap(); }
    static final Vec3 CENTER = new Vec3(.5,.5,.5);
    static boolean eligible(DiscreteVoxelShape g) {
        long cells=(long)g.xSize*g.ySize*g.zSize;
        return g.getClass()==BitSetDiscreteVoxelShape.class && g.xSize>0&&g.ySize>0&&g.zSize>0
            && g.xSize<=256&&g.ySize<=256&&g.zSize<=256&&cells>=512&&cells<=65536
            && ((BitSetDiscreteVoxelShape)g).storage.length()>=0 && ((BitSetDiscreteVoxelShape)g).storage.length()<=65536;
    }
    static void same(DiscreteVoxelShape a, DiscreteVoxelShape b) {
        assertEquals(a.getClass(),b.getClass());
        assertEquals(a.xSize,b.xSize);assertEquals(a.ySize,b.ySize);assertEquals(a.zSize,b.zSize);
        var x=(BitSetDiscreteVoxelShape)a;var y=(BitSetDiscreteVoxelShape)b;
        assertArrayEquals(x.storage.toLongArray(),y.storage.toLongArray());assertEquals(x.storage.size(),y.storage.size());
        for(var axis:Direction.Axis.values()) {assertEquals(a.firstFull(axis),b.firstFull(axis));assertEquals(a.lastFull(axis),b.lastFull(axis));}
        assertEquals(a.isEmpty(),b.isEmpty());
    }
    static void same(VoxelShape a,VoxelShape b) {
        assertEquals(a.getClass(),b.getClass());same(a.shape,b.shape);
        for(var axis:Direction.Axis.values()) {
            var x=a.getCoords(axis);var y=b.getCoords(axis);assertEquals(x.size(),y.size());
            for(int i=0;i<x.size();i++)assertEquals(Double.doubleToRawLongBits(x.getDouble(i)),Double.doubleToRawLongBits(y.getDouble(i)));
        }
    }
    @Test void allGroupsComplexPatternsAndInputOwnership() {
        int comparisons=0;
        for(int[] d:new int[][]{{8,8,8},{16,16,16},{17,9,7},{3,3,65},{1,8,64},{256,1,256}})
        for(var pattern:List.of("empty","full","random","sparse","dense","checker","shell","slabs"))for(int seed=0;seed<3;seed++) {
            var g=NativeVoxelBoxesTest.grid(d[0],d[1],d[2],1977L*seed,pattern);
            var words=g.storage.toLongArray();int capacity=g.storage.size();
            for(var group:OctahedralGroup.values()) {
                if(group==OctahedralGroup.IDENTITY){assertSame(g,g.rotate(group));continue;}
                var expected=JavaVoxelRotation.rotate(g,group);var direct=NativeVoxelRotation.rotate(g,group);
                assertNotNull(direct);same(expected,direct);same(expected,g.rotate(group));
                // Result mutation cannot change the input or another returned result.
                var again=g.rotate(group);direct.fill(0,0,0);same(expected,again);comparisons++;
            }
            assertArrayEquals(words,g.storage.toLongArray());assertEquals(capacity,g.storage.size());
        }
        System.out.println("ROTATION_PARITY complex_fixtures=144 nonidentity_comparisons="+comparisons);
    }
    @Test void exhaustiveSmallAndUnsupportedCompatibility() {
        for(int bits=0;bits<4096;bits++) {
            var g=new BitSetDiscreteVoxelShape(2,2,3);
            for(int i=0;i<12;i++)if((bits&(1<<i))!=0)g.fill(i/6,(i/3)%2,i%3);
            for(var group:OctahedralGroup.values()) {
                if(group==OctahedralGroup.IDENTITY)assertSame(g,g.rotate(group));
                else same(JavaVoxelRotation.rotate(g,group),g.rotate(group));
            }
        }
        for(int[] d:new int[][]{{0,8,8},{8,0,8},{8,8,0},{7,8,9},{8,8,8},{257,1,2},{64,64,17}}) {
            var g=NativeVoxelBoxesTest.grid(d[0],d[1],d[2],71,"random");
            for(var group:OctahedralGroup.values())if(group!=OctahedralGroup.IDENTITY) {
                assertEquals(eligible(g),NativeVoxelRotation.rotate(g,group)!=null);
                same(JavaVoxelRotation.rotate(g,group),g.rotate(group));
            }
            assertThrows(NullPointerException.class,()->g.rotate(null));assertThrows(NullPointerException.class,()->JavaVoxelRotation.rotate(g,null));
        }
        var g=NativeVoxelBoxesTest.grid(17,9,7,71,"random");g.storage.set(17*9*7,17*9*7+55);g.xMin=-99;g.yMax=Integer.MAX_VALUE;
        for(var group:OctahedralGroup.values())if(group!=OctahedralGroup.IDENTITY)same(JavaVoxelRotation.rotate(g,group),g.rotate(group));
        g.storage.set(65537);assertFalse(eligible(g));same(JavaVoxelRotation.rotate(g,OctahedralGroup.INVERT_Z),g.rotate(OctahedralGroup.INVERT_Z));
        System.out.println("ROTATION_SMALL_PARITY exhaustive_java_grids=4096 groups=48");
    }
    @Test void overflowingBitSetLengthUsesOriginalLoopWithoutSnapshotCopy() {
        var grid=new BitSetDiscreteVoxelShape(8,8,8);
        grid.fill(0,0,1);
        // This is a real BitSet boundary, not mocked length metadata. The
        // highest legal index makes Java's int-valued length overflow.
        grid.storage.set(Integer.MAX_VALUE);
        assertEquals(Integer.MIN_VALUE,grid.storage.length());
        assertFalse(eligible(grid));assertSame(grid,grid.rotate(OctahedralGroup.IDENTITY));
        for(var group:OctahedralGroup.values())if(group!=OctahedralGroup.IDENTITY) {
            assertNull(NativeVoxelRotation.rotate(grid,group));
            same(JavaVoxelRotation.rotate(grid,group),grid.rotate(group));
        }
        assertTrue(grid.storage.get(Integer.MAX_VALUE));assertTrue(grid.isFull(0,0,1));
    }
    @Test void completePublicCallerCoordinatesCentersIdentityAndSpecialValues() {
        var grid=NativeVoxelBoxesTest.grid(8,8,8,71,"random");
        var unusual=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){return switch(i){
            case 0->-0.0;case 1->Double.NEGATIVE_INFINITY;case 2->Double.longBitsToDouble(0x7ff8000000000001L);
            case 3->Double.longBitsToDouble(0xfff8000000000002L);case 8->Double.POSITIVE_INFINITY;default->i/8.0;};}};
        for(var input:List.of(NativeVoxelBoxesTest.shape(grid),new CubeVoxelShape(grid),new ArrayVoxelShape(grid,unusual,unusual,unusual)))
        for(var center:List.of(CENTER,new Vec3(-0.0,1.125,-2.5),new Vec3(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)))
        for(var group:OctahedralGroup.values()) {
            if(group==OctahedralGroup.IDENTITY){assertSame(input,Shapes.rotate(input,group,center));continue;}
            same(JavaVoxelShapeRotation.rotate(input,group,center),Shapes.rotate(input,group,center));
        }
        assertNull(Shapes.rotate(null,OctahedralGroup.IDENTITY,null));
        assertThrows(NullPointerException.class,()->Shapes.rotate(NativeVoxelBoxesTest.shape(grid),null,CENTER));
        assertThrows(NullPointerException.class,()->Shapes.rotate(NativeVoxelBoxesTest.shape(grid),OctahedralGroup.INVERT_Z,null));
    }
    @Test void customGridAndCoordinatesKeepCallbacksExceptionsAndReentry() {
        var grid=NativeVoxelBoxesTest.grid(8,8,8,71,"checker");var events=new ArrayList<String>();
        var custom=new DiscreteVoxelShape(8,8,8) {
            public boolean isFull(int x,int y,int z){events.add(x+":"+y+":"+z);return grid.isFull(x,y,z);}
            public void fill(int x,int y,int z){throw new AssertionError();}
            public int firstFull(Direction.Axis axis){throw new AssertionError();}
            public int lastFull(Direction.Axis axis){throw new AssertionError();}
        };
        assertNull(NativeVoxelRotation.rotate(custom,OctahedralGroup.INVERT_Z));
        for(var group:OctahedralGroup.values())if(group!=OctahedralGroup.IDENTITY) {
            events.clear();var a=JavaVoxelRotation.rotate(custom,group);var trace=List.copyOf(events);events.clear();
            same(a,custom.rotate(group));assertEquals(trace,events);
        }
        var coordinates=new AbstractDoubleList(){
            public int size(){events.add("size");return 9;}
            public double getDouble(int i){events.add("get:"+i);same(JavaVoxelRotation.rotate(grid,OctahedralGroup.INVERT_X),grid.rotate(OctahedralGroup.INVERT_X));return i/8.0;}
        };
        var input=new ArrayVoxelShape(grid,coordinates,coordinates,coordinates);
        for(var group:OctahedralGroup.values()) {
            events.clear();var a=JavaVoxelShapeRotation.rotate(input,group,CENTER);var trace=List.copyOf(events);events.clear();
            var b=Shapes.rotate(input,group,CENTER);assertEquals(trace,events);same(a,b);
        }
        var error=new IllegalStateException("coordinate failure");
        var throwing=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){throw error;}};
        var bad=new ArrayVoxelShape(grid,throwing,throwing,throwing);
        assertSame(error,assertThrows(IllegalStateException.class,()->JavaVoxelShapeRotation.rotate(bad,OctahedralGroup.INVERSION,CENTER)));
        assertSame(error,assertThrows(IllegalStateException.class,()->Shapes.rotate(bad,OctahedralGroup.INVERSION,CENTER)));
        same(JavaVoxelRotation.rotate(grid,OctahedralGroup.INVERSION),grid.rotate(OctahedralGroup.INVERSION));
    }
    @Test void everyRegisteredStateAllGroupsAndDerivedComplexJoins() {
        int states=0,eligible=0,rotations=0;
        for(var state:Block.BLOCK_STATE_REGISTRY) {
            var input=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);states++;
            if(eligible(input.shape))eligible++;
            for(var group:OctahedralGroup.values()) {
                var a=JavaVoxelShapeRotation.rotate(input,group,CENTER);var b=Shapes.rotate(input,group,CENTER);
                if(group==OctahedralGroup.IDENTITY){assertSame(input,b);continue;}
                same(a,b);rotations++;
            }
        }
        int joined=0;
        for(var pair:NativeVoxelJoinTest.registeredPairs(8)) {
            var input=Shapes.joinUnoptimized(pair.a(),pair.b(),BooleanOp.OR);
            for(var group:OctahedralGroup.values())if(group!=OctahedralGroup.IDENTITY)
                same(JavaVoxelShapeRotation.rotate(input,group,CENTER),Shapes.rotate(input,group,CENTER));
            if(++joined==64)break;
        }
        System.out.println("ROTATION_REGISTERED_PARITY states="+states+" native_original_grids="+eligible+" nonidentity_comparisons="+rotations+" joined_grids="+joined);
    }
    @Test void concurrentCallsAndChangingInputAlwaysRecompute() throws Exception {
        var grid=NativeVoxelBoxesTest.grid(16,16,16,71,"random");var words=grid.storage.toLongArray();
        var pool=Executors.newFixedThreadPool(4);
        try {
            var tasks=new ArrayList<Future<?>>();
            for(int t=0;t<4;t++)tasks.add(pool.submit(()->{for(int i=0;i<300;i++){
                var group=OctahedralGroup.values()[1+i%47];same(JavaVoxelRotation.rotate(grid,group),grid.rotate(group));
                if(i%100==0)System.gc();
            }}));
            for(var task:tasks)task.get();
        } finally {pool.shutdownNow();}
        assertArrayEquals(words,grid.storage.toLongArray());
        for(int i=0;i<100;i++) {grid.storage.flip(i*7);same(JavaVoxelRotation.rotate(grid,OctahedralGroup.INVERSION),grid.rotate(OctahedralGroup.INVERSION));}
    }
}

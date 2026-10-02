package net.minecraft.world.phys.shapes;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVoxelBoxesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    record Box(int x, int y, int z, int xx, int yy, int zz) {}
    static BitSetDiscreteVoxelShape grid(int nx, int ny, int nz, long seed, String pattern) {
        var grid = new BitSetDiscreteVoxelShape(nx, ny, nz); var random = new Random(seed);
        for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
            boolean filled=switch(pattern) {
                case "empty" -> false; case "full" -> true;
                case "checker" -> ((x+y+z)&1)==0; case "sparse" -> random.nextInt(16)==0;
                case "dense" -> random.nextInt(16)!=0;
                case "shell" -> x==0||y==0||z==0||x==nx-1||y==ny-1||z==nz-1;
                case "slabs" -> (y&1)==0; default -> random.nextBoolean();
            };
            if (filled) grid.fill(x,y,z);
        }
        return grid;
    }
    static VoxelShape shape(BitSetDiscreteVoxelShape grid) {
        return new ArrayVoxelShape(grid,NativeVoxelJoinTest.coords(grid.xSize,0),
            NativeVoxelJoinTest.coords(grid.ySize,0),NativeVoxelJoinTest.coords(grid.zSize,0));
    }
    static List<Box> original(DiscreteVoxelShape grid,boolean merge) {
        var out=new ArrayList<Box>();JavaVoxelBoxes.forAllBoxes(grid,(x,y,z,xx,yy,zz)->out.add(new Box(x,y,z,xx,yy,zz)),merge);return out;
    }
    static List<Box> migrated(DiscreteVoxelShape grid,boolean merge) {
        var out=new ArrayList<Box>();grid.forAllBoxes((x,y,z,xx,yy,zz)->out.add(new Box(x,y,z,xx,yy,zz)),merge);return out;
    }
    static void compare(DiscreteVoxelShape grid,boolean required) {
        var expected=original(grid,true);
        var direct=new ArrayList<Box>();boolean used=NativeVoxelBoxes.emit(new BitSetDiscreteVoxelShape(grid),
            (x,y,z,xx,yy,zz)->direct.add(new Box(x,y,z,xx,yy,zz)));
        if(required)assertTrue(used,"Expected native grid: "+grid.xSize+","+grid.ySize+","+grid.zSize);
        if(used)assertEquals(expected,direct);
        assertEquals(expected,migrated(grid,true));
        // Independently reconstruct the complete occupied grid from the emitted boxes.
        var seen=new BitSet(grid.xSize*grid.ySize*grid.zSize);
        for(var b:expected)for(int x=b.x;x<b.xx;x++)for(int y=b.y;y<b.yy;y++)for(int z=b.z;z<b.zz;z++){
            int id=(x*grid.ySize+y)*grid.zSize+z;assertFalse(seen.get(id));seen.set(id);}
        for(int x=0;x<grid.xSize;x++)for(int y=0;y<grid.ySize;y++)for(int z=0;z<grid.zSize;z++)
            assertEquals(grid.isFull(x,y,z),seen.get((x*grid.ySize+y)*grid.zSize+z));
    }
    @Test void exhaustiveSmallGridsAndComplexPatterns() {
        for(int bits=0;bits<4096;bits++) {
            var grid=new BitSetDiscreteVoxelShape(2,2,3);
            for(int i=0;i<12;i++)if((bits&(1<<i))!=0)grid.fill(i/6,(i/3)%2,i%3);
            assertEquals(original(grid,true),migrated(grid,true));
        }
        int fixtures=0;
        for(int[] d:new int[][]{{8,8,8},{16,16,16},{17,9,7},{3,3,65},{1,8,64},{8,1,64},{32,8,16},{256,1,256}})
            for(String pattern:List.of("empty","full","random","sparse","dense","checker","shell","slabs"))for(int seed=0;seed<4;seed++) {
                var grid=grid(d[0],d[1],d[2],seed*1977L,pattern);var before=grid.storage.toLongArray();int capacity=grid.storage.size();
                compare(grid,true);assertArrayEquals(before,grid.storage.toLongArray());assertEquals(capacity,grid.storage.size());fixtures++;
            }
        System.out.println("BOX_PARITY exhaustive_java_grids=4096 complex_fixtures="+fixtures);
    }
    @Test void boundaryWidthsPaddingStaleBoundsAndUnmergedCompatibility() {
        for(int[] d:new int[][]{{0,8,8},{8,0,8},{8,8,0},{1,1,1},{7,8,9},{8,8,8},{1,1,65},{256,256,2},{257,1,2}}) {
            var grid=grid(d[0],d[1],d[2],71,"random");compare(grid,(long)d[0]*d[1]*d[2]>=512 && (long)d[0]*d[1]*d[2]<=65536 && d[0]<=256);
            assertEquals(original(grid,false),migrated(grid,false));
        }
        var odd=grid(17,9,7,71,"random");odd.storage.set(17*9*7,17*9*7+31);odd.xMin=-77;odd.zMax=Integer.MAX_VALUE;compare(odd,true);
        odd.storage.set(65537);compare(odd,false);
        var empty=grid(8,8,8,71,"empty");assertFalse(NativeVoxelBoxes.emit(empty,null));
        JavaVoxelBoxes.forAllBoxes(empty,null,true);empty.forAllBoxes(null,true);
        var full=grid(8,8,8,71,"full");assertThrows(NullPointerException.class,()->full.forAllBoxes(null,true));
        assertThrows(NullPointerException.class,()->JavaVoxelBoxes.forAllBoxes(null,(a,b,c,d,e,f)->{},true));
        assertThrows(NullPointerException.class,()->BitSetDiscreteVoxelShape.forAllBoxes(null,(a,b,c,d,e,f)->{},true));
    }
    @Test void registeredCollisionShapesAndJoinOutputs() {
        int states=0,eligible=0,joined=0;var probe=shape(grid(8,8,8,71,"shell"));
        for(var state:Block.BLOCK_STATE_REGISTRY) {
            var s=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);states++;
            assertEquals(JavaVoxelBoxList.toAabbs(s),s.toAabbs());
            if(s.shape.xSize>0&&s.shape.ySize>0&&s.shape.zSize>0) {
                var g=new BitSetDiscreteVoxelShape(s.shape);
                if((long)g.xSize*g.ySize*g.zSize>=512&&(long)g.xSize*g.ySize*g.zSize<=65536&&g.xSize<=256&&g.ySize<=256&&g.zSize<=256)eligible++;
            }
            if(states%47==0&&!s.isEmpty()) {
                var combined=Shapes.joinUnoptimized(s,probe,BooleanOp.OR);
                assertEquals(JavaVoxelBoxList.toAabbs(combined),combined.toAabbs());compare(combined.shape,true);joined++;
            }
        }
        System.out.println("BOX_REGISTERED_PARITY states="+states+" complex_original_grids="+eligible+" joined_grids="+joined);
    }
    @Test void publicCoordinatesCallbackOrderAndConsumersRetainExactBits() {
        var grid=grid(8,8,8,71,"checker");var events=new ArrayList<String>();
        var coordinates=new AbstractDoubleList() {
            public int size(){events.add("size");return 9;}
            public double getDouble(int i){events.add("get:"+i);return switch(i){case 0->-0.0;case 1->Double.NEGATIVE_INFINITY;case 2->Double.longBitsToDouble(0x7ff8000000000001L);case 8->Double.POSITIVE_INFINITY;default->i/8.0;};}
        };
        var s=new ArrayVoxelShape(grid,coordinates,coordinates,coordinates);events.clear();
        var expected=new ArrayList<List<Long>>();JavaVoxelBoxList.forAllBoxes(s,(a,b,c,d,e,f)->expected.add(raw(a,b,c,d,e,f)));
        var trace=List.copyOf(events);events.clear();var actual=new ArrayList<List<Long>>();
        s.forAllBoxes((a,b,c,d,e,f)->actual.add(raw(a,b,c,d,e,f)));assertEquals(expected,actual);assertEquals(trace,events);
        // A callback changes coordinates; all later coordinate reads still occur in Java at the same point.
        var coords=DoubleArrayList.wrap(new double[]{0,.1,.2,.3,.4,.5,.6,.7,1});
        s=new ArrayVoxelShape(grid,coords,coords,coords);var receiver=s;
        var first=new ArrayList<List<Long>>();JavaVoxelBoxList.forAllBoxes(receiver,(a,b,c,d,e,f)->{first.add(raw(a,b,c,d,e,f));coords.set(4,.499);});
        coords.set(4,.4);var second=new ArrayList<List<Long>>();receiver.forAllBoxes((a,b,c,d,e,f)->{second.add(raw(a,b,c,d,e,f));coords.set(4,.499);});assertEquals(first,second);
    }
    private static List<Long> raw(double... values){return Arrays.stream(values).mapToObj(Double::doubleToRawLongBits).toList();}
    @Test void completeBoxListCustomCoordinatesOverridesExceptionsAndReentry() {
        var g=grid(8,8,8,71,"checker");var events=new ArrayList<String>();
        var coordinates=new AbstractDoubleList(){
            public int size(){events.add("size");return 9;}
            public double getDouble(int i){events.add("get:"+i);return i==0?-0.0:i==8?Double.POSITIVE_INFINITY:i/8.0;}
        };
        var s=new ArrayVoxelShape(g,coordinates,coordinates,coordinates);events.clear();
        var expected=JavaVoxelBoxList.toAabbs(s);var trace=List.copyOf(events);events.clear();
        assertEquals(expected,s.toAabbs());assertEquals(trace,events);
        var unusual=new AbstractDoubleList(){
            public int size(){return 9;}
            public double getDouble(int i){return switch(i){case 0->-0.0;case 1->Double.NEGATIVE_INFINITY;
                case 2->Double.longBitsToDouble(0x7ff8000000000001L);case 3->Double.longBitsToDouble(0x7ff8000000000002L);
                case 8->Double.POSITIVE_INFINITY;default->i/8.0;};}
        };
        var nonfinite=new ArrayVoxelShape(g,unusual,unusual,unusual);
        var reference=JavaVoxelBoxList.toAabbs(nonfinite);var output=nonfinite.toAabbs();assertEquals(reference.size(),output.size());
        for(int i=0;i<reference.size();i++){var a=reference.get(i);var b=output.get(i);
            assertEquals(raw(a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ),raw(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));}
        var special=new VoxelShape(g){
            public it.unimi.dsi.fastutil.doubles.DoubleList getCoords(Direction.Axis a){throw new AssertionError("Unexpected coordinate read");}
            public void forAllBoxes(Shapes.DoubleLineConsumer c){events.add("override");c.consume(1,2,3,4,5,6);}
        };
        events.clear();assertEquals(List.of(new AABB(1,2,3,4,5,6)),special.toAabbs());assertEquals(List.of("override"),events);
        var failure=new IllegalStateException("coordinate");
        var throwing=new AbstractDoubleList(){public int size(){return 9;}public double getDouble(int i){throw failure;}};
        var broken=new ArrayVoxelShape(g,throwing,throwing,throwing);
        assertSame(failure,assertThrows(IllegalStateException.class,broken::toAabbs));
        assertSame(failure,assertThrows(IllegalStateException.class,()->JavaVoxelBoxList.toAabbs(broken)));
        var nested=shape(grid(8,8,8,137,"full"));
        var reentrant=new AbstractDoubleList(){
            public int size(){return 9;}
            public double getDouble(int i){
                assertFalse(NativeVoxelBoxes.emit(new BitSetDiscreteVoxelShape(nested.shape),(a,b,c,d,e,f)->{}));
                assertEquals(List.of(new AABB(0,0,0,1,1,1)),nested.toAabbs());return i/8.0;
            }
        };
        var outer=new ArrayVoxelShape(g,reentrant,reentrant,reentrant);
        assertEquals(JavaVoxelBoxList.toAabbs(shape(g)),outer.toAabbs());
        // An exception or nested callback must always release the numeric workspace.
        assertNotNull(NativeVoxelBoxes.toAabbs(shape(g)));
    }
    @Test void customGridSnapshotCallbacksMutationsAndReentry() {
        var events=new ArrayList<String>();var base=grid(8,8,8,71,"random");
        var custom=new DiscreteVoxelShape(8,8,8) {
            public boolean isFull(int x,int y,int z){events.add("read:"+x+":"+y+":"+z);return base.isFull(x,y,z);}
            public void fill(int x,int y,int z){base.fill(x,y,z);}
            public int firstFull(Direction.Axis a){events.add("min:"+a);return base.firstFull(a);}
            public int lastFull(Direction.Axis a){events.add("max:"+a);return base.lastFull(a);}
        };
        var original=original(custom,true);var trace=List.copyOf(events);events.clear();
        assertEquals(original,migrated(custom,true));assertEquals(trace,events);
        var outer=grid(8,8,8,71,"checker");var before=original(outer,true);var nested=grid(8,8,8,137,"random");
        var actual=new ArrayList<Box>();outer.forAllBoxes((x,y,z,xx,yy,zz)->{
            actual.add(new Box(x,y,z,xx,yy,zz));outer.fill(0,0,1);
            assertFalse(NativeVoxelBoxes.emit(new BitSetDiscreteVoxelShape(nested),(a,b,c,d,e,f)->{}));
            assertEquals(original(nested,true),migrated(nested,true));
        },true);assertEquals(before,actual);
    }
    @Test void consumerExceptionIdentityPrefixAndScratchRelease() {
        var grid=grid(8,8,8,71,"checker");var failure=new IllegalStateException("consumer");
        var expected=new ArrayList<Box>();assertSame(failure,assertThrows(IllegalStateException.class,()->JavaVoxelBoxes.forAllBoxes(grid,(x,y,z,xx,yy,zz)->{
            expected.add(new Box(x,y,z,xx,yy,zz));if(expected.size()==17)throw failure;},true)));
        var actual=new ArrayList<Box>();assertSame(failure,assertThrows(IllegalStateException.class,()->grid.forAllBoxes((x,y,z,xx,yy,zz)->{
            actual.add(new Box(x,y,z,xx,yy,zz));if(actual.size()==17)throw failure;},true)));assertEquals(expected,actual);compare(grid,true);
    }
    @Test void concurrentGcAndIndependentScratch() throws Exception {
        var executor=Executors.newFixedThreadPool(4);try {
            var jobs=new ArrayList<Future<?>>();for(int t=0;t<4;t++){final int seed=t;jobs.add(executor.submit(()->{for(int n=0;n<40;n++)compare(grid(17,9,7,seed*1977L+n,"random"),true);}));}
            System.gc();for(var job:jobs)job.get();
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(30,TimeUnit.SECONDS));}
    }
}

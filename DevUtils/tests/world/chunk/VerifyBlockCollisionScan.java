package net.minecraft.client;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Frozen production iterator oracle, including real client/chunk/section reads. */
public final class VerifyBlockCollisionScan {
    static VerifyCameraEnvironment.Fixture fixture() throws Exception {
        var f=new VerifyCameraEnvironment.Fixture();
        var dimension=new DimensionType(OptionalLong.empty(),true,false,false,true,1.,true,false,
            -64,384,384,null,null,0,Optional.empty(),null);
        VerifyCameraEnvironment.set(Level.class,f.level,"dimensionTypeRegistration",Holder.direct(dimension));
        var random=new Random(2385041);
        var blocks=new Block[]{Blocks.AIR,Blocks.AIR,Blocks.AIR,Blocks.STONE,Blocks.OAK_SLAB,Blocks.OAK_STAIRS,
            Blocks.COBBLESTONE_WALL,Blocks.OAK_FENCE,Blocks.SNOW,Blocks.POWDER_SNOW,Blocks.WATER,Blocks.LAVA};
        for(var row:f.chunks)for(var chunk:row){
            var sections=new LevelChunkSection[24];
            for(int i=0;i<24;i++)sections[i]=new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
            for(int y=3;y<=5;y++)for(int i=0;i<4096;i++){
                var block=blocks[random.nextInt(blocks.length)];
                var states=block.getStateDefinition().getPossibleStates();
                var state=states.get(random.nextInt(states.size()));
                sections[y].setBlockState(i&15,i>>8,i>>4&15,state);
            }
            VerifyCameraEnvironment.set(ChunkAccess.class,chunk,"sections",sections);
        }
        return f;
    }
    static AABB[] probes(){
        var random=new Random(985104);var boxes=new AABB[2048];
        double[] offsets={0,1e-8,1e-7,4e-7,0.5,0.99999999};
        for(int i=0;i<boxes.length;i++){
            double x=random.nextInt(52)-18+offsets[i%offsets.length],y=random.nextInt(52)-20+offsets[i/6%6],z=random.nextInt(52)-18+offsets[i/36%6];
            double w=i%7==0?1e-8:i%5==0?1e-7:0.6,h=i%3==0?1.8:0.9;
            boxes[i]=new AABB(x,y,z,x+w,y+h,z+w);
        }return boxes;
    }
    static List<AABB> staticProbes(){
        var boxes=new ArrayList<AABB>();double eps=1e-7;
        for(int axis=0;axis<3;axis++)for(double boundary:new double[]{0,.125,.5,.875,1,1.5})
            for(double shift:new double[]{-1.1*eps,-eps,-.5*eps,0,.5*eps,eps,1.1*eps})
                for(double width:new double[]{1e-8,eps,2*eps,.6}){
                    double[] min={.1,.1,.1},max={.9,.9,.9};min[axis]=boundary+shift;max[axis]=min[axis]+width;
                    boxes.add(new AABB(min[0],min[1],min[2],max[0],max[1],max[2]));
                }
        for(double x:new double[]{0,.5,1})for(double y:new double[]{0,.5,1})for(double z:new double[]{0,.5,1})
            boxes.add(new AABB(x-.4*eps,y-.4*eps,z-.4*eps,x+.6*eps,y+.6*eps,z+.6*eps));
        return boxes;
    }
    static void cell(VerifyCameraEnvironment.Fixture f,int x,int y,int z,net.minecraft.world.level.block.state.BlockState state){
        f.chunks[(z>>4)+1][(x>>4)+1].getSection((y+64)>>4).setBlockState(x&15,y&15,z&15,state);
    }
    static void compare(byte[] got,DataInputStream in,DataOutputStream out,int index)throws Exception{
        if(in!=null){int n=in.readInt();byte[] expected=in.readNBytes(n);if(!Arrays.equals(got,expected))throw new AssertionError("Collision case "+index+" differs");}
        else{out.writeInt(got.length);out.write(got);}
    }
    static byte[] observe(Level level,AABB box,CollisionContext context,boolean suffocating)throws Exception{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        var iterator=new BlockCollisions<BlockPos>(level,context,box,suffocating,(p,s)->{
            try{
                out.writeInt(p.getX());out.writeInt(p.getY());out.writeInt(p.getZ());
                var shapes=s.toAabbs();out.writeInt(shapes.size());
                for(var b:shapes)for(double d:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})out.writeLong(Double.doubleToRawLongBits(d));
            }catch(IOException e){throw new UncheckedIOException(e);}
            return p;
        });
        while(iterator.hasNext())iterator.next();
        return bytes.toByteArray();
    }
    static long queryLoop(Level level,AABB[] probes){
        long checksum=0;
        for(int i=0;i<65536;i++)if(level.noBlockCollision(null,probes[i&2047]))checksum++;
        return checksum;
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        boolean current=args[0].equals("current");
        for(String name:List.of("net.minecraft.world.level.BlockCollisions","net.minecraft.core.Cursor3D",
            "net.minecraft.world.level.CollisionGetter","net.minecraft.world.level.chunk.LevelChunk",
            "net.minecraft.world.level.chunk.LevelChunkSection","net.minecraft.world.level.chunk.PalettedContainer",
            "net.minecraft.world.phys.shapes.EntityCollisionContext","net.minecraft.world.phys.shapes.CollisionContext","net.minecraft.world.phys.shapes.Shapes"))VerifyCameraEnvironment.image(name);
        if(current)for(String name:List.of("net.minecraft.world.level.chunk.NativeBlockCollisionScan",
            "net.minecraft.world.level.chunk.NativeBlockCollisionScan$Holder","net.minecraft.world.level.chunk.NativeBlockCollisionScan$Scratch",
            "net.minecraft.world.level.chunk.NativeChunkInputs","net.minecraft.world.level.block.NativeCollisionGeometry"))VerifyCameraEnvironment.image(name);
        var f=fixture();var probes=probes();
        if(current){
            // Construction changed height/section fields directly; discard only
            // the fixture's pre-construction cache before exercising real queries.
            for(var row:f.chunks)for(var chunk:row)VerifyCameraEnvironment.set(ChunkAccess.class,chunk,"nativeDhInputs",null);
            var cursor=new net.minecraft.core.Cursor3D(0,64,0,2,66,2);cursor.advance();var position=new BlockPos.MutableBlockPos();
            var skip=Class.forName("net.minecraft.world.level.chunk.NativeBlockCollisionScan").getMethod("next",BlockGetter.class,
                net.minecraft.core.Cursor3D.class,AABB.class,BlockPos.MutableBlockPos.class);
            long result=(long)skip.invoke(null,f.chunks[1][1],cursor,new AABB(0.5,64.5,0.5,1.5,65.5,1.5),position);
            int count=(int)(result>>>32);
            if(count!=27||!position.equals(new BlockPos(1,66,2)))throw new AssertionError("Native prefix admission: "+count+" "+position);
            System.out.println("NATIVE_PREFIX 27");
        }
        if(args.length>2&&args[2].equals("benchmark")){
            var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId();
            var compiler=java.lang.management.ManagementFactory.getCompilationMXBean();
            var samples=new long[8][4];
            for(int pass=-40;pass<8;pass++){
                long compiled=compiler.getTotalCompilationTime();
                long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
                long checksum=queryLoop(f.level,probes);
                long nanos=System.nanoTime()-start;bytes=bean.getThreadAllocatedBytes(thread)-bytes;
                long compilationMillis=compiler.getTotalCompilationTime()-compiled;
                if(pass>=0){samples[pass][0]=nanos;samples[pass][1]=bytes;samples[pass][2]=checksum;samples[pass][3]=compilationMillis;}
            }
            for(var sample:samples){
                System.out.println("COLLISION_TIMING "+sample[0]+" "+sample[1]+" "+sample[2]);
                System.out.println("COLLISION_COMPILED "+sample[3]);
            }
        }else{
            try(var out=current?null:new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(Path.of(args[1]))));
                var in=current?new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[1])))):null){
                int cases=0;
                for(var context:List.of(CollisionContext.empty(),CollisionContext.emptyWithFluidCollisions()))for(boolean suffocating:new boolean[]{false,true})for(var box:probes){
                    byte[] got=observe(f.level,box,context,suffocating);
                    compare(got,in,out,cases++);
                }
                System.out.println("COLLISION_CASES "+cases);
                for(int x=-3;x<=4;x++)for(int y=-3;y<=4;y++)for(int z=-3;z<=4;z++)cell(f,x,y,z,Blocks.AIR.defaultBlockState());
                var staticBoxes=staticProbes();int staticCases=0;
                for(var block:List.of(Blocks.AIR,Blocks.STONE,Blocks.OAK_SLAB,Blocks.OAK_STAIRS,Blocks.COBBLESTONE_WALL,Blocks.SNOW))
                    for(var state:block.getStateDefinition().getPossibleStates()){
                        cell(f,0,0,0,state);
                        for(var box:staticBoxes){
                            try { compare(observe(f.level,box,CollisionContext.empty(),false),in,out,cases+staticCases); }
                            catch(AssertionError failure){throw new AssertionError(state+" "+box+": "+failure.getMessage());}
                            staticCases++;
                        }
                    }
                if(current&&in.read()!=-1)throw new AssertionError("Trailing data");
                System.out.println("STATIC_COLLISION_CASES "+staticCases);
            }
        }
        if(current){var path=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(!Files.readString(Path.of("/proc/self/maps")).contains(path.toString()))throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))+" "+path);}
    }
}

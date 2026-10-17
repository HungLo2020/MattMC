package net.minecraft.client;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Actual ServerLevel.tickChunk on both sides; only a disconnected world shell
 * is synthesized. Nether wart invokes its real randomTick without placing blocks.
 * Its supplied RandomSource observes ordering and can mutate future inputs. */
public final class VerifyRandomTicks {
    static boolean current;
    static boolean replay;
    static long mix(long a, long b) { return (a ^ b) * 1099511628211L; }
    static final class TickRandom extends LegacyRandomSource {
        Fixture fixture;
        int action, calls, callbacks;
        long trace;
        TickRandom() { super(1); }
        @Override public int nextInt(int bound) {
            calls++; trace=mix(trace,bound);
            if(bound==10) {
                callbacks++;
                if(action!=0 && callbacks%7==0) {
                    var p=fixture.level.getBlockRandomPos(Integer.MAX_VALUE,-91,Integer.MIN_VALUE,31);
                    trace=mix(mix(mix(trace,p.getX()),p.getY()),p.getZ());
                }
                if(callbacks==2) {
                    if(action==1) fixture.chunk.getSections()[1]=section(0);
                    if(action==2) {
                        // Retire/replace the current primary palette through its public codec.
                        fixture.sections[0].getStates().getAndSet(0,0,0,Blocks.STONE.defaultBlockState());
                        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                            fixture.sections[0].setBlockState(x,y,z,Blocks.STONE.defaultBlockState());
                    }
                    if(action==3) fixture.chunk.getSections()[0]=section(0); // Captured section stays active until its loop ends.
                }
                if(action==4 && callbacks==5) throw new IllegalStateException("fixture tick failure");
            } else if(bound!=48) throw new AssertionError("Unexpected random bound "+bound);
            return bound-1;
        }
    }
    static LevelChunkSection section(int density) {
        var states=new PalettedContainer<>(Blocks.STONE.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        var section=new LevelChunkSection(states,null);
        for(int i=0;i<4096;i++)if(i%densitySafe(density)==0 && density!=0)
            section.setBlockState(i&15,i>>8,i>>4&15,Blocks.NETHER_WART.defaultBlockState());
        return section;
    }
    static int densitySafe(int density){return density==0?4097:density;}
    static final class Fixture {
        final ServerLevel level;
        final LevelChunk chunk;
        final LevelChunkSection[] sections;
        final TickRandom random=new TickRandom();
        Fixture(int seed,int density,int action,int count) throws Exception {
            level=VerifyCameraEnvironment.allocate(ServerLevel.class);
            if(current) {
                var type=Class.forName("net.minecraft.world.level.chunk.NativeRandomTicks");
                VerifyCameraEnvironment.set(Level.class,level,"randomTicks",type.getConstructor(int.class).newInstance(seed));
            } else VerifyCameraEnvironment.set(Level.class,level,"randValue",seed);
            random.fixture=this;random.action=action;
            VerifyCameraEnvironment.set(Level.class,level,"random",random);
            chunk=VerifyCameraEnvironment.allocate(LevelChunk.class);
            VerifyCameraEnvironment.set(ChunkAccess.class,chunk,"chunkPos",new ChunkPos(-17,29));
            var dimension = new DimensionType(OptionalLong.empty(), true, false, false, true, 1., true, false,
                -64, count*16, count*16, null, null, 0, Optional.empty(), null);
            VerifyCameraEnvironment.set(Level.class,level,"dimensionTypeRegistration",Holder.direct(dimension));
            VerifyCameraEnvironment.set(ChunkAccess.class,chunk,"levelHeightAccessor",level);
            VerifyCameraEnvironment.set(LevelChunk.class,chunk,"level",level);
            sections=new LevelChunkSection[count];for(int i=0;i<count;i++)sections[i]=section(density);
            VerifyCameraEnvironment.set(ChunkAccess.class,chunk,"sections",sections);
        }
        long run(int speed) {
            long value=0;
            try {level.tickChunk(chunk,speed);}catch(IllegalStateException e){if(!e.getMessage().equals("fixture tick failure"))throw e;value=1;}
            return mix(mix(mix(value,random.trace),random.calls),random.callbacks);
        }
        long finish(long value) {
            for(int mask:new int[]{0,15,31,-1,Integer.MIN_VALUE,Integer.MAX_VALUE}) {
                BlockPos p=level.getBlockRandomPos(Integer.MIN_VALUE,91,Integer.MAX_VALUE,mask);
                value=mix(mix(mix(value,p.getX()),p.getY()),p.getZ());
            }
            return value;
        }
    }
    static volatile long blackhole;
    public static void main(String[] args) throws Exception {
        current=args[0].equals("current");replay=!args[0].equals("frozen");SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String name:List.of("net.minecraft.server.level.ServerLevel","net.minecraft.world.level.Level","net.minecraft.world.level.chunk.ChunkAccess",
            "net.minecraft.world.level.chunk.LevelChunk","net.minecraft.world.level.chunk.LevelChunkSection","net.minecraft.world.level.chunk.PalettedContainer",
            "net.minecraft.world.level.block.NetherWartBlock","net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")) VerifyCameraEnvironment.image(name);
        if(current)VerifyCameraEnvironment.image("net.minecraft.world.level.chunk.NativeRandomTicks");
        if(args.length>2) {
            var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId();
            var compiler=ManagementFactory.getCompilationMXBean();
            for(int density:new int[]{4096,16,1}) {
                var f=new Fixture(71,density,0,24);var samples=new long[8][4];
                for(int round=-96;round<8;round++) {
                    long compiled=compiler.getTotalCompilationTime(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime(),sum=0;
                    for(int i=0;i<4096;i++)sum=mix(sum,f.run(3));
                    long elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-bytes;
                    long compilation=compiler.getTotalCompilationTime()-compiled;blackhole=sum;
                    if(round>=0)samples[round]=new long[]{elapsed,allocated,sum,compilation};
                }
                for(long[] row:samples)System.out.println("TICK_TIMING "+density+" "+row[0]+" "+row[1]+" "+row[2]+" "+row[3]);
            }
        } else {
            try(var out=replay?null:new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(Path.of(args[1]))));
                var in=replay?new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[1])))):null) {
                int cases=0;
                for(int seed:new int[]{0,71,-1,Integer.MIN_VALUE,Integer.MAX_VALUE})for(int density:new int[]{0,4096,16,1})
                    for(int action=0;action<5;action++)for(int speed:new int[]{-1,0,1,3,31,4097}) {
                        var f=new Fixture(seed,density,action,3);long value=f.finish(f.run(speed));
                        if(replay){long expected=in.readLong();if(value!=expected)throw new AssertionError("Case "+cases+" seed="+seed+" density="+density+" action="+action+" speed="+speed+" "+value+" != "+expected);}
                        else out.writeLong(value);cases++;
                    }
                if(replay&&in.read()!=-1)throw new AssertionError("Trailing oracle data");
                System.out.println("TICK_CASES "+cases);
            }
        }
        if(replay) {
            var path=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(!Files.readString(Path.of("/proc/self/maps")).contains(path.toString()))throw new AssertionError("Native image not loaded");
            System.out.println("NATIVE_IMAGE "+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))+" "+path);
        }
    }
}

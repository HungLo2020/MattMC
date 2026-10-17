package net.minecraft.world.level.lighting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Prepared Frozen-first observation only; not part of the r3 release candidate. */
public final class VerifySkySourcePublicUpdateContracts {
    static final IOException MARKER = new IOException("original source lookup failure");
    @SuppressWarnings("unchecked") static <T extends Throwable> void fail() throws T { throw (T)MARKER; }
    static final class Reader implements BlockGetter {
        final ChunkSkyLightSources source;
        final String mode;
        final List<String> trace=new ArrayList<>();
        final List<BlockPos> retained=new ArrayList<>();
        final IdentityHashMap<BlockPos,Integer> ids=new IdentityHashMap<>();
        int calls;
        Reader(ChunkSkyLightSources source,String mode){this.source=source;this.mode=mode;}
        public int getMinY(){return 0;}
        public int getHeight(){return 16;}
        public BlockEntity getBlockEntity(BlockPos pos){throw new AssertionError("unexpected block entity");}
        public FluidState getFluidState(BlockPos pos){throw new AssertionError("unexpected fluid");}
        public BlockState getBlockState(BlockPos pos){
            int call=++calls;
            int id=ids.computeIfAbsent(pos,p->ids.size());retained.add(pos);
            trace.add("get:"+call+":"+id+":"+pos.getX()+","+pos.getY()+","+pos.getZ());
            if(mode.equals("mutate-first")&&call==1)((BlockPos.MutableBlockPos)pos).set(9,12,7);
            if(mode.equals("mutate-second")&&call==2)((BlockPos.MutableBlockPos)retained.get(0)).setY(11);
            if((mode.equals("reenter-first")||mode.equals("reenter-fail"))&&call==1)reenter();
            if(mode.equals("reenter-second")&&call==2)reenter();
            if(mode.equals("throw-first")&&call==1||mode.equals("throw-second")&&call==2||mode.equals("throw-third")&&call==3||mode.equals("throw-scan")&&call==5||mode.equals("reenter-fail")&&call==1){trace.add("throw:"+call);VerifySkySourcePublicUpdateContracts.<RuntimeException>fail();}
            if(mode.equals("occlude")||mode.startsWith("mutate-")||mode.startsWith("reenter-"))return call==2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
            return mode.equals("scan-floor")&&pos.getY()==2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
        }
        void reenter(){
            trace.add("nested:begin");
            Reader nested=new Reader(source,"occlude");boolean changed=source.update(nested,10,12,10);
            trace.add("nested:"+changed+":"+nested.trace);trace.add("nested:end");
        }
    }
    static String positions(List<BlockPos> positions){
        List<String> out=new ArrayList<>();for(BlockPos p:positions)out.add(p.getX()+","+p.getY()+","+p.getZ());return out.toString();
    }
    static void run(String mode) throws Exception {
        ChunkSkyLightSources source=new ChunkSkyLightSources(LevelHeightAccessor.create(0,16));
        // Establish every column through public effects, with no private-storage assumption.
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            Reader initial=new Reader(source,"occlude");
            if(!source.update(initial,x,4,z)||source.getLowestSourceY(x,z)!=5)throw new AssertionError("Public source setup failed");
        }
        Reader reader=new Reader(source,mode);Throwable thrown=null;boolean changed=false;
        int y=mode.equals("below")?3:mode.equals("occlude")||mode.startsWith("mutate-")||mode.startsWith("reenter-")?7:mode.equals("throw-third")?5:4;
        try{changed=source.update(reader,2,y,4);}catch(Throwable failure){thrown=failure;}
        List<Integer> values=new ArrayList<>();for(int z=0;z<16;z++)for(int x=0;x<16;x++)values.add(source.getLowestSourceY(x,z));
        System.out.println("SKY_UPDATE "+mode+" changed="+changed+" original="+(thrown==MARKER)+" type="+(thrown==null?"none":thrown.getClass().getName())+" max="+source.getHighestLowestSourceY()+" values="+values+" calls="+reader.trace+" aliases="+positions(reader.retained));
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String mode:List.of("below","occlude","scan-floor","scan-empty","mutate-first","mutate-second","reenter-first","reenter-second","throw-first","throw-second","throw-third","throw-scan","reenter-fail"))run(mode);
    }
}

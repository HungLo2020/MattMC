package net.minecraft.world.level.levelgen.carver;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Real sections/palettes with a strict ordered carving trace. */
final class CarverTestChunk extends ProtoChunk {
    final LongArrayList trace=new LongArrayList();
    final Set<BlockPos> writes=new HashSet<>();
    boolean recording=true,upgrading;
    Runnable onWrite;
    boolean fail;
    CarverTestChunk(ChunkPos pos,int minY,int height,boolean mixed) {
        super(pos,UpgradeData.EMPTY,sections(height,mixed),new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(minY,height),null,null);
    }
    private static LevelChunkSection[] sections(int height,boolean mixed) {
        var sections=new LevelChunkSection[height/16];
        for(int s=0;s<sections.length;s++) {
            var states=new PalettedContainer<BlockState>(Blocks.STONE.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
            if(mixed)for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<16;y++)
                if((x*7+y*3+z*11+s)%17==0)states.set(x,y,z,Blocks.BEDROCK.defaultBlockState());
            sections[s]=new LevelChunkSection(states,null);
        }return sections;
    }
    void event(int type,BlockPos p){if(recording){trace.add(type);trace.add(p.getX());trace.add(p.getY());trace.add(p.getZ());}}
    @Override public boolean isUpgrading(){return upgrading;}
    @Override public BlockState getBlockState(BlockPos p){var s=super.getBlockState(p);event(1,p);if(recording)trace.add(Block.getId(s));return s;}
    @Override public BlockState setBlockState(BlockPos p,BlockState s,int flags){
        event(2,p);if(recording){trace.add(Block.getId(s));trace.add(flags);}
        if(onWrite!=null){var callback=onWrite;onWrite=null;callback.run();}if(fail)throw new IllegalStateException("write failure");
        writes.add(p.immutable());return super.setBlockState(p,s,flags);
    }
    @Override public void markPosForPostprocessing(BlockPos p){event(3,p);super.markPosForPostprocessing(p);}
    Map<BlockPos,Integer> blocks(){var result=new HashMap<BlockPos,Integer>();for(var p:writes)result.put(p,Block.getId(super.getBlockState(p)));return result;}
    Aquifer aquifer(){return new Aquifer(){
        @Override public BlockState computeSubstance(DensityFunction.FunctionContext p,double density){
            var pos=new BlockPos(p.blockX(),p.blockY(),p.blockZ());event(4,pos);if(recording)trace.add(Double.doubleToRawLongBits(density));
            return switch(Math.floorMod(p.blockX()+p.blockY()+p.blockZ(),5)){case 0->null;case 1->Blocks.WATER.defaultBlockState();case 2->Blocks.LAVA.defaultBlockState();default->Blocks.AIR.defaultBlockState();};
        }
        @Override public boolean shouldScheduleFluidUpdate(){if(recording)trace.add(5);return true;}
    };}
}

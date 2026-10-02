package net.minecraft.world.level.levelgen.feature;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;

/** Strict placement adapter with real chunk palettes, traced reads/writes/ticks. */
final class GeodeTestWorld {
    final OreTestWorld storage;
    final LongArrayList trace = new LongArrayList();
    final Set<BlockPos> written = new HashSet<>();
    final WorldGenLevel level;
    boolean deny, failWrite;
    int terrain;
    Runnable onWrite;
    GeodeTestWorld(long seed, int terrain) {
        this.terrain=terrain;
        storage=new OreTestWorld(-64,384,false,false);
        level=new UnsupportedOreWorld() {
            @Override public long getSeed() { trace.add(0); trace.add(seed); return seed; }
            @Override public BlockState getBlockState(BlockPos p) {
                var state=read(p);event(1,p);trace.add(Block.getId(state));return state;
            }
            @Override public boolean setBlock(BlockPos p, BlockState state, int flags) {return setBlock(p,state,flags,512);}
            @Override public boolean setBlock(BlockPos p, BlockState state, int flags, int depth) {
                event(2,p);trace.add(Block.getId(state));trace.add(flags);trace.add(depth);
                if(onWrite!=null){var f=onWrite;onWrite=null;f.run();}
                if(failWrite)throw new IllegalStateException("write failure");
                if(deny && (p.getX()&1)!=0)return false;
                storage.chunk(p.getX()>>4,p.getZ()>>4).setBlockState(p,state,flags);written.add(p.immutable());return true;
            }
            @Override public FluidState getFluidState(BlockPos p) {
                event(3,p);return read(p).getFluidState();
            }
            @Override public void scheduleTick(BlockPos p, Fluid fluid, int delay) {
                event(4,p);trace.add(fluid==Fluids.WATER?1:fluid==Fluids.LAVA?2:3);trace.add(delay);
            }
        };
    }
    private void event(long type,BlockPos p){trace.add(type);trace.add(p.getX());trace.add(p.getY());trace.add(p.getZ());}
    private BlockState read(BlockPos p) {
        if(written.contains(p))return storage.chunk(p.getX()>>4,p.getZ()>>4).getBlockState(p);
        if(terrain==1 && Math.floorMod(p.getX()*17+p.getY()*7+p.getZ()*3,13)==0)return Blocks.AIR.defaultBlockState();
        if(terrain==2)return Blocks.WATER.defaultBlockState();
        if(terrain==3)return Blocks.AIR.defaultBlockState();
        if(terrain==4 && (p.getX()&3)==0)return Blocks.BEDROCK.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }
    Map<BlockPos,Integer> blocks(){var out=new HashMap<BlockPos,Integer>();for(var p:written)out.put(p,Block.getId(read(p)));return out;}
}

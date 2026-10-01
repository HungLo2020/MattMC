package net.minecraft.world.level.levelgen.feature;

import java.util.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** Isolated real chunk sections. The surrounding WorldGenLevel is a test adapter;
 * unexpected world calls fail. No renderer, server, disk or mocked block storage. */
final class OreTestWorld {
    final Map<Long, ProtoChunk> chunks = new HashMap<>();
    final LongArrayList trace = new LongArrayList();
    final List<Runnable> undo = new ArrayList<>();
    final WorldGenLevel level;
    final int minY, height;
    final boolean mixed;
    boolean recording;
    boolean denyWrites;
    boolean noHeight;
    Runnable onWriteCheck;
    long checks;
    OreTestWorld(int minY, int height, boolean mixed, boolean recording) {
        this.minY=minY; this.height=height; this.mixed=mixed; this.recording=recording;
        level=new UnsupportedOreWorld() {
            @Override public int getMinY() { return minY; }
            @Override public int getHeight() { return height; }
            @Override public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types type,int x,int z) { return noHeight?Integer.MIN_VALUE:minY+height; }
            @Override public boolean isOutsideBuildHeight(int y) {event(1,y); checks++; return y<minY || y>=minY+height;}
            @Override public boolean ensureCanWrite(BlockPos pos) {
                event(2,pos.asLong());
                if(onWriteCheck!=null){var callback=onWriteCheck;onWriteCheck=null;callback.run();}
                return !denyWrites || (pos.getX()&1)==0;
            }
            @Override public ChunkAccess getChunk(int x,int z) {event(3,ChunkPos.asLong(x,z));return chunk(x,z);}
            @Override public ChunkAccess getChunk(int x,int z,net.minecraft.world.level.chunk.status.ChunkStatus status,boolean create) {return getChunk(x,z);}
        };
    }

    private void event(long type,long value) {if(recording){trace.add(type);trace.add(value);}}
    ProtoChunk chunk(int cx,int cz) {
        return chunks.computeIfAbsent(ChunkPos.asLong(cx,cz),key->{
            var sections=new LevelChunkSection[height/16];
            for(int sy=0;sy<sections.length;sy++) {
                final int baseY=minY+sy*16;
                var states=new PalettedContainer<BlockState>(Blocks.STONE.defaultBlockState(),Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
                if(mixed)for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                    if(Math.floorMod((cx*16+x)*17+(baseY+y)*31+(cz*16+z)*7,19)==0)states.set(x,y,z,Blocks.AIR.defaultBlockState());
                sections[sy]=new LevelChunkSection(states,null) {
                    @Override public BlockState getBlockState(int x,int y,int z) {
                        var result=super.getBlockState(x,y,z);event(4,BlockPos.asLong(cx*16+x,baseY+y,cz*16+z));event(5,Block.getId(result));return result;
                    }
                    @Override public BlockState setBlockState(int x,int y,int z,BlockState state,boolean lock) {
                        event(6,BlockPos.asLong(cx*16+x,baseY+y,cz*16+z));event(7,Block.getId(state));
                        var previous=super.setBlockState(x,y,z,state,lock);
                        if(recording)undo.add(()->super.setBlockState(x,y,z,previous,false));
                        return previous;
                    }
                    @Override public void acquire(){event(8,BlockPos.asLong(cx,baseY,cz));super.acquire();}
                    @Override public void release(){event(9,BlockPos.asLong(cx,baseY,cz));super.release();}
                };
            }
            return new ProtoChunk(new ChunkPos(cx,cz),UpgradeData.EMPTY,sections,new ProtoChunkTicks<>(),new ProtoChunkTicks<>(),LevelHeightAccessor.create(minY,height),null,null);
        });
    }
    Map<Long,Integer> writtenBlocks() {
        var positions=new HashSet<Long>();
        for(int i=0;i<trace.size();i+=2)if(trace.getLong(i)==6)positions.add(trace.getLong(i+1));
        var result=new HashMap<Long,Integer>();
        boolean previous=recording;recording=false;
        try {for(long position:positions){var pos=BlockPos.of(position);result.put(position,Block.getId(chunk(pos.getX()>>4,pos.getZ()>>4).getBlockState(pos)));}}
        finally {recording=previous;}
        return result;
    }
    void reset() {for(int i=undo.size()-1;i>=0;i--)undo.get(i).run();undo.clear();trace.clear();checks=0;}
}

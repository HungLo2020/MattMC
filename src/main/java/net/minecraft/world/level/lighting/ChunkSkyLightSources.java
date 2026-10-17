package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

public class ChunkSkyLightSources {
	public static final int NEGATIVE_INFINITY = Integer.MIN_VALUE;
	private final NativeSkySourceOwner nativeOwner;
	private final BlockPos.MutableBlockPos mutablePos1 = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos mutablePos2 = new BlockPos.MutableBlockPos();

	public ChunkSkyLightSources(LevelHeightAccessor accessor) {
        int low=accessor.getMinY();int high=accessor.getMaxY();
        this.nativeOwner=new NativeSkySourceOwner(low,high);
    }

	/** Legacy live packed-array view; outside writes are imported before source operations. */
	public long[] nativeRaw() {
		return this.nativeOwner.raw();
	}

    final long nativeOwnerForLighting() { this.nativeOwner.importEscaped(); return this.nativeOwner.id; }

	public int nativeBits() {
		return this.nativeOwner.bits();
	}

    // Additive producer transport agreed in S33/0211 and S31/r4.
    // Every subclass uses actual virtual legacy dispatch; no extra nativeBits call.
    public int nativeWordCount() {
        if (this.getClass()!=ChunkSkyLightSources.class) return this.nativeRaw().length;
        return this.nativeOwner.wordCount();
    }
    public void nativeCopyTo(java.lang.foreign.MemorySegment destination,long byteOffset) {
        if (this.getClass()!=ChunkSkyLightSources.class) {
            long[] raw=this.nativeRaw();
            java.lang.foreign.MemorySegment.copy(raw,0,destination,java.lang.foreign.ValueLayout.JAVA_LONG,byteOffset,raw.length);
        } else this.nativeOwner.copyTo(destination,byteOffset);
    }
    public void nativeCopyFrom(java.lang.foreign.MemorySegment source,long byteOffset) {
        if (this.getClass()!=ChunkSkyLightSources.class) {
            long[] raw=this.nativeRaw();
            java.lang.foreign.MemorySegment.copy(source,java.lang.foreign.ValueLayout.JAVA_LONG,byteOffset,raw,0,raw.length);
        } else this.nativeOwner.copyFrom(source,byteOffset);
    }
    public long[] nativeSnapshot() {
        if (this.getClass()!=ChunkSkyLightSources.class) return this.nativeRaw().clone();
        return this.nativeOwner.snapshot();
    }
    public void nativeRestore(long[] snapshot) {
        if (this.getClass()!=ChunkSkyLightSources.class) {
            long[] raw=this.nativeRaw();System.arraycopy(snapshot,0,raw,0,snapshot.length);
        } else this.nativeOwner.restore(snapshot);
    }
    public boolean nativeMatches(long[] snapshot) {
        if (this.getClass()!=ChunkSkyLightSources.class) {
            long[] raw=this.nativeRaw();return java.util.Arrays.equals(snapshot,raw);
        }
        return this.nativeOwner.matches(snapshot);
    }

	public void fillFrom(ChunkAccess chunkAccess) {
        this.nativeOwner.fillFrom(chunkAccess,this.mutablePos1,this.mutablePos2);
    }

	public boolean update(BlockGetter getter, int x, int y, int z) {
        return this.nativeOwner.update(getter,x,y,z,this.mutablePos1,this.mutablePos2);
    }

	public int getLowestSourceY(int x,int z) { return this.nativeOwner.get(x,z); }
    public int getHighestLowestSourceY() { return this.nativeOwner.highest(); }

}

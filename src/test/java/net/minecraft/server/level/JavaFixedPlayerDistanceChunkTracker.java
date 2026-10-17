package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.level.lighting.JavaChunkTracker;

/** Original DistanceManager.FixedPlayerDistanceChunkTracker at the pinned
 * reference, relocated onto the pinned original ChunkTracker/graph/queue. Only
 * the player-presence map is supplied instead of the enclosing instance's. */
public class JavaFixedPlayerDistanceChunkTracker extends JavaChunkTracker {
	private final Long2ObjectMap<ObjectSet<Object>> playersPerChunk;
	protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
	protected final int maxDistance;

	protected JavaFixedPlayerDistanceChunkTracker(final int i, Long2ObjectMap<ObjectSet<Object>> playersPerChunk) {
		super(i + 2, 16, 256);
		this.playersPerChunk = playersPerChunk;
		this.maxDistance = i;
		this.chunks.defaultReturnValue((byte)(i + 2));
	}

	@Override
	protected int getLevel(long l) {
		return this.chunks.get(l);
	}

	@Override
	protected void setLevel(long l, int i) {
		byte b;
		if (i > this.maxDistance) {
			b = this.chunks.remove(l);
		} else {
			b = this.chunks.put(l, (byte)i);
		}

		this.onLevelChange(l, b, i);
	}

	protected void onLevelChange(long l, int i, int j) {
	}

	@Override
	protected int getLevelFromSource(long l) {
		return this.havePlayer(l) ? 0 : Integer.MAX_VALUE;
	}

	private boolean havePlayer(long l) {
		ObjectSet<Object> objectSet = this.playersPerChunk.get(l);
		return objectSet != null && !objectSet.isEmpty();
	}

	public void runAllUpdates() {
		this.runUpdates(Integer.MAX_VALUE);
	}
}

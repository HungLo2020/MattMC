package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** Published level view of the native-owned simulation distance graph. Rust
 * owns propagation and mirrors each chunk's lowest simulating ticket level;
 * each run replays the original ordered {@code setLevel} calls into this map. */
public class SimulationChunkTracker {
	public static final int MAX_LEVEL = 33;
	protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
	private final TicketStorage ticketStorage;
	private final SimulationChunkDistance distance = new SimulationChunkDistance(ChunkLevel.MAX_LEVEL + 1);
	private final PlayerChunkDistances.LevelSink levelSink = this::setLevel;

	public SimulationChunkTracker(TicketStorage ticketStorage) {
		this.ticketStorage = ticketStorage;
		// The original graph read ticket levels lazily, including tickets added
		// before this listener; seed those before any notification arrives.
		for (LongIterator iterator = ticketStorage.activeTicketChunks(); iterator.hasNext(); ) {
			long l = iterator.nextLong();
			this.distance.seed(l, ticketStorage.getTicketLevelAt(l, true));
		}
		ticketStorage.setSimulationChunkUpdatedListener(this::update);
		this.chunks.defaultReturnValue((byte)33);
	}

	public void update(long l, int i, boolean bl) {
		this.distance.update(l, this.ticketStorage.getTicketLevelAt(l, true), i, bl);
	}

	public int getLevel(ChunkPos chunkPos) {
		return this.getLevel(chunkPos.toLong());
	}

	protected int getLevel(long l) {
		return this.chunks.get(l);
	}

	protected void setLevel(long l, int i) {
		if (i >= 33) {
			this.chunks.remove(l);
		} else {
			this.chunks.put(l, (byte)i);
		}
	}

	public void runAllUpdates() {
		this.distance.runAllUpdates(this.levelSink);
	}
}

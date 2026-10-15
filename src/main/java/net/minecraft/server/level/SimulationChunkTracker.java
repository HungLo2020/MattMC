package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** Java side of the native-owned simulation distance graph. Rust owns
 * propagation, a mirror of each chunk's lowest simulating ticket level and the
 * published level view (the original {@code Long2ByteOpenHashMap chunks}, in its
 * exact iteration order): each run applies the original ordered {@code setLevel}
 * calls to the view natively. */
public class SimulationChunkTracker {
	public static final int MAX_LEVEL = 33;
	/** The original map: levels below 33 stored, default 33. */
	private final NativeLevelView chunks = new NativeLevelView(33, 33);
	private final TicketStorage ticketStorage;
	private final TicketChunkDistance distance = new TicketChunkDistance(TicketChunkDistance.SIMULATION, ChunkLevel.MAX_LEVEL + 1);

	public SimulationChunkTracker(TicketStorage ticketStorage) {
		this.ticketStorage = ticketStorage;
		// The original graph read ticket levels lazily, including tickets added
		// before this listener; seed those before any notification arrives.
		for (LongIterator iterator = ticketStorage.activeTicketChunks(); iterator.hasNext(); ) {
			long l = iterator.nextLong();
			this.distance.seed(l, ticketStorage.getTicketLevelAt(l, true));
		}
		// The storage notifies the native graph directly; update() is the same call from Java.
		ticketStorage.setSimulationChunkUpdatedListener(this.distance.listener(ticketStorage, this::update));
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

	public void runAllUpdates() {
		this.distance.run(Integer.MAX_VALUE);
		this.chunks.takeTicket(this.distance);
	}

	/** The original walk over {@code Long2ByteMaps.fastIterable(chunks)}, keeping entity-ticking
	 * levels: live, so the consumer may change the view exactly as it could change the map. */
	void forEachEntityTicking(LongConsumer consumer) {
		this.chunks.forEachAtMost(ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING), consumer);
	}

	/** The published view in iteration order, for verification. */
	@VisibleForTesting
	public Long2ByteLinkedOpenHashMap publishedView() {
		Long2ByteLinkedOpenHashMap view = new Long2ByteLinkedOpenHashMap();
		this.chunks.forEachAtMost(Integer.MAX_VALUE, l -> view.put(l, (byte)this.getLevel(l)));
		return view;
	}
}

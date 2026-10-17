package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.longs.Long2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** Native-owned simulation propagation with live source queries and ordered
 * updates to the published native level view. Java effects run without holding
 * the graph lock, and may reenter this tracker. */
public class SimulationChunkTracker {
	public static final int MAX_LEVEL = 33;
	/** The original map: levels below 33 stored, default 33. */
	private final NativeLevelView chunks = new NativeLevelView(33, 33);
	private final TicketStorage ticketStorage;
	private final TicketChunkDistance distance = new TicketChunkDistance(TicketChunkDistance.SIMULATION, ChunkLevel.MAX_LEVEL + 1);

    private final TicketChunkDistance.Effects effects = new TicketChunkDistance.Effects() {
        public int level(long position) { return SimulationChunkTracker.this.getLevel(position); }
        public int source(long position) { return SimulationChunkTracker.this.getLevelFromSource(position); }
        public void set(long position, int level) { SimulationChunkTracker.this.setLevel(position, level); }
    };
    protected int getLevelFromSource(long position) { return this.ticketStorage.getTicketLevelAt(position, true); }
    protected void setLevel(long position, int level) { this.chunks.set(position, level); }

	public SimulationChunkTracker(TicketStorage ticketStorage) {
		this.ticketStorage = ticketStorage;
        ticketStorage.setSimulationChunkUpdatedListener(this::update);
	}

	public void update(long l, int i, boolean bl) {
		this.distance.updateEffects(l, i, bl, this.effects);
	}

	public int getLevel(ChunkPos chunkPos) {
		return this.getLevel(chunkPos.toLong());
	}

	protected int getLevel(long l) {
		return this.chunks.get(l);
	}

	public void runAllUpdates() {
        this.distance.runEffects(Integer.MAX_VALUE, this.effects);
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

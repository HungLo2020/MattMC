package net.minecraft.world.level;

import com.mojang.datafixers.util.Pair;
import net.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class TicketStorage extends SavedData {
	private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Codec<Pair<ChunkPos, Ticket>> TICKET_ENTRY = Codec.mapPair(ChunkPos.CODEC.fieldOf("chunk_pos"), Ticket.CODEC).codec();
	public static final Codec<TicketStorage> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(TICKET_ENTRY.listOf().optionalFieldOf("tickets", List.of()).forGetter(TicketStorage::packTickets))
			.apply(instance, TicketStorage::fromPacked)
	);
	public static final SavedDataType<TicketStorage> TYPE = new SavedDataType<>("chunks", TicketStorage::new, CODEC, DataFixTypes.SAVED_DATA_FORCED_CHUNKS);
	/** {@code ChunkLevel.MAX_LEVEL + 1}: the level of a chunk without a ticket of a kind. */
	static final int ABSENT_LEVEL = ChunkLevel.MAX_LEVEL + 1;
	/** Rust owns the ticket lists, their table order, expiry and deactivation. */
	private final NativeTicketStorage tickets;
	private LongSet chunksWithForcedTickets = new LongOpenHashSet();
	@Nullable
	private TicketStorage.ChunkUpdated loadingChunkUpdatedListener;
	@Nullable
	private TicketStorage.ChunkUpdated simulationChunkUpdatedListener;
	private final int[] count = new int[1];
	/** Listener slots bound to a native tracker graph. */
	private final boolean[] nativeListener = new boolean[2];

	private TicketStorage(NativeTicketStorage nativeTicketStorage) {
		this.tickets = nativeTicketStorage;
		this.updateForcedChunks();
	}

	public TicketStorage() {
		this(new NativeTicketStorage());
	}

	private static TicketStorage fromPacked(List<Pair<ChunkPos, Ticket>> list) {
		NativeTicketStorage nativeTicketStorage = new NativeTicketStorage();

		for (Pair<ChunkPos, Ticket> pair : list) {
			nativeTicketStorage.addDeactivated(pair.getFirst().toLong(), pair.getSecond());
		}

		return new TicketStorage(nativeTicketStorage);
	}

	private List<Pair<ChunkPos, Ticket>> packTickets() {
		long[] pairs = this.tickets.export(3, 0, this.count);
		List<Pair<ChunkPos, Ticket>> list = new ArrayList(this.count[0] / 2);
		ChunkPos chunkPos = null;

		for (int i = 0; i < this.count[0]; i += 2) {
			if (chunkPos == null || chunkPos.toLong() != pairs[i]) {
				chunkPos = new ChunkPos(pairs[i]);
			}

			list.add(new Pair<>(chunkPos, NativeTicketStorage.Cells.ticket((int)pairs[i + 1])));
		}

		return list;
	}

	public void activateAllDeactivatedTickets() {
		// The original loops over the live deactivated map and lists; Rust iterates
		// them positionally and returns each ticket for addTicket.
		this.walk(NativeTicketStorage.ACTIVATE, null, null, null);
		this.tickets.clearDeactivated();
	}

	public void setLoadingChunkUpdatedListener(@Nullable TicketStorage.ChunkUpdated chunkUpdated) {
		this.loadingChunkUpdatedListener = chunkUpdated;
		this.bindListener(NativeTicketStorage.LOADING, chunkUpdated);
	}

	public void setSimulationChunkUpdatedListener(@Nullable TicketStorage.ChunkUpdated chunkUpdated) {
		this.simulationChunkUpdatedListener = chunkUpdated;
		this.bindListener(NativeTicketStorage.SIMULATION, chunkUpdated);
	}

	/** A production tracker of this storage is notified natively; any other listener by Java. */
	private void bindListener(int which, @Nullable TicketStorage.ChunkUpdated chunkUpdated) {
		if (chunkUpdated instanceof TicketStorage.NativeChunkUpdated nativeChunkUpdated && nativeChunkUpdated.ticketStorage() == this) {
			nativeChunkUpdated.withGraph(graph -> this.tickets.listen(which, 0, graph));
			this.nativeListener[which] = true;
		} else {
			this.tickets.listen(which, chunkUpdated == null ? 0 : 1, 0L);
			this.nativeListener[which] = false;
		}
	}

	@Nullable
	private TicketStorage.ChunkUpdated listener(int which) {
		return which == NativeTicketStorage.LOADING ? this.loadingChunkUpdatedListener : this.simulationChunkUpdatedListener;
	}

	/** {@code listener.update(l, i, bl)} for a notification native delivery left to Java. */
	private void deliver(int which, long l, int i, boolean bl) {
		TicketStorage.ChunkUpdated chunkUpdated = this.listener(which);
		if (chunkUpdated != null && !(this.nativeListener[which] && this.tickets.notify(which, l, i, bl))) {
			chunkUpdated.update(l, i, bl);
		}
	}

	public boolean hasTickets() {
		return (this.tickets.state() & 1) != 0;
	}

	public boolean shouldKeepDimensionActive() {
		return (this.tickets.state() & 2) != 0;
	}

	/** Chunks holding active tickets, so a listener attached after tickets exist can seed its state. */
	public LongIterator activeTicketChunks() {
		long[] keys = this.tickets.export(0, 0, this.count);
		return LongArrayList.wrap(Arrays.copyOf(keys, this.count[0])).iterator();
	}

	public List<Ticket> getTickets(long l) {
		return this.tickets.tickets(l);
	}

	public void addTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		Ticket ticket = new Ticket(ticketType, ChunkLevel.byStatus(FullChunkStatus.FULL) - i);
		this.addTicket(chunkPos.toLong(), ticket);
	}

	public void addTicket(Ticket ticket, ChunkPos chunkPos) {
		this.addTicket(chunkPos.toLong(), ticket);
	}

	public boolean addTicket(long l, Ticket ticket) {
		long status = this.tickets.add(l, ticket);
		if ((status & NativeTicketStorage.RESULT) == 0) {
			this.setDirty();
			return false;
		}

		if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
			LOGGER.debug("ATI {} {}", new ChunkPos(l), ticket);
		}

		// Native delivery covered the notifications before the first Java listener.
		int resume = NativeTicketStorage.resume(status);
		if (resume <= 0 && (status & NativeTicketStorage.WANT_SIMULATION) != 0) {
			this.deliver(NativeTicketStorage.SIMULATION, l, ticket.getTicketLevel(), true);
		}

		if (resume <= 1 && (status & NativeTicketStorage.WANT_LOADING) != 0) {
			this.deliver(NativeTicketStorage.LOADING, l, ticket.getTicketLevel(), true);
		}

		if (ticket.getType().equals(TicketType.FORCED)) {
			this.chunksWithForcedTickets.add(l);
		}

		this.setDirty();
		return true;
	}

	public int getTicketLevelAt(long l, boolean bl) {
		return this.tickets.level(l, bl);
	}

	public void removeTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		Ticket ticket = new Ticket(ticketType, ChunkLevel.byStatus(FullChunkStatus.FULL) - i);
		this.removeTicket(chunkPos.toLong(), ticket);
	}

	public void removeTicket(Ticket ticket, ChunkPos chunkPos) {
		this.removeTicket(chunkPos.toLong(), ticket);
	}

	public boolean removeTicket(long l, Ticket ticket) {
		long status = this.tickets.remove(l, ticket);
		if ((status & NativeTicketStorage.RESULT) == 0) {
			return false;
		} else {
			long list = this.tickets.io[0];
			if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
				LOGGER.debug("RTI {} {}", new ChunkPos(l), ticket);
			}

			// Each level is read from the emptied-or-shrunk list when delivered, as before.
			int resume = NativeTicketStorage.resume(status);
			if (resume <= 0 && ticket.getType().doesSimulate() && this.simulationChunkUpdatedListener != null) {
				this.deliver(NativeTicketStorage.SIMULATION, l, this.tickets.listLevel(list, true), false);
			}

			if (resume <= 1 && ticket.getType().doesLoad() && this.loadingChunkUpdatedListener != null) {
				this.deliver(NativeTicketStorage.LOADING, l, this.tickets.listLevel(list, false), false);
			}

			if (ticket.getType().equals(TicketType.FORCED)) {
				this.updateForcedChunks();
			}

			this.setDirty();
			return true;
		}
	}

	private void updateForcedChunks() {
		long[] keys = this.tickets.export(1, 0, this.count);
		LongOpenHashSet longOpenHashSet = new LongOpenHashSet();

		for (int i = 0; i < this.count[0]; i++) {
			longOpenHashSet.add(keys[i]);
		}

		this.chunksWithForcedTickets = longOpenHashSet;
	}

	public String getTicketDebugString(long l, boolean bl) {
		int cell = this.tickets.lowest(l, bl);
		return cell < 0 ? "no_ticket" : NativeTicketStorage.Cells.ticket(cell).toString();
	}

	public void purgeStaleTickets(ChunkMap chunkMap) {
		this.walk(NativeTicketStorage.PURGE, null, null, chunkMap);
		this.setDirty();
	}

	public void deactivateTicketsOnClosing() {
		this.walk(NativeTicketStorage.DEACTIVATE, null, null, null);
	}

	public void removeTicketIf(TicketStorage.TicketPredicate ticketPredicate, @Nullable Long2ObjectOpenHashMap<List<Ticket>> long2ObjectOpenHashMap) {
		this.walk(NativeTicketStorage.JAVA_PREDICATE, ticketPredicate, long2ObjectOpenHashMap, null);
	}

	/**
	 * The original removeTicketIf walk, run natively. It returns here only to ask
	 * Java: whether a chunk is ready for saving (purge's canTicketExpire, in walk
	 * order), a Java predicate, or a Java listener's notification.
	 */
	private void walk(
		int kind,
		@Nullable TicketStorage.TicketPredicate ticketPredicate,
		@Nullable Long2ObjectOpenHashMap<List<Ticket>> long2ObjectOpenHashMap,
		@Nullable ChunkMap chunkMap
	) {
		long walk = this.tickets.walk(kind);
		long[] io = this.tickets.io;
		boolean released = false;

		try {
			int answer = 0;

			while (true) {
				long status = this.tickets.step(walk, answer);
				if ((status & NativeTicketStorage.DIRTY) != 0) {
					this.setDirty();
				}

				released |= NativeTicketStorage.released(status);
				NativeTicketStorage.checkInWalk(status);

				switch (NativeTicketStorage.event(status)) {
					case NativeTicketStorage.DONE:
						if (kind != NativeTicketStorage.ACTIVATE && (status & NativeTicketStorage.RESULT) != 0) {
							this.updateForcedChunks();
						}

						return;
					case NativeTicketStorage.READY:
						ChunkHolder chunkHolder = chunkMap.getUpdatingChunkIfPresent(io[0]);
						answer = chunkHolder == null || chunkHolder.isReadyForSaving() ? 1 : 0;
						break;
					case NativeTicketStorage.BATCH:
						// The next tickets the original would test, in order. Stop at the
						// first match, or once a predicate changed the lists.
						answer = -1;
						long[] batch = this.tickets.batch;
						int n = (int)(status >>> 32);

						for (int i = 0; i < n; i++) {
							long l = batch[i * 2];
							Ticket ticket = NativeTicketStorage.Cells.ticket((int)batch[i * 2 + 1]);
							long modifications = this.tickets.modifications;
							boolean matched = ticketPredicate.test(ticket, l);
							if (matched && long2ObjectOpenHashMap != null) {
								long walkHandle = walk;
								int index = i;
								// The original sized a new list by entry.getValue(), read positionally here.
								long2ObjectOpenHashMap.computeIfAbsent(l, (Long2ObjectFunction<? extends List<Ticket>>)(lx -> {
									this.tickets.checkBatchValue(walkHandle, index);
									return new ObjectArrayList<>(4);
								})).add(ticket);
							}

							if (matched || modifications != this.tickets.modifications) {
								answer = i << 1 | (matched ? 1 : 0);
								break;
							}
						}

						this.tickets.consumed(answer < 0 ? n : (answer >> 1) + 1, n);
						break;
					case NativeTicketStorage.NOTIFY_EVENT:
						long key = io[0];
						int then = (int)io[3];
						long modifications = this.tickets.modifications;
						this.listener((int)io[2]).update(key, (int)io[1], false);
						answer = 0;
						// The simulation notification that follows, unless the lists changed.
						if ((status & NativeTicketStorage.PAIRED) != 0 && modifications == this.tickets.modifications) {
							TicketStorage.ChunkUpdated chunkUpdated = this.simulationChunkUpdatedListener;
							if (chunkUpdated != null && !this.nativeListener[NativeTicketStorage.SIMULATION]) {
								chunkUpdated.update(key, then, false);
								answer = 1;
							}
						}

						break;
					case NativeTicketStorage.ACTIVATE_EVENT:
						this.addTicket(io[0], NativeTicketStorage.Cells.ticket((int)io[1]));
						break;
					default:
						throw new IllegalStateException("Unknown ticket walk event " + status);
				}
			}
		} finally {
			NativeTicketStorage.endWalk(walk, released);
		}
	}

	public void replaceTicketLevelOfType(int i, TicketType ticketType) {
		long[] pairs = this.tickets.export(2, NativeTicketStorage.Types.id(ticketType), this.count).clone();
		int n = this.count[0];
		List<Pair<Ticket, Long>> list = new ArrayList(n / 2);

		for (int j = 0; j < n; j += 2) {
			list.add(Pair.of(NativeTicketStorage.Cells.ticket((int)pairs[j + 1]), pairs[j]));
		}

		for (Pair<Ticket, Long> pair : list) {
			Long long_ = pair.getSecond();
			Ticket ticketx = pair.getFirst();
			this.removeTicket(long_, ticketx);
			TicketType ticketType2 = ticketx.getType();
			this.addTicket(long_, new Ticket(ticketType2, i));
		}
	}

	public boolean updateChunkForced(ChunkPos chunkPos, boolean bl) {
		Ticket ticket = new Ticket(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL);
		return bl ? this.addTicket(chunkPos.toLong(), ticket) : this.removeTicket(chunkPos.toLong(), ticket);
	}

	public LongSet getForceLoadedChunks() {
		return this.chunksWithForcedTickets;
	}

	/**
	 * A production tracker's listener: the storage it reads, and its native
	 * graph, which that storage then notifies directly. Calling
	 * {@link #update} is equivalent.
	 */
	public interface NativeChunkUpdated extends TicketStorage.ChunkUpdated {
		TicketStorage ticketStorage();

		/** Runs {@code binder} with the live native graph handle. */
		void withGraph(LongConsumer binder);
	}

	@FunctionalInterface
	public interface ChunkUpdated {
		void update(long l, int i, boolean bl);
	}

	public interface TicketPredicate {
		boolean test(Ticket ticket, long l);
	}
}

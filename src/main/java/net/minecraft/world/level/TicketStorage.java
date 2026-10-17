package net.minecraft.world.level;

import com.mojang.datafixers.util.Pair;
import net.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
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
    /** Actual VM destination passed through virtual removeTicketIf. Its contents
     * have one backing source; native commands drive traversal and mutation. */
    private final Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets;
	private LongSet chunksWithForcedTickets = new LongOpenHashSet();
	@Nullable
	private TicketStorage.ChunkUpdated loadingChunkUpdatedListener;
	@Nullable
	private TicketStorage.ChunkUpdated simulationChunkUpdatedListener;
	private final int[] count = new int[1];
	/** Listener slots bound to a native tracker graph. */
	private final boolean[] nativeListener = new boolean[2];

	private TicketStorage(NativeTicketStorage nativeTicketStorage, Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets) {
		this.tickets = nativeTicketStorage;
        this.deactivatedTickets = deactivatedTickets;
		this.updateForcedChunks();
	}

	public TicketStorage() {
		this(new NativeTicketStorage(), new Long2ObjectOpenHashMap<>());
	}

	private static TicketStorage fromPacked(List<Pair<ChunkPos, Ticket>> list) {
        Long2ObjectOpenHashMap<List<Ticket>> destination = new Long2ObjectOpenHashMap<>();
        java.util.Iterator<Pair<ChunkPos, Ticket>> iterator = null;
        Pair<ChunkPos, Ticket> pair = null;
        ChunkPos position = null;
        List<Ticket> tickets = null;
        Ticket ticket = null;
        long command = NativeTicketStorage.vmMapBegin(2);
        while (true) {
            int answer = 0;
            switch ((int)command & 255) {
                case 1: iterator = list.iterator(); break;
                case 2: answer = iterator.hasNext() ? 1 : 0; break;
                case 3: pair = iterator.next(); break;
                case 4: position = pair.getFirst(); break;
                case 5: tickets = destination.computeIfAbsent(position.toLong(),
                    (Long2ObjectFunction<? extends List<Ticket>>)(key -> new ObjectArrayList<>(INITIAL_TICKET_LIST_CAPACITY))); break;
                case 6: ticket = pair.getSecond(); break;
                case 7: tickets.add(ticket); break;
                case 10: return new TicketStorage(new NativeTicketStorage(), destination);
                default: throw new IllegalStateException("Unknown native decoded ticket command");
            }
            command = NativeTicketStorage.vmMapNext(command, answer);
        }
    }

	private List<Pair<ChunkPos, Ticket>> packTickets() {
        List<Pair<ChunkPos, Ticket>> pairs = new ArrayList<>();
        this.consumeTickets(1, null, pairs, null, null);
        this.consumeDeactivated(1, pairs);
        return pairs;
    }

    /** VM leaves for the native-owned ordered consumer. Ticket and ChunkPos
     * resources stay captured across queries that may reenter the storage. */
    private boolean consumeTickets(int mode, TicketType type, List<Pair<ChunkPos, Ticket>> packed,
                                   List<Pair<Ticket, Long>> replacements, LongSet forced) {
        Ticket ticket = null;
        ChunkPos position = null;
        try (NativeTicketStorage.Consumer operation = this.tickets.consumer(mode)) {
            int answer = 0;
            while (true) {
                operation.step(answer);
                int command = operation.command();
                long key = operation.key();
                int argument = operation.argument();
                answer = 0;
                switch (command) {
                    case 1: position = new ChunkPos(key); break;
                    case 11: operation.captureList(); break;
                    case 2: ticket = NativeTicketStorage.Cells.ticket(argument); answer = ticket.getType().shouldKeepDimensionActive() ? 1 : 0; break;
                    case 3: ticket = NativeTicketStorage.Cells.ticket(argument); answer = ticket.getType().persist() ? 1 : 0; break;
                    case 4: ticket = NativeTicketStorage.Cells.ticket(argument); answer = ticket.getType() == type ? 1 : 0; break;
                    case 5: ticket = NativeTicketStorage.Cells.ticket(argument); answer = ticket.getType().equals(TicketType.FORCED) ? 1 : 0; break;
                    case 6: packed.add(new Pair<>(position, ticket)); break;
                    case 10: replacements.add(Pair.of(ticket, key)); break;
                    case 7: forced.add(key); break;
                    case 8: return argument != 0;
                    case 9: NativeTicketStorage.check(argument); throw new IllegalStateException("Unknown native consumer failure");
                    default: throw new IllegalStateException("Unknown native ticket consumer command");
                }
            }
        }
    }

	public void activateAllDeactivatedTickets() {
        this.consumeDeactivated(0, null);
    }

    /** The publicly observable destination is a VM resource, not a mirror.
     * Rust drives both loops over its actual iterators and chooses each effect. */
    private void consumeDeactivated(int mode, List<Pair<ChunkPos, Ticket>> packed) {
        java.util.Iterator<Long2ObjectMap.Entry<List<Ticket>>> entries = null;
        Long2ObjectMap.Entry<List<Ticket>> entry = null;
        java.util.Iterator<Ticket> tickets = null;
        Ticket ticket = null;
        ChunkPos position = null;
        long key = 0;
        long command = NativeTicketStorage.vmMapBegin(mode);
        while (true) {
            int answer = 0;
            if (mode == 0) {
                switch ((int)command & 255) {
                    case 1: entries = Long2ObjectMaps.fastIterable(this.deactivatedTickets).iterator(); break;
                    case 2: answer = entries.hasNext() ? 1 : 0; break;
                    case 3: entry = entries.next(); break;
                    case 4: tickets = entry.getValue().iterator(); break;
                    case 5: answer = tickets.hasNext() ? 1 : 0; break;
                    case 6: ticket = tickets.next(); break;
                    case 7: key = entry.getLongKey(); break;
                    case 8: this.addTicket(key, ticket); break;
                    case 9: this.deactivatedTickets.clear(); break;
                    case 10: return;
                    default: throw new IllegalStateException("Unknown native ticket activation command");
                }
            } else {
                switch ((int)command & 255) {
                    case 1: entries = Long2ObjectMaps.fastIterable(this.deactivatedTickets).iterator(); break;
                    case 2: answer = entries.hasNext() ? 1 : 0; break;
                    case 3: entry = entries.next(); break;
                    case 4: position = new ChunkPos(entry.getLongKey()); break;
                    case 5: tickets = entry.getValue().iterator(); break;
                    case 6: answer = tickets.hasNext() ? 1 : 0; break;
                    case 7: ticket = tickets.next(); break;
                    case 8: answer = ticket.getType().persist() ? 1 : 0; break;
                    case 9: packed.add(new Pair<>(position, ticket)); break;
                    case 10: return;
                    default: throw new IllegalStateException("Unknown native deactivated pack command");
                }
            }
            command = NativeTicketStorage.vmMapNext(command, answer);
        }
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
		return this.consumeTickets(0, null, null, null, null);
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

    /** Internal generation binding: a strong lease on this actual native owner. */
    public final java.lang.foreign.MemorySegment generationOwner() { return this.tickets.generationOwner(); }

    /** Complete queued JVM unpins after a generation owner drops its store lease.
     * Native release has returned; no native lock spans these VM root changes. */
    public static void finishNativeTicketRelease() { NativeTicketStorage.finishNativeRelease(); }

    public final long generationTypeDescriptor() { return this.tickets.generationTypeDescriptor(); }
    public final void prepareGenerationRequest() { this.tickets.prepareGenerationRequest(); }
    public final void completeGenerationRequest(long key, int level, Ticket ticket, long status) {
        status = this.tickets.completeGenerationRequest(key, level, ticket, status);
        this.finishTicketAdd(key, ticket, status);
    }

	public boolean addTicket(long l, Ticket ticket) {
        List<Ticket> list = null;
        Ticket stored = null;
        try (NativeTicketStorage.LiveCursor operation = this.tickets.admission(l)) {
            list = operation.list;
            while (true) {
                long answer = 0;
                if (operation.captureNative()) stored = operation.ticket();
                switch (operation.command()) {
                    case 105: answer = stored.getType() == ticket.getType() ? 1 : 0; break;
                    case 108: answer = NativeTicketStorage.levelPair(stored.getTicketLevel(), ticket.getTicketLevel()); break;
                    case 110: stored.resetTicksLeft(); this.setDirty(); return false;
                    case 200: answer = list.size(); break;
                    case 201: stored = list.get(operation.index()); break;
                    case 11: answer = getTicketLevelAt(list, true); break;
                    case 12: answer = getTicketLevelAt(list, false); break;
                    case 13: list.add(ticket); break;
                    case 14:
                        if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) LOGGER.debug("ATI {} {}", new ChunkPos(l), ticket);
                        break;
                    case 15: answer = ticket.getType().doesSimulate() ? 1 : 0; break;
                    case 16: answer = ticket.getTicketLevel(); break;
                    case 17: answer = this.simulationChunkUpdatedListener != null ? 1 : 0; break;
                    case 18: this.simulationChunkUpdatedListener.update(l, ticket.getTicketLevel(), true); break;
                    case 19: answer = ticket.getType().doesLoad() ? 1 : 0; break;
                    case 20: answer = ticket.getTicketLevel(); break;
                    case 21: answer = this.loadingChunkUpdatedListener != null ? 1 : 0; break;
                    case 22: this.loadingChunkUpdatedListener.update(l, ticket.getTicketLevel(), true); break;
                    case 23: answer = ticket.getType().equals(TicketType.FORCED) ? 1 : 0; break;
                    case 24: this.chunksWithForcedTickets.add(l); break;
                    case 30: case 31: this.setDirty(); break;
                    case 32: return false;
                    case 33: return true;
                    default: throw new IllegalStateException("Unknown native ticket admission command");
                }
                operation.next(answer);
            }
        }
    }
    private static int getTicketLevelAt(List<Ticket> list, boolean simulation) {
        Ticket ticket = getLowestTicket(list, simulation);
        return ticket == null ? ChunkLevel.MAX_LEVEL + 1 : ticket.getTicketLevel();
    }
    private boolean finishTicketAdd(long l, Ticket ticket, long status) {
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
        Ticket ticket = getLowestTicket(this.getTickets(l), bl);
        return ticket == null ? ChunkLevel.MAX_LEVEL + 1 : ticket.getTicketLevel();
    }

    /** Native controls selection; VM leaves retain the actual iterator/tickets. */
    private static Ticket getLowestTicket(List<Ticket> list, boolean simulation) {
        try (NativeTicketStorage.LiveCursor operation = NativeTicketStorage.query(list, simulation)) {
            if (operation == null) return getLowestTicketVm(list, simulation);
            Ticket best = null, current = null;
            while (true) {
                if (operation.selectPrevious()) best = current;
                if (operation.captureNative()) current = operation.ticket();
                long answer = 0;
                switch (operation.command()) {
                    case 6: answer = current.getType().doesSimulate() ? 1 : 0; break;
                    case 7: answer = current.getType().doesLoad() ? 1 : 0; break;
                    case 104: answer = NativeTicketStorage.levelPair(current.getTicketLevel(), best.getTicketLevel()); break;
                    case 200: answer = list.size(); break;
                    case 201: current = list.get(operation.index()); break;
                    case 10: return best;
                    default: throw new IllegalStateException("Unknown native source query command");
                }
                operation.next(answer);
            }
        }
    }

    /** Actual foreign VM list/iterator effects; native selection remains authoritative. */
    private static Ticket getLowestTicketVm(List<Ticket> list, boolean simulation) {
        java.util.Iterator<Ticket> iterator = null;
        Ticket ticket = null;
        Ticket ticket2 = null;
        long command = NativeTicketStorage.queryBegin(simulation, list != null);
        while (true) {
            long answer = 0;
            switch ((int)command & 255) {
                case 1: iterator = list.iterator(); break;
                case 2: answer = iterator.hasNext() ? 1 : 0; break;
                case 3: ticket2 = iterator.next(); break;
                case 4: answer = ticket2.getTicketLevel(); break;
                case 5: answer = ticket.getTicketLevel(); break;
                case 6: answer = ticket2.getType().doesSimulate() ? 1 : 0; break;
                case 7: answer = ticket2.getType().doesLoad() ? 1 : 0; break;
                case 8: ticket = ticket2; break;
                case 10: return ticket;
                default: throw new IllegalStateException("Unknown native ticket query command");
            }
            command = NativeTicketStorage.queryNext(command, answer);
        }
    }

	public void removeTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		Ticket ticket = new Ticket(ticketType, ChunkLevel.byStatus(FullChunkStatus.FULL) - i);
		this.removeTicket(chunkPos.toLong(), ticket);
	}

	public void removeTicket(Ticket ticket, ChunkPos chunkPos) {
		this.removeTicket(chunkPos.toLong(), ticket);
	}

	public boolean removeTicket(long l, Ticket ticket) {
        List<Ticket> list = null;
        Ticket stored = null;
        try (NativeTicketStorage.LiveCursor operation = this.tickets.removal(l)) {
        list = operation.list;
        while (true) {
            long answer = 0;
            if (operation.captureNative()) stored = operation.ticket();
            switch (operation.command()) {
                case 105: answer = stored.getType() == ticket.getType() ? 1 : 0; break;
                case 108: answer = NativeTicketStorage.levelPair(stored.getTicketLevel(), ticket.getTicketLevel()); break;
                case 10: list.remove(operation.index()); break;
                case 200: answer = list.size(); break;
                case 201: stored = list.get(operation.index()); break;
                case 11:
                    if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) LOGGER.debug("RTI {} {}", new ChunkPos(l), stored);
                    break;
                case 12: answer = list.isEmpty() ? 1 : 0; break;
                case 13: this.tickets.unmap(l); break;
                case 14: answer = ticket.getType().doesSimulate() ? 1 : 0; break;
                case 15: answer = this.simulationChunkUpdatedListener != null ? 1 : 0; break;
                case 16: this.simulationChunkUpdatedListener.update(l, getTicketLevelAt(list, true), false); break;
                case 17: answer = ticket.getType().doesLoad() ? 1 : 0; break;
                case 18: answer = this.loadingChunkUpdatedListener != null ? 1 : 0; break;
                case 19: this.loadingChunkUpdatedListener.update(l, getTicketLevelAt(list, false), false); break;
                case 20: answer = ticket.getType().equals(TicketType.FORCED) ? 1 : 0; break;
                case 21: this.updateForcedChunks(); break;
                case 22: this.setDirty(); break;
                case 30: return false;
                case 31: return true;
                default: throw new IllegalStateException("Unknown native ticket removal command");
            }
            operation.next(answer);
        }
        }
    }

	private void updateForcedChunks() {
        LongOpenHashSet forced = new LongOpenHashSet();
        this.consumeTickets(3, null, null, null, forced);
        this.chunksWithForcedTickets = forced;
    }

    public String getTicketDebugString(long l, boolean bl) {
        Ticket ticket = getLowestTicket(this.getTickets(l), bl);
        return ticket == null ? "no_ticket" : ticket.toString();
    }

	public void purgeStaleTickets(ChunkMap chunkMap) {
		this.removeTicketIf((ticket, key) -> this.expiryPredicate(chunkMap, ticket, key), null);
		this.setDirty();
	}

    private boolean expiryPredicate(ChunkMap chunkMap, Ticket ticket, long key) {
        ChunkHolder holder = null;
        int command = NativeTicketStorage.expiryNext(0, 0);
        while (true) {
            int answer = 0;
            switch (command) {
                case 1: answer = ticket.getType().hasTimeout() ? 1 : 0; break;
                case 2: answer = ticket.getType().canExpireIfUnloaded() ? 1 : 0; break;
                case 3: holder = chunkMap.getUpdatingChunkIfPresent(key); answer = holder == null ? 1 : 0; break;
                case 4: answer = holder.isReadyForSaving() ? 1 : 0; break;
                case 5: ticket.decreaseTicksLeft(); break;
                case 6: answer = ticket.isTimedOut() ? 1 : 0; break;
                case -1: return false;
                case -2: return true;
                default: throw new IllegalStateException("Unknown native ticket expiry command");
            }
            command = NativeTicketStorage.expiryNext(command, answer);
        }
    }

	public void deactivateTicketsOnClosing() {
		this.removeTicketIf((ticket, key) -> ticket.getType() != TicketType.UNKNOWN, this.deactivatedTickets);
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
        Object capturedList = null;
		long[] io = this.tickets.io;
		boolean released = false;

		try {
			int answer = 0;
            Ticket removedTicket = null;

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
							long l = batch[i * 3];
                            capturedList = NativeTicketStorage.captureBatchList(batch[i * 3 + 2], capturedList);
                            Ticket ticket = NativeTicketStorage.Cells.ticket((int)batch[i * 3 + 1]);
							long modifications = this.tickets.modifications;
							boolean matched = ticketPredicate.test(ticket, l);
                            if (matched) removedTicket = ticket;
							if (matched && long2ObjectOpenHashMap != null) {
								long walkHandle = walk;
								int index = i;
								// The original sized a new list by entry.getValue(), read positionally here.
								long2ObjectOpenHashMap.computeIfAbsent(l, (Long2ObjectFunction<? extends List<Ticket>>)(lx -> {
									return new ObjectArrayList<>(this.tickets.batchValueSize(walkHandle, index));
								})).add(ticket);
							}

							if (matched || modifications != this.tickets.modifications) {
								answer = i << 1 | (matched ? 1 : 0);
								break;
							}
						}

						this.tickets.consumed(answer < 0 ? n : (answer >> 1) + 1, n);
						break;
                    case NativeTicketStorage.REMOVED_QUERY:
                        switch ((int)io[0]) {
                            case 0: answer = removedTicket.getType().doesLoad() ? 1 : 0; break;
                            case 1: answer = removedTicket.getType().doesSimulate() ? 1 : 0; break;
                            case 2: answer = removedTicket.getType().equals(TicketType.FORCED) ? 1 : 0; break;
                            default: throw new IllegalStateException("Unknown removed ticket query");
                        }
                        break;
                    case NativeTicketStorage.LIVE_NOTIFY:
                        long liveKey = io[0];
                        int which = (int)io[2];
                        TicketStorage.ChunkUpdated receiver = this.listener(which);
                        if (receiver != null) receiver.update(liveKey, getTicketLevelAt(this.tickets.walkList(walk), which == NativeTicketStorage.SIMULATION), false);
                        answer = 0;
                        break;
                    case NativeTicketStorage.DIRTY_EVENT:
                        this.setDirty(); answer = 0; break;
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
			try { NativeTicketStorage.endWalk(walk, released); }
            finally { java.lang.ref.Reference.reachabilityFence(capturedList); }
		}
	}

	public void replaceTicketLevelOfType(int i, TicketType ticketType) {
        List<Pair<Ticket, Long>> list = new ArrayList<>();
        this.consumeTickets(2, ticketType, null, list, null);

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

package net.vulkanic.bridge;

import net.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RustGalFrameScheduler<T> {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Sequence space reserved by each token for an ordered semantic sub-batch. */
	public static final long SEQUENCE_STRIDE = 1_000_000L;
	/** Must stay within the Rust GUI/world semantic batch contract. */
	private static final int MAX_PENDING_BATCHES = 65_536;

	private final String label;
	// Tokens carry the explicit sequence/stratum ordering; pending lookup is by
	// opaque batch id only. A hash table avoids an unnecessary O(log n) tree
	// operation for every semantic GUI item in a large text frame.
	private final Map<Long, Scheduled<T>> pending = new HashMap<>();
	private long nextBatchId = 1L;
	private long nextSequence = 1L;
	private int lastExecutedOrder = Integer.MIN_VALUE;

	public RustGalFrameScheduler(String label) {
		this.label = label;
	}

	public Token enqueue(long generation, String stratumId, int stratumOrder, T payload) {
		if (this.pending.size() >= MAX_PENDING_BATCHES) {
			throw new IllegalStateException(this.label + " pending semantic batch bound exceeded " + MAX_PENDING_BATCHES);
		}
		long batchId = this.nextBatchId++;
		long sequence = this.nextSequence++;
		this.nextSequence = Math.addExact(this.nextSequence - 1L, SEQUENCE_STRIDE);
		Token token = new Token(batchId, sequence, generation, stratumId, stratumOrder);
		this.pending.put(batchId, new Scheduled<>(token, payload));
		return token;
	}

	public List<T> takeAll(List<Token> tokens, long generation) {
		return this.takeAllItems(tokens, generation).stream().map(Item::payload).toList();
	}

	/** Returns the semantic payload with its stable scheduler sequence. */
	public List<Item<T>> takeAllItems(List<Token> tokens, long generation) {
		List<Token> ordered = new ArrayList<>(tokens);
		ordered.sort(Comparator.comparingInt(Token::stratumOrder).thenComparingLong(Token::sequence));
		// One submitted semantic batch may provide several render-state elements
		// (for example a wrapped image split into unit-UV quads).  Those elements
		// deliberately share their token, while the scheduler owns exactly one
		// payload for that token.  Consume it once, retaining the explicit batch
		// ordering, and reject any attempt to alias a batch id with different work.
		Map<Long, Token> uniqueTokens = new HashMap<>();
		List<Token> uniqueOrdered = new ArrayList<>(ordered.size());
		for (Token token : ordered) {
			Token existing = uniqueTokens.putIfAbsent(token.batchId(), token);
			if (existing == null) {
				uniqueOrdered.add(token);
			} else if (!existing.equals(token)) {
				throw new IllegalStateException(this.label + " conflicting duplicate batch token: batch=" + token.batchId());
			}
		}
		List<Item<T>> payloads = new ArrayList<>(uniqueOrdered.size());
		int lastOrder = Integer.MIN_VALUE;
		for (Token token : uniqueOrdered) {
			Scheduled<T> scheduled = this.take(token, generation);
			if (scheduled.token().stratumOrder() < lastOrder) {
				throw new IllegalStateException(this.label + " batch executed out of stratum order: stratum=" + scheduled.token().stratumId());
			}
			lastOrder = scheduled.token().stratumOrder();
			payloads.add(new Item<>(scheduled.token(), scheduled.payload()));
		}
		this.lastExecutedOrder = Integer.MIN_VALUE;
		return payloads;
	}

	private Scheduled<T> take(Token token, long generation) {
		Scheduled<T> scheduled = this.pending.remove(token.batchId());
		if (scheduled == null) {
			throw new IllegalStateException(this.label + " batch is no longer pending: batch=" + token.batchId());
		}
		Token scheduledToken = scheduled.token();
		if (scheduledToken.generation() != token.generation() || scheduledToken.generation() != generation) {
			throw new IllegalStateException(this.label + " batch generation is stale: batch=" + token.batchId());
		}
		if (
			scheduledToken.sequence() != token.sequence()
				|| !scheduledToken.stratumId().equals(token.stratumId())
				|| scheduledToken.stratumOrder() != token.stratumOrder()
		) {
			throw new IllegalStateException(this.label + " token does not match scheduled work: batch=" + token.batchId());
		}
		if (scheduledToken.stratumOrder() < this.lastExecutedOrder) {
			throw new IllegalStateException(this.label + " batch executed out of stratum order: stratum=" + scheduledToken.stratumId());
		}
		this.lastExecutedOrder = scheduledToken.stratumOrder();
		return scheduled;
	}

	public int cancelFrame(long frameId, String reason) {
		if (frameId == 0L || this.pending.isEmpty()) {
			return 0;
		}
		return cancelAll(reason + ":frame=" + frameId);
	}

	public int cancelAll(String reason) {
		int cancelled = this.pending.size();
		if (!this.pending.isEmpty()) {
			LOGGER.info("Cancelling {} {} batch(es): {}", this.pending.size(), this.label, reason);
			this.pending.clear();
		}
		this.lastExecutedOrder = Integer.MIN_VALUE;
		return cancelled;
	}

	public int pendingCount() {
		return this.pending.size();
	}

	public record Token(long batchId, long sequence, long generation, String stratumId, int stratumOrder) {
	}

	public record Item<T>(Token token, T payload) {
	}

	private record Scheduled<T>(Token token, T payload) {
	}
}

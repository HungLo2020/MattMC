package net.vulkanic.bridge;

import net.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class RustGalFrameScheduler<T> {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final String label;
	private final NavigableMap<Long, Scheduled<T>> pending = new TreeMap<>();
	private long nextBatchId = 1L;
	private long nextSequence = 1L;
	private int lastExecutedOrder = Integer.MIN_VALUE;

	public RustGalFrameScheduler(String label) {
		this.label = label;
	}

	public Token enqueue(long generation, String stratumId, int stratumOrder, T payload) {
		long batchId = this.nextBatchId++;
		long sequence = this.nextSequence++;
		Token token = new Token(batchId, sequence, generation, stratumId, stratumOrder);
		this.pending.put(batchId, new Scheduled<>(token, payload));
		return token;
	}

	public List<T> takeAll(List<Token> tokens, long generation) {
		List<Token> ordered = new ArrayList<>(tokens);
		ordered.sort(Comparator.comparingInt(Token::stratumOrder).thenComparingLong(Token::sequence));
		List<T> payloads = new ArrayList<>(ordered.size());
		int lastOrder = Integer.MIN_VALUE;
		for (Token token : ordered) {
			Scheduled<T> scheduled = this.take(token, generation);
			if (scheduled.token().stratumOrder() < lastOrder) {
				throw new IllegalStateException(this.label + " batch executed out of stratum order: stratum=" + scheduled.token().stratumId());
			}
			lastOrder = scheduled.token().stratumOrder();
			payloads.add(scheduled.payload());
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

	private record Scheduled<T>(Token token, T payload) {
	}
}

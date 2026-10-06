package net.vulkanic.world;

import java.util.NoSuchElementException;

/**
 * FIFO of primitive longs that keeps its capacity. fastutil's
 * {@code LongArrayFIFOQueue} halves its array whenever it drains below a
 * quarter full, so a per-frame visibility search reallocated its frontier on
 * every wave.
 */
final class LongRingQueue {
	private long[] elements;
	private int head;
	private int size;

	LongRingQueue(int initialCapacity) {
		this.elements = new long[Math.max(4, Integer.highestOneBit(Math.max(1, initialCapacity - 1)) << 1)];
	}

	void enqueue(long value) {
		if (this.size == this.elements.length) {
			long[] grown = new long[this.elements.length << 1];
			int firstRun = Math.min(this.size, this.elements.length - this.head);
			System.arraycopy(this.elements, this.head, grown, 0, firstRun);
			System.arraycopy(this.elements, 0, grown, firstRun, this.size - firstRun);
			this.elements = grown;
			this.head = 0;
		}
		this.elements[(this.head + this.size) & (this.elements.length - 1)] = value;
		this.size++;
	}

	long dequeueLong() {
		if (this.size == 0) {
			throw new NoSuchElementException();
		}
		long value = this.elements[this.head];
		this.head = (this.head + 1) & (this.elements.length - 1);
		this.size--;
		return value;
	}

	boolean isEmpty() {
		return this.size == 0;
	}

	int size() {
		return this.size;
	}

	void clear() {
		this.head = 0;
		this.size = 0;
	}
}

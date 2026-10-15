package net.minecraft.world.ticks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class LevelChunkTicks<T> implements SerializableTickContainer<T>, TickContainerAccess<T> {
	/** Native queue (PriorityQueue array order under DRAIN_ORDER) plus the per-position dedup keys. */
	private final NativeChunkTicks<T> ticks = new NativeChunkTicks<>();
	@Nullable
	private List<SavedTick<T>> pendingTicks;
	@Nullable
	private BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded;

	public LevelChunkTicks() {
	}

	public LevelChunkTicks(List<SavedTick<T>> list) {
		this.pendingTicks = list;

		for (SavedTick<T> savedTick : list) {
			this.ticks.addKey(savedTick.type(), savedTick.pos());
		}
	}

	NativeChunkTicks<T> nativeTicks() {
		return this.ticks;
	}

	public void setOnTickAdded(@Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> biConsumer) {
		this.onTickAdded = biConsumer;
	}

	@Nullable
	public ScheduledTick<T> peek() {
		return this.ticks.peek();
	}

	@Nullable
	public ScheduledTick<T> poll() {
		return this.ticks.poll();
	}

	@Override
	public void schedule(ScheduledTick<T> scheduledTick) {
		if (this.ticks.schedule(scheduledTick, false)) {
			this.tickAdded(scheduledTick);
		}
	}

	private void scheduleUnchecked(ScheduledTick<T> scheduledTick) {
		this.ticks.schedule(scheduledTick, true);
		this.tickAdded(scheduledTick);
	}

	private void tickAdded(ScheduledTick<T> scheduledTick) {
		if (this.onTickAdded != null) {
			this.onTickAdded.accept(this, scheduledTick);
		}
	}

	@Override
	public boolean hasScheduledTick(BlockPos blockPos, T object) {
		return this.ticks.containsKey(object, blockPos);
	}

	public void removeIf(Predicate<ScheduledTick<T>> predicate) {
		// The native iterator's remove also drops the removed tick's dedup key.
		Iterator<ScheduledTick<T>> iterator = this.ticks.iterator();

		while (iterator.hasNext()) {
			ScheduledTick<T> scheduledTick = (ScheduledTick<T>)iterator.next();
			if (predicate.test(scheduledTick)) {
				iterator.remove();
			}
		}
	}

	/** LevelTicks.clearArea's box removal; same effect as removeIf with a box.isInside predicate. */
	void removeInBox(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		this.ticks.removeInBox(box);
	}

	public Stream<ScheduledTick<T>> getAll() {
		return StreamSupport.stream(this.ticks.spliterator(), false);
	}

	@Override
	public int count() {
		return this.ticks.size() + (this.pendingTicks != null ? this.pendingTicks.size() : 0);
	}

	@Override
	public List<SavedTick<T>> pack(long l) {
		List<SavedTick<T>> list = new ArrayList(this.ticks.size());
		if (this.pendingTicks != null) {
			list.addAll(this.pendingTicks);
		}

		Iterator<ScheduledTick<T>> iterator = this.ticks.iterator();

		while (iterator.hasNext()) {
			list.add(iterator.next().toSavedTick(l));
		}

		return list;
	}

	public void unpack(long l) {
		if (this.pendingTicks != null) {
			int i = -this.pendingTicks.size();

			for (SavedTick<T> savedTick : this.pendingTicks) {
				this.scheduleUnchecked(savedTick.unpack(l, i++));
			}
		}

		this.pendingTicks = null;
	}
}

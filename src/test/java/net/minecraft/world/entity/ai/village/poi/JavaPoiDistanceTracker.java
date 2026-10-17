package net.minecraft.world.entity.ai.village.poi;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.function.LongPredicate;
import net.minecraft.server.level.JavaSectionTracker;

/** Original PoiManager.DistanceTracker at the pinned reference, relocated onto
 * the pinned original SectionTracker/graph/queue. Only isVillageCenter is
 * supplied instead of the enclosing PoiManager's. */
public class JavaPoiDistanceTracker extends JavaSectionTracker {
	private final LongPredicate villageCenter;
	private final Long2ByteMap levels = new Long2ByteOpenHashMap();

	public JavaPoiDistanceTracker(LongPredicate villageCenter) {
		super(7, 16, 256);
		this.villageCenter = villageCenter;
		this.levels.defaultReturnValue((byte)7);
	}

	@Override
	protected int getLevelFromSource(long l) {
		return this.villageCenter.test(l) ? 0 : 7;
	}

	@Override
	protected int getLevel(long l) {
		return this.levels.get(l);
	}

	@Override
	protected void setLevel(long l, int i) {
		if (i > 6) {
			this.levels.remove(l);
		} else {
			this.levels.put(l, (byte)i);
		}
	}

	public void runAllUpdates() {
		super.runUpdates(Integer.MAX_VALUE);
	}
}

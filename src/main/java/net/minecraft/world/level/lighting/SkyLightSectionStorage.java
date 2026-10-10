package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class SkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
	protected SkyLightSectionStorage(LightChunkGetter lightChunkGetter) {
		super(
			LightLayer.SKY,
			lightChunkGetter,
			new SkyLightSectionStorage.SkyDataLayerStorageMap()
		);
	}

	@Override
	protected int getLightValue(long l) {
		return this.getLightValue(l, false);
	}

	protected int getLightValue(long l, boolean bl) {
        if (getClass() == SkyLightSectionStorage.class) {
            var owner = (bl ? this.updatingSectionData : this.visibleSectionData).nativeMap;
            if (owner != null) {
                boolean enabled = !bl || this.lightOnInSection(SectionPos.blockToSection(l));
                long result = owner.sample(l, true, bl, enabled);
                if (result != 0) return (int)result;
            }
        }
		long m = SectionPos.blockToSection(l);
		int i = SectionPos.y(m);
		SkyLightSectionStorage.SkyDataLayerStorageMap skyDataLayerStorageMap = bl ? this.updatingSectionData : this.visibleSectionData;
		int j = skyDataLayerStorageMap.top(SectionPos.getZeroNode(m));
		if (j != skyDataLayerStorageMap.lowestY() && i < j) {
			DataLayer dataLayer = this.getDataLayer(skyDataLayerStorageMap, m);
			if (dataLayer == null) {
				for (l = BlockPos.getFlatIndex(l); dataLayer == null; dataLayer = this.getDataLayer(skyDataLayerStorageMap, m)) {
					if (++i >= j) {
						return 15;
					}

					m = SectionPos.offset(m, Direction.UP);
				}
			}

			return dataLayer.get(
				SectionPos.sectionRelative(BlockPos.getX(l)), SectionPos.sectionRelative(BlockPos.getY(l)), SectionPos.sectionRelative(BlockPos.getZ(l))
			);
		} else {
			return bl && !this.lightOnInSection(m) ? 0 : 15;
		}
	}

	@Override
	protected void onNodeAdded(long l) {
		int i = SectionPos.y(l);
		if (this.updatingSectionData.lowestY() > i) {
			this.updatingSectionData.lowestY(i);
		}

		long m = SectionPos.getZeroNode(l);
		int j = this.updatingSectionData.top(m);
		if (j < i + 1) {
			this.updatingSectionData.putTop(m, i + 1);
		}
	}

	@Override
	protected void onNodeRemoved(long l) {
		long m = SectionPos.getZeroNode(l);
		int i = SectionPos.y(l);
		if (this.updatingSectionData.top(m) == i + 1) {
			long n;
			for (n = l; !this.storingLightForSection(n) && this.hasLightDataAtOrBelow(i); n = SectionPos.offset(n, Direction.DOWN)) {
				i--;
			}

			if (this.storingLightForSection(n)) {
				this.updatingSectionData.putTop(m, i + 1);
			} else {
				this.updatingSectionData.removeTop(m);
			}
		}
	}

	@Override
	protected DataLayer createDataLayer(long l) {
		DataLayer dataLayer = this.queuedSections.get(l);
		if (dataLayer != null) {
			return dataLayer;
		} else {
			int i = this.updatingSectionData.top(SectionPos.getZeroNode(l));
			if (i != this.updatingSectionData.lowestY() && SectionPos.y(l) < i) {
				long m = SectionPos.offset(l, Direction.UP);

				DataLayer dataLayer2;
				while ((dataLayer2 = this.getDataLayer(m, true)) == null) {
					m = SectionPos.offset(m, Direction.UP);
				}

				return repeatFirstLayer(dataLayer2);
			} else {
				return this.lightOnInSection(l) ? new DataLayer(15) : new DataLayer();
			}
		}
	}

	private static DataLayer repeatFirstLayer(DataLayer dataLayer) {
        if (dataLayer.getClass() == DataLayer.class) {
            DataLayer nativeCopy = dataLayer.repeatNativeFirstLightLayer();
            if (nativeCopy != null) return nativeCopy;
        }
		if (dataLayer.isDefinitelyHomogenous()) {
			return dataLayer.copy();
		} else {
			byte[] bs = dataLayer.getData();
			byte[] cs = new byte[2048];

			for (int i = 0; i < 16; i++) {
				System.arraycopy(bs, 0, cs, i * 128, 128);
			}

			return new DataLayer(cs);
		}
	}

	protected boolean hasLightDataAtOrBelow(int i) {
		return i >= this.updatingSectionData.lowestY();
	}

	protected boolean isAboveData(long l) {
		long m = SectionPos.getZeroNode(l);
		int i = this.updatingSectionData.top(m);
		return i == this.updatingSectionData.lowestY() || SectionPos.y(l) >= i;
	}

	protected int getTopSectionY(long l) {
		return this.updatingSectionData.top(l);
	}

	protected int getBottomSectionY() {
		return this.updatingSectionData.lowestY();
	}

	protected static final class SkyDataLayerStorageMap extends DataLayerStorageMap<SkyLightSectionStorage.SkyDataLayerStorageMap> {
		int currentLowestY;
		final Long2IntOpenHashMap topSections;

        SkyDataLayerStorageMap() { this(new NativeLightingMap()); }
        private SkyDataLayerStorageMap(NativeLightingMap owner) {
            super(owner); this.topSections = null;
        }
        int lowestY() { return nativeMap == null ? currentLowestY : nativeMap.lowestY(); }
        void lowestY(int value) {
            if (nativeMap == null) { currentLowestY = value; topSections.defaultReturnValue(value); }
            else nativeMap.lowestY(value, false);
        }
        void topDefault(int value) {
            if (nativeMap == null) topSections.defaultReturnValue(value);
            else nativeMap.lowestY(value, true);
        }
        int top(long key) { return nativeMap == null ? topSections.get(key) : nativeMap.top(key); }
        int putTop(long key, int value) { return nativeMap == null ? topSections.put(key, value) : nativeMap.putTop(key, value, false); }
        int removeTop(long key) { return nativeMap == null ? topSections.remove(key) : nativeMap.putTop(key, 0, true); }
        java.util.Map<Long, Integer> topsForVerification() {
            return nativeMap == null ? topSections : nativeMap.topsForVerification();
        }

		public SkyDataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> long2ObjectOpenHashMap, Long2IntOpenHashMap long2IntOpenHashMap, int i) {
			super(long2ObjectOpenHashMap);
			this.topSections = long2IntOpenHashMap;
			long2IntOpenHashMap.defaultReturnValue(i);
			this.currentLowestY = i;
		}

		public SkyLightSectionStorage.SkyDataLayerStorageMap copy() {
			return nativeMap == null
                ? new SkyLightSectionStorage.SkyDataLayerStorageMap(this.map.clone(), this.topSections.clone(), this.currentLowestY)
                : new SkyLightSectionStorage.SkyDataLayerStorageMap(nativeMap.copy());
		}
	}
}

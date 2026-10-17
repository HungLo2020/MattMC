package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import org.jetbrains.annotations.Nullable;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {
	private static final int CACHE_SIZE = 2;
	private final long[] lastSectionKeys;
	private final DataLayer[] lastSections;
	private boolean cacheEnabled;
	protected final Long2ObjectOpenHashMap<DataLayer> map;
    protected final NativeLightingMap nativeMap;

	protected DataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> long2ObjectOpenHashMap) {
		this.map = long2ObjectOpenHashMap;
        this.nativeMap = null;
        this.lastSectionKeys = new long[2];
        this.lastSections = new DataLayer[2];
		this.clearCache();
		this.cacheEnabled = true;
	}

    /** Only owned canonical factories use Rust; supplied Java maps retain their alias. */
    DataLayerStorageMap(NativeLightingMap owner) {
        this.nativeMap = owner;
        this.map = null;
        this.lastSectionKeys = null;
        this.lastSections = null;
    }

    java.util.Map<Long, DataLayer> snapshotForVerification() {
        return nativeMap == null ? map : nativeMap.snapshotForVerification();
    }

	public abstract M copy();

	public DataLayer copyDataLayer(long l) {
        if (nativeMap != null) {
            // Preserve raw lookup and virtual copy dispatch, including null's NPE.
            DataLayer copy = nativeMap.get(l, true).copy();
            nativeMap.set(l, copy);
            nativeMap.clearCache(false);
            return copy;
        }
		DataLayer dataLayer = this.map.get(l).copy();
		this.map.put(l, dataLayer);
		this.clearCache();
		return dataLayer;
	}

	public boolean hasLayer(long l) {
		return nativeMap == null ? this.map.containsKey(l) : nativeMap.has(l);
	}

	@Nullable
	public DataLayer getLayer(long l) {
        if (nativeMap != null) return nativeMap.get(l, false);
		if (this.cacheEnabled) {
			for (int i = 0; i < 2; i++) {
				if (l == this.lastSectionKeys[i]) {
					return this.lastSections[i];
				}
			}
		}

		DataLayer dataLayer = this.map.get(l);
		if (dataLayer == null) {
			return null;
		} else {
			if (this.cacheEnabled) {
				for (int j = 1; j > 0; j--) {
					this.lastSectionKeys[j] = this.lastSectionKeys[j - 1];
					this.lastSections[j] = this.lastSections[j - 1];
				}

				this.lastSectionKeys[0] = l;
				this.lastSections[0] = dataLayer;
			}

			return dataLayer;
		}
	}

	@Nullable
	public DataLayer removeLayer(long l) {
		return nativeMap == null ? this.map.remove(l) : nativeMap.remove(l);
	}

	public void setLayer(long l, DataLayer dataLayer) {
        if (nativeMap != null) { nativeMap.set(l, dataLayer); return; }
		this.map.put(l, dataLayer);
	}

	public void clearCache() {
        if (nativeMap != null) { nativeMap.clearCache(false); return; }
		for (int i = 0; i < 2; i++) {
			this.lastSectionKeys[i] = Long.MAX_VALUE;
			this.lastSections[i] = null;
		}
	}

	public void disableCache() {
        if (nativeMap != null) { nativeMap.clearCache(true); return; }
		this.cacheEnabled = false;
	}
}

package net.minecraft.world.level.chunk;

import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.util.VisibleForDebug;
import org.jetbrains.annotations.Nullable;

public class DataLayer {
	public static final int LAYER_COUNT = 16;
	public static final int LAYER_SIZE = 128;
	public static final int SIZE = 2048;
	private static final int NIBBLE_SIZE = 4;
	@Nullable
	protected byte[] data;
	private int defaultValue;
	@Nullable
	private NativeLightLayer nativeLayer;

	public DataLayer() {
		this(0);
	}

	public DataLayer(int i) {
		this.defaultValue = i;
		if (getClass() == DataLayer.class) this.nativeLayer = NativeLightLayer.create(i);
	}

	public DataLayer(byte[] bs) {
		this.data = bs;
		this.defaultValue = 0;
		if (bs.length != 2048) {
			throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("DataLayer should be 2048 bytes not: " + bs.length));
		}
	}

    /** Independent import; callers retaining a mutable array use the public constructor. */
    public static DataLayer copyOf(byte[] bytes) {
        // Keep the original malformed-array constructor and exception path.
        if (bytes == null || bytes.length != SIZE) return new DataLayer((byte[])bytes.clone());
        return new DataLayer(NativeLightLayer.importBytes(bytes));
    }

    private DataLayer(NativeLightLayer owner) {
        this.nativeLayer = owner;
        this.defaultValue = owner.rawDefault();
    }

    /** Package-internal saved decode handoff; consumes independent native leases. */
    static DataLayer adoptDecoded(java.lang.foreign.MemorySegment owner, java.lang.foreign.MemorySegment view) {
        var layer = NativeLightLayer.adoptOwned(owner, view);
        return new DataLayer(layer);
    }

    /** Refreshes this facade after native materialization; no array escape. */
    void refreshNativeLightView() {
        if(!this.hasNativeLight())throw new IllegalStateException("Native light generation escaped during a coordinator write");
        this.nativeLayer.refreshView();
    }

    @Nullable
    NativeLightLayer nativeLightLayer() { return this.hasNativeLight() ? this.nativeLayer : null; }

    private boolean hasNativeLight() { return this.nativeLayer != null && this.nativeLayer.valid(); }

    /** Caller-retained CPU owner for the Rust light map; never retain this raw address in Java. */
    public java.lang.foreign.MemorySegment nativeLightOwnerForMap() {
        return getClass() == DataLayer.class && this.hasNativeLight()
            ? this.nativeLayer.ownerForNativeCall() : java.lang.foreign.MemorySegment.NULL;
    }

    /** A read-only CPU generation lease; the returned segment owns its lifetime. */
    @Nullable
    public java.lang.foreign.MemorySegment nativeLightView() {
        return getClass() == DataLayer.class && this.hasNativeLight()
            ? this.nativeLayer.view().metadata() : null;
    }

    @Nullable
    public DataLayer repeatNativeFirstLightLayer() {
        return !this.hasNativeLight() ? null : new DataLayer(this.nativeLayer.repeatFirst());
    }

    /** Installs a native propagation result without exposing a mutable array. */
    public boolean installNativeLightResult(long engine, int index) {
        if (!this.hasNativeLight()) return false;
        this.nativeLayer.installResult(engine, index);
        return true;
    }

    /** Retains lazy allocation until the original sky loop actually writes. */
    public boolean seedNativeSkyLight(int[] columns, int bottom, int minX, int minZ, long[] entries, int[] output) {
        if (!this.hasNativeLight()) return false;
        if (!this.nativeLayer.allocated() && (this.nativeLayer.rawDefault() < 0 || this.nativeLayer.rawDefault() > 15)) return false;
        this.nativeLayer.seedSky(columns, bottom, minX, minZ, entries, output);
        return true;
    }

    /** Seeds the canonical native work owner without exporting entry pairs. */
    public boolean seedNativeSkyLightQueued(java.lang.foreign.MemorySegment columns, int bottom, int minX, int minZ,
            net.minecraft.world.level.lighting.NativeLightQueues queues, java.lang.foreign.MemorySegment output) {
        if (!this.hasNativeLight()) return false;
        if (!this.nativeLayer.allocated() && (this.nativeLayer.rawDefault() < 0 || this.nativeLayer.rawDefault() > 15)) return false;
        this.nativeLayer.seedSkyQueued(columns, bottom, minX, minZ, queues, output);
        return true;
    }

	// Compatibility array projection. Native consumers use the leased CPU view.
	@Nullable
	public byte[] dataForNativeLight() {
        return this.hasNativeLight() && this.nativeLayer.allocated() ? this.getData() : this.data;
	}

	public int defaultValueForNativeLight() {
        return !this.hasNativeLight() ? this.defaultValue : this.nativeLayer.rawDefault();
	}

	public int get(int i, int j, int k) {
		return this.get(getIndex(i, j, k));
	}

	public void set(int i, int j, int k, int l) {
		this.set(getIndex(i, j, k), l);
	}

	private static int getIndex(int i, int j, int k) {
		return j << 8 | k << 4 | i;
	}

	private int get(int i) {
        if (this.hasNativeLight()) {
            if (!this.nativeLayer.allocated()) return this.nativeLayer.rawDefault();
            if (i >= 0 && i < 4096) return this.nativeLayer.view().get(i);
            // Preserve the original array exception, without a native unchecked read.
            this.getData();
        }
		if (this.data == null) {
			return this.defaultValue;
		} else {
			int j = getByteIndex(i);
			int k = getNibbleIndex(i);
			return this.data[j] >> 4 * k & 15;
		}
	}

	private void set(int i, int j) {
        if (this.hasNativeLight() && i >= 0 && i < 4096) {
            this.nativeLayer.set(i, j);
            return;
        }
		byte[] bs = this.getData();
		int k = getByteIndex(i);
		int l = getNibbleIndex(i);
		int m = ~(15 << 4 * l);
		int n = (j & 15) << 4 * l;
		bs[k] = (byte)(bs[k] & m | n);
	}

	private static int getNibbleIndex(int i) {
		return i & 1;
	}

	private static int getByteIndex(int i) {
		return i >> 1;
	}

	public void fill(int i) {
        if (this.nativeLayer != null) this.nativeLayer.fill(i);
        else if (getClass() == DataLayer.class) this.nativeLayer = NativeLightLayer.create(i);
		this.defaultValue = i;
		this.data = null;
	}

	private static byte packFilled(int i) {
		byte b = (byte)i;

		for (int j = 4; j < 8; j += 4) {
			b = (byte)(b | i << j);
		}

		return b;
	}

	public byte[] getData() {
        if (this.hasNativeLight()) {
            this.data = this.nativeLayer.exportBytes();
            this.defaultValue = this.nativeLayer.rawDefault();
            this.nativeLayer.invalidate();
            return this.data;
        }
		if (this.data == null) {
			this.data = new byte[2048];
			if (this.defaultValue != 0) {
				Arrays.fill(this.data, packFilled(this.defaultValue));
			}
		}

		return this.data;
	}

	public DataLayer copy() {
        if (getClass() == DataLayer.class) {
            if (this.hasNativeLight()) return new DataLayer(this.nativeLayer.copy());
            if (this.data != null && this.data.length == 2048) return new DataLayer(NativeLightLayer.importBytes(this.data));
        }
		return this.data == null ? new DataLayer(this.defaultValue) : new DataLayer((byte[])this.data.clone());
	}

	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();

		for (int i = 0; i < 4096; i++) {
			stringBuilder.append(Integer.toHexString(this.get(i)));
			if ((i & 15) == 15) {
				stringBuilder.append("\n");
			}

			if ((i & 0xFF) == 255) {
				stringBuilder.append("\n");
			}
		}

		return stringBuilder.toString();
	}

	@VisibleForDebug
	public String layerToString(int i) {
		StringBuilder stringBuilder = new StringBuilder();

		for (int j = 0; j < 256; j++) {
			stringBuilder.append(Integer.toHexString(this.get(j)));
			if ((j & 15) == 15) {
				stringBuilder.append("\n");
			}
		}

		return stringBuilder.toString();
	}

	public boolean isDefinitelyHomogenous() {
		return !this.hasNativeLight() ? this.data == null : !this.nativeLayer.allocated();
	}

	public boolean isDefinitelyFilledWith(int i) {
		return this.isDefinitelyHomogenous() && this.defaultValueForNativeLight() == i;
	}

	public boolean isEmpty() {
		return this.isDefinitelyFilledWith(0);
	}
}

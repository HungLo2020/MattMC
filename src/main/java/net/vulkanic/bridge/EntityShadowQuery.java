package net.vulkanic.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/**
 * Rust's shadow-pass admission of entity candidates, asked before Java
 * extracts them. The native handle shares only its context's active shadow
 * policy, so a query never joins a pipelined frame. Rust applies the same
 * admission again to the frame, so this only saves extraction work.
 * Render thread only; owned and closed by {@link VulkanicGalBridge}.
 */
public final class EntityShadowQuery implements AutoCloseable {
	private static final int DECIDED = 0;
	private static final int UNDECIDED = 1;
	private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_vulkanic_gal_entity_shadow_query_create",
		FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_vulkanic_gal_entity_shadow_query_destroy", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle SELECT = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_vulkanic_gal_entity_shadow_query_select", FunctionDescriptor.of(ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS,
			ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
			ValueLayout.ADDRESS));

	private final Arena arena = Arena.ofConfined();
	private final MemorySegment handle;
	private final MemorySegment matrices = arena.allocate(32 * Float.BYTES, 8);
	private final MemorySegment camera = arena.allocate(3 * Double.BYTES, 8);
	private Arena candidateArena;
	private MemorySegment flags = MemorySegment.NULL;
	private MemorySegment bounds = MemorySegment.NULL;
	private MemorySegment holderBounds = MemorySegment.NULL;
	private MemorySegment admitted = MemorySegment.NULL;
	private int capacity;
	private int count;
	private boolean closed;

	EntityShadowQuery(long contextId) {
		try {
			this.handle = (MemorySegment) CREATE.invokeExact(contextId);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Failed to create Rust entity shadow query", throwable);
		}
		if (this.handle.equals(MemorySegment.NULL)) {
			this.arena.close();
			throw new IllegalStateException("Rust entity shadow query has no context " + contextId);
		}
	}

	public void clear() {
		this.count = 0;
	}

	public int size() {
		return this.count;
	}

	public void add(VulkanicGalBridge.WorldEntityCullingRecord culling) {
		if (this.count == this.capacity) {
			this.grow(Math.max(64, this.capacity * 2));
		}
		int index = this.count++;
		this.flags.setAtIndex(ValueLayout.JAVA_INT, index, culling.flags());
		writeBox(this.bounds, index, culling.bounds());
		writeBox(this.holderBounds, index, culling.leashHolderBounds());
	}

	/**
	 * Decides admission of every added candidate for the frame described by
	 * the copied inputs. Returns {@code false} when Rust cannot decide here;
	 * the caller must then keep every candidate.
	 */
	public boolean select(int skyType, float timeOfDay, float[] projection, float[] view, float farPlane,
			int configuredShadowDistanceChunks, double cameraX, double cameraY, double cameraZ) {
		if (this.closed) {
			return false;
		}
		MemorySegment.copy(projection, 0, this.matrices, ValueLayout.JAVA_FLOAT, 0, 16);
		MemorySegment.copy(view, 0, this.matrices, ValueLayout.JAVA_FLOAT, 16L * Float.BYTES, 16);
		this.camera.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, cameraX);
		this.camera.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, cameraY);
		this.camera.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, cameraZ);
		int status;
		try {
			status = (int) SELECT.invokeExact(this.handle, skyType, timeOfDay, this.matrices,
				this.matrices.asSlice(16L * Float.BYTES), farPlane, configuredShadowDistanceChunks, this.camera,
				this.count, this.flags, this.bounds, this.holderBounds, this.admitted);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Failed to query Rust entity shadow admission", throwable);
		}
		if (status != DECIDED && status != UNDECIDED) {
			throw new IllegalStateException("Rust entity shadow query failed with status " + status);
		}
		return status == DECIDED;
	}

	/** Valid after {@link #select} returned {@code true}. */
	public boolean admitted(int index) {
		return this.admitted.get(ValueLayout.JAVA_BYTE, index) != 0;
	}

	private void grow(int newCapacity) {
		Arena next = Arena.ofConfined();
		MemorySegment nextFlags = next.allocate((long) newCapacity * Integer.BYTES, 8);
		MemorySegment nextBounds = next.allocate((long) newCapacity * 6 * Double.BYTES, 8);
		MemorySegment nextHolder = next.allocate((long) newCapacity * 6 * Double.BYTES, 8);
		MemorySegment nextAdmitted = next.allocate(newCapacity, 8);
		if (this.count > 0) {
			MemorySegment.copy(this.flags, 0, nextFlags, 0, (long) this.count * Integer.BYTES);
			MemorySegment.copy(this.bounds, 0, nextBounds, 0, (long) this.count * 6 * Double.BYTES);
			MemorySegment.copy(this.holderBounds, 0, nextHolder, 0, (long) this.count * 6 * Double.BYTES);
		}
		if (this.candidateArena != null) {
			this.candidateArena.close();
		}
		this.candidateArena = next;
		this.flags = nextFlags;
		this.bounds = nextBounds;
		this.holderBounds = nextHolder;
		this.admitted = nextAdmitted;
		this.capacity = newCapacity;
	}

	private static void writeBox(MemorySegment target, int index, VulkanicGalBridge.WorldAabbRecord box) {
		long base = (long) index * 6;
		target.setAtIndex(ValueLayout.JAVA_DOUBLE, base, box == null ? 0.0 : box.minX());
		target.setAtIndex(ValueLayout.JAVA_DOUBLE, base + 1, box == null ? 0.0 : box.minY());
		target.setAtIndex(ValueLayout.JAVA_DOUBLE, base + 2, box == null ? 0.0 : box.minZ());
		target.setAtIndex(ValueLayout.JAVA_DOUBLE, base + 3, box == null ? 0.0 : box.maxX());
		target.setAtIndex(ValueLayout.JAVA_DOUBLE, base + 4, box == null ? 0.0 : box.maxY());
		target.setAtIndex(ValueLayout.JAVA_DOUBLE, base + 5, box == null ? 0.0 : box.maxZ());
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		try {
			DESTROY.invokeExact(this.handle);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Failed to destroy Rust entity shadow query", throwable);
		} finally {
			if (this.candidateArena != null) {
				this.candidateArena.close();
			}
			this.arena.close();
		}
	}

}

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
		"mattmc_vulkanic_gal_entity_shadow_query_select_candidates", FunctionDescriptor.of(ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS,
			ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	private final Arena arena = Arena.ofConfined();
	private final MemorySegment handle;
	private final MemorySegment matrices = arena.allocate(32 * Float.BYTES, 8);
	private final MemorySegment camera = arena.allocate(3 * Double.BYTES, 8);
	private final EntityShadowCandidates candidates = new EntityShadowCandidates();
	private boolean closed;

	EntityShadowQuery(long contextId) {
		try {
			this.handle = (MemorySegment) CREATE.invokeExact(contextId);
		} catch (Throwable throwable) {
			this.candidates.close();
			this.arena.close();
			throw new IllegalStateException("Failed to create Rust entity shadow query", throwable);
		}
		if (this.handle.equals(MemorySegment.NULL)) {
			this.candidates.close();
			this.arena.close();
			throw new IllegalStateException("Rust entity shadow query has no context " + contextId);
		}
	}

	public void clear() { this.candidates.clear(); }

	public int size() { return this.candidates.size(); }

	public void add(int flags, net.minecraft.world.phys.AABB bounds, net.minecraft.world.phys.AABB holder) {
		this.candidates.add(flags, bounds, holder);
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
				this.candidates.size(), this.candidates.owner());
		} catch (Throwable throwable) {
			throw new IllegalStateException("Failed to query Rust entity shadow admission", throwable);
		}
		if (status != DECIDED && status != UNDECIDED) {
			throw new IllegalStateException("Rust entity shadow query failed with status " + status);
		}
		return status == DECIDED;
	}

	/** Valid after {@link #select} returned {@code true}. */
	public boolean admitted(int index) { return this.candidates.admitted(index); }

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
			this.candidates.close();
			this.arena.close();
		}
	}

}

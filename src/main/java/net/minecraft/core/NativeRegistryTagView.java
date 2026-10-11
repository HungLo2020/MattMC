package net.minecraft.core;

import it.unimi.dsi.fastutil.ints.AbstractIntList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.RandomAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable compatibility lists reading Rust-owned IDs; one lease per payload map. */
final class NativeRegistryTagView {
	private static final MethodHandle ACQUIRE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_registry_tag_view",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_registry_tag_view_release",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private final MemorySegment owner;

	private NativeRegistryTagView(MemorySegment pointer, Arena scope) {
		this.owner = pointer.reinterpret(1, scope, NativeRegistryTagView::release);
	}

	private static void release(MemorySegment owner) {
		try {
			RELEASE.invokeExact(owner);
		} catch (Throwable failure) {
			throw new IllegalStateException("Release registry tag view failed", failure);
		}
	}

	static IntList[] acquire(int context, long generation, int index, ResourceKey<? extends Registry<?>> registry, int expected) {
		try (Arena outputs = Arena.ofConfined()) {
			MemorySegment ownerOutput = outputs.allocate(ValueLayout.ADDRESS);
			MemorySegment rowsOutput = outputs.allocate(ValueLayout.ADDRESS);
			MemorySegment countOutput = outputs.allocate(ValueLayout.JAVA_LONG);
			NativeRegistrySnapshot.check(registry, (long)ACQUIRE.invokeExact(context, generation, index, ownerOutput, rowsOutput, countOutput));
			MemorySegment pointer = ownerOutput.get(ValueLayout.ADDRESS, 0);
			boolean attached = false;
			try {
				long count = countOutput.get(ValueLayout.JAVA_LONG, 0);
				if (count != expected || pointer.address() == 0) {
					throw new IllegalStateException("Registry tag view does not match published names");
				}
				Arena scope = Arena.ofAuto();
				NativeRegistryTagView lease = new NativeRegistryTagView(pointer, scope);
				attached = true;
				MemorySegment rows = rowsOutput.get(ValueLayout.ADDRESS, 0).reinterpret(count * 16, scope, null).asReadOnly();
				IntList[] lists = new IntList[expected];
				for (int tag = 0; tag < expected; tag++) {
					long size = rows.get(ValueLayout.JAVA_LONG, tag * 16L + 8);
					if (size < 0 || size > Integer.MAX_VALUE) {
						throw new IllegalStateException("Registry tag view member count is invalid");
					}
					MemorySegment members = rows.get(ValueLayout.ADDRESS, tag * 16L).reinterpret(size * 4, scope, null).asReadOnly();
					lists[tag] = IntLists.unmodifiable(new Members(lease, members, (int)size));
				}
				Reference.reachabilityFence(lease);
				return lists;
			} finally {
				if (!attached) {
					release(pointer);
				}
			}
		} catch (Throwable failure) {
			if (failure instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (failure instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Acquire registry tag view failed", failure);
		}
	}

	private static final class Members extends AbstractIntList implements RandomAccess {
		private final NativeRegistryTagView lease;
		private final MemorySegment members;
		private final int size;

		Members(NativeRegistryTagView lease, MemorySegment members, int size) {
			this.lease = lease;
			this.members = members;
			this.size = size;
		}

		@Override
		public int size() {
			return this.size;
		}

		@Override
		public int getInt(int index) {
			this.ensureRestrictedIndex(index);
			try {
				return this.members.get(ValueLayout.JAVA_INT_UNALIGNED, index * 4L);
			} finally {
				Reference.reachabilityFence(this.lease);
			}
		}
	}
}

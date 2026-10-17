package net.minecraft.tags;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.NativeLog;

/** Bindings for Rust's tag loader (`content/tags`). */
final class NativeTags {
	private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
	private static final MethodHandle LOAD = handle("tags_load", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_tags_release",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
	private static final MethodHandle PLAN = handle("tags_plan", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle RESOLVE = handle("tags_resolve", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle BUFFER_FREE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_buffer_free",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

	static {
		NativeLog.ensureRegistered();
	}

	private NativeTags() {
	}

	private static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.of(result, arguments));
	}

	private static byte[] take(MemorySegment pointer, long length) throws Throwable {
		MemorySegment buffer = pointer.get(ValueLayout.ADDRESS, 0).reinterpret(length);
		try {
			return buffer.toArray(ValueLayout.JAVA_BYTE);
		} finally {
			BUFFER_FREE.invokeExact(buffer, length);
		}
	}

	/** Collects tag file records for {@link #load}. */
	static final class Records {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		private void text(String text) {
			this.bytes(text.getBytes(StandardCharsets.UTF_8));
		}

		private void bytes(byte[] bytes) {
			int length = bytes.length;
			this.out.write(length);
			this.out.write(length >>> 8);
			this.out.write(length >>> 16);
			this.out.write(length >>> 24);
			this.out.write(bytes, 0, length);
		}

		void add(ResourceLocation file, ResourceLocation id, String pack, byte[] contents) {
			this.text(file.toString());
			this.text(id.getNamespace());
			this.text(id.getPath());
			this.text(pack);
			this.bytes(contents);
		}
	}

	static long load(Records records) {
		byte[] bytes = records.out.toByteArray();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment input = bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
			long handle = (long)LOAD.invokeExact(input, (long)bytes.length);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected tag files");
			}
			return handle;
		} catch (Throwable throwable) {
			throw throwable instanceof IllegalStateException state ? state : new IllegalStateException("Load tags failed", throwable);
		}
	}

	static void release(long handle) {
		try {
			RELEASE.invokeExact(handle);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Release tags failed", throwable);
		}
	}

	/** One element lookup the build makes, in call order. */
	record Lookup(ResourceLocation id, boolean required) {
	}

	/** The lookups in call order; a self-referencing cycle check is Java's endless recursion. */
	static List<Lookup> plan(long handle) {
		byte[] bytes;
		int status;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			status = (int)PLAN.invokeExact(handle, pointer, length);
			if (status < 0) {
				throw new IllegalStateException("Plan tags failed: " + status);
			}
			bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable throwable) {
			throw throwable instanceof IllegalStateException state ? state : new IllegalStateException("Plan tags failed", throwable);
		}
		if (status == 1) {
			throw new StackOverflowError("Tag dependency check recursed without end at " + new String(bytes, StandardCharsets.UTF_8));
		}
		List<Lookup> lookups = new ArrayList<>();
		MemorySegment buffer = MemorySegment.ofArray(bytes);
		int at = 0;
		while (at < bytes.length) {
			int namespaceLength = buffer.get(INT, at);
			String namespace = new String(bytes, at + 4, namespaceLength, StandardCharsets.UTF_8);
			at += 4 + namespaceLength;
			int pathLength = buffer.get(INT, at);
			String path = new String(bytes, at + 4, pathLength, StandardCharsets.UTF_8);
			at += 4 + pathLength;
			lookups.add(new Lookup(ResourceLocation.fromNamespaceAndPath(namespace, path), bytes[at] != 0));
			at++;
		}
		return lookups;
	}

	/** One built tag: its values' lookup indices, or the entries it could not resolve. */
	record Built(ResourceLocation id, int[] values, List<String> missing) {
	}

	static List<Built> resolve(long handle, boolean[] found) {
		byte[] bytes;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment flags = arena.allocate(Math.max(1, found.length));
			for (int index = 0; index < found.length; index++) {
				flags.set(ValueLayout.JAVA_BYTE, index, found[index] ? (byte)1 : (byte)0);
			}
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)RESOLVE.invokeExact(handle, flags, (long)found.length, pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Resolve tags failed: " + status);
			}
			bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable throwable) {
			throw throwable instanceof IllegalStateException state ? state : new IllegalStateException("Resolve tags failed", throwable);
		}
		List<Built> built = new ArrayList<>();
		MemorySegment buffer = MemorySegment.ofArray(bytes);
		int at = 0;
		while (at < bytes.length) {
			int namespaceLength = buffer.get(INT, at);
			String namespace = new String(bytes, at + 4, namespaceLength, StandardCharsets.UTF_8);
			at += 4 + namespaceLength;
			int pathLength = buffer.get(INT, at);
			String path = new String(bytes, at + 4, pathLength, StandardCharsets.UTF_8);
			at += 4 + pathLength;
			boolean ok = bytes[at] != 0;
			int count = buffer.get(INT, at + 1);
			at += 5;
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
			if (ok) {
				int[] values = new int[count];
				for (int index = 0; index < count; index++) {
					values[index] = buffer.get(INT, at);
					at += 4;
				}
				built.add(new Built(id, values, List.of()));
			} else {
				List<String> missing = new ArrayList<>(count);
				for (int index = 0; index < count; index++) {
					int size = buffer.get(INT, at);
					missing.add(new String(bytes, at + 4, size, StandardCharsets.UTF_8));
					at += 4 + size;
				}
				built.add(new Built(id, null, missing));
			}
		}
		return built;
	}
}

package net.minecraft.server.packs;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.NativeLog;
import org.jetbrains.annotations.Nullable;

/**
 * Bindings for Rust's packs and resource managers (`assets/packs`,
 * `assets/resources`). Pack and manager handles are released by their Java
 * owners' cleaners; entry references are opaque `long`s.
 */
public final class NativePacks {
	public static final int FLAG_RUNNING_IN_IDE = 1;
	public static final int FLAG_VALIDATE_PATH_CASE = 1 << 1;
	private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
	private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED;
	private static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT_UNALIGNED;
	private static final int ERROR_CAPACITY = 1024;

	private static final MethodHandle OPEN_DIRECTORY = handle("pack_open_directory", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT);
	private static final MethodHandle OPEN_ZIP = handle("pack_open_zip", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT);
	private static final MethodHandle OPEN_ARCHIVE_DIRECTORY = handle("pack_open_archive_directory", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
	private static final MethodHandle OPEN_VANILLA = handle("pack_open_vanilla", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT);
	private static final MethodHandle PACK_RELEASE = voidHandle("pack_release", ValueLayout.JAVA_LONG);
	private static final MethodHandle PACK_CLOSE = voidHandle("pack_close", ValueLayout.JAVA_LONG);
	private static final MethodHandle PACK_ROOT_RESOURCE = handle("pack_root_resource", ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle PACK_RESOURCE = handle("pack_resource", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS);
	private static final MethodHandle PACK_NAMESPACES = handle("pack_namespaces", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle PACK_LIST = handle("pack_list", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle PACK_READ = handle("pack_read", ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle BUFFER_FREE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_buffer_free",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle RESOURCES_CREATE = handle("resources_create", ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
	private static final MethodHandle RESOURCES_RELEASE = voidHandle("resources_release", ValueLayout.JAVA_LONG);
	private static final MethodHandle RESOURCES_NAMESPACES = handle("resources_namespaces", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle RESOURCES_GET = handle("resources_get", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
	private static final MethodHandle RESOURCES_STACK_METADATA = handle("resources_stack_metadata", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS);
	private static final MethodHandle RESOURCES_STACK = handle("resources_stack", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS);
	private static final MethodHandle RESOURCES_LIST = handle("resources_list", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle RESOURCES_LIST_STACKS = handle("resources_list_stacks", ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

	private static final MethodHandle DISCOVER = handle("pack_discover", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle DETECT = handle("pack_detect", ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle DISCOVER_ARCHIVE = handle("pack_discover_archive", ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle INSERT_INDEX = handle("pack_insert_index", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle REBUILD_SELECTED = handle("pack_rebuild_selected", ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

	/** Filter checks Rust asks about, by manager token. */
	private static final Map<Long, FilterCheck> FILTERS = new ConcurrentHashMap<>();
	private static final AtomicLong NEXT_TOKEN = new AtomicLong(1L);

	static {
		NativeLog.ensureRegistered();
		try {
			MethodHandle target = MethodHandles.lookup().findStatic(NativePacks.class, "filter",
				MethodType.methodType(int.class, long.class, int.class, int.class, MemorySegment.class, long.class));
			MemorySegment stub = Linker.nativeLinker().upcallStub(target, FunctionDescriptor.of(ValueLayout.JAVA_INT,
				ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), Arena.global());
			MethodHandle register = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_resources_register_filter",
				FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			register.invokeExact(stub);
		} catch (Throwable throwable) {
			throw new ExceptionInInitializerError(throwable);
		}
	}

	private NativePacks() {
	}

	private static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.of(result, arguments));
	}

	private static MethodHandle voidHandle(String name, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_" + name, FunctionDescriptor.ofVoid(arguments));
	}

	private static IllegalStateException failure(String operation, Throwable throwable) {
		return throwable instanceof IllegalStateException state ? state : new IllegalStateException(operation + " failed", throwable);
	}

	/** A UTF-8 argument in a confined arena. */
	private static MemorySegment utf8(Arena arena, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
	}

	private static long length(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

	/** Takes an owned native buffer: copies it out and frees it. */
	private static byte[] take(MemorySegment pointer, long length) throws Throwable {
		MemorySegment buffer = pointer.get(ValueLayout.ADDRESS, 0).reinterpret(length);
		try {
			return buffer.toArray(ValueLayout.JAVA_BYTE);
		} finally {
			BUFFER_FREE.invokeExact(buffer, length);
		}
	}

	// -- packs ---------------------------------------------------------------

	public static long openDirectory(String id, String path, List<String> overlays, int flags) {
		try (Arena arena = Arena.ofConfined()) {
			String joined = String.join("\n", overlays);
			long handle = (long)OPEN_DIRECTORY.invokeExact(utf8(arena, id), length(id), utf8(arena, path), length(path), utf8(arena, joined),
				length(joined), flags);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected directory pack " + id);
			}
			return handle;
		} catch (Throwable throwable) {
			throw failure("Open directory pack", throwable);
		}
	}

	public static long openZip(String id, String path, List<String> overlays, int flags) {
		try (Arena arena = Arena.ofConfined()) {
			String joined = String.join("\n", overlays);
			long handle = (long)OPEN_ZIP.invokeExact(utf8(arena, id), length(id), utf8(arena, path), length(path), utf8(arena, joined),
				length(joined), flags);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected zip pack " + id);
			}
			return handle;
		} catch (Throwable throwable) {
			throw failure("Open zip pack", throwable);
		}
	}

	/** A directory pack inside an archive (`prefix` empty or ending in `/`). */
	public static long openArchiveDirectory(String id, String archive, String prefix, List<String> overlays, int flags) {
		try (Arena arena = Arena.ofConfined()) {
			String joined = String.join("\n", overlays);
			long handle = (long)OPEN_ARCHIVE_DIRECTORY.invokeExact(utf8(arena, id), length(id), utf8(arena, archive), length(archive),
				utf8(arena, prefix), length(prefix), utf8(arena, joined), length(joined), flags);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected archive directory pack " + id);
			}
			return handle;
		} catch (Throwable throwable) {
			throw failure("Open archive directory pack", throwable);
		}
	}

	/** One vanilla pack root: role `R` (root files) or a pack type ordinal digit, and its location. */
	public record VanillaRoot(char role, boolean archive, String path, String prefix) {
	}

	public static long openVanilla(String id, List<VanillaRoot> roots, List<String> namespaces, int flags) {
		try (Arena arena = Arena.ofConfined()) {
			java.io.ByteArrayOutputStream records = new java.io.ByteArrayOutputStream();
			for (VanillaRoot root : roots) {
				records.write(root.role());
				records.write(root.archive() ? 'Z' : 'D');
				writeText(records, root.path());
				writeText(records, root.prefix());
			}
			byte[] bytes = records.toByteArray();
			MemorySegment buffer = bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
			String joined = String.join("\n", namespaces);
			long handle = (long)OPEN_VANILLA.invokeExact(utf8(arena, id), length(id), buffer, (long)bytes.length, utf8(arena, joined),
				length(joined), flags);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected vanilla pack " + id);
			}
			return handle;
		} catch (Throwable throwable) {
			throw failure("Open vanilla pack", throwable);
		}
	}

	private static void writeText(java.io.ByteArrayOutputStream out, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		int length = bytes.length;
		out.write(length);
		out.write(length >>> 8);
		out.write(length >>> 16);
		out.write(length >>> 24);
		out.write(bytes, 0, length);
	}

	public static void releasePack(long handle) {
		try {
			PACK_RELEASE.invokeExact(handle);
		} catch (Throwable throwable) {
			throw failure("Release pack", throwable);
		}
	}

	public static void closePack(long handle) {
		try {
			PACK_CLOSE.invokeExact(handle);
		} catch (Throwable throwable) {
			throw failure("Close pack", throwable);
		}
	}

	/** A located entry, or null when absent. */
	@Nullable
	public static Long rootResource(long handle, String joined) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment error = arena.allocate(ERROR_CAPACITY);
			long result = (long)PACK_ROOT_RESOURCE.invokeExact(handle, utf8(arena, joined), length(joined), out, error, (long)ERROR_CAPACITY);
			if (result < 0L) {
				long size = Math.min(-result, ERROR_CAPACITY);
				throw new IllegalArgumentException(new String(error.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8));
			}
			return result == 1L ? out.get(ValueLayout.JAVA_LONG, 0) : null;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw failure("Find root pack resource", throwable);
		}
	}

	/** A located entry, or null when absent. */
	@Nullable
	public static Long resource(long handle, int type, String namespace, String path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.JAVA_LONG);
			int found = (int)PACK_RESOURCE.invokeExact(handle, type, utf8(arena, namespace), length(namespace), utf8(arena, path), length(path), out);
			return found == 1 ? out.get(ValueLayout.JAVA_LONG, 0) : null;
		} catch (Throwable throwable) {
			throw failure("Find pack resource", throwable);
		}
	}

	public static List<String> packNamespaces(long handle, int type) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)PACK_NAMESPACES.invokeExact(handle, type, pointer, length);
			if (status != 0) {
				throw new IllegalStateException("List pack namespaces failed: " + status);
			}
			return lines(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)));
		} catch (Throwable throwable) {
			throw failure("List pack namespaces", throwable);
		}
	}

	private static List<String> lines(byte[] bytes) {
		if (bytes.length == 0) {
			return List.of();
		}
		return List.of(new String(bytes, StandardCharsets.UTF_8).split("\n"));
	}

	/** One listed pack file: its path under the namespace and entry. */
	public interface PackListOutput {
		void accept(String path, long entry);
	}

	public static void listPack(long handle, int type, String namespace, String directory, PackListOutput output) {
		byte[] bytes;
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)PACK_LIST.invokeExact(handle, type, utf8(arena, namespace), length(namespace), utf8(arena, directory),
				length(directory), pointer, length);
			if (status != 0) {
				throw new IllegalStateException("List pack failed: " + status);
			}
			bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (Throwable throwable) {
			throw failure("List pack", throwable);
		}
		MemorySegment buffer = MemorySegment.ofArray(bytes);
		long at = 0L;
		while (at < bytes.length) {
			int pathLength = buffer.get(INT, at);
			String path = new String(bytes, (int)at + 4, pathLength, StandardCharsets.UTF_8);
			at += 4L + pathLength;
			long entry = buffer.get(LONG, at);
			at += 8L;
			output.accept(path, entry);
		}
	}

	/** Reads an entry; `relative` is `namespace/path`, or the root file path. */
	public static byte[] read(long handle, long entry, String relative) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment error = arena.allocate(ERROR_CAPACITY);
			long status = (long)PACK_READ.invokeExact(handle, entry, utf8(arena, relative), length(relative), pointer, length, error,
				(long)ERROR_CAPACITY);
			if (status < 0L) {
				long size = Math.min(-status, ERROR_CAPACITY);
				throw new IOException(new String(error.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8) + ": " + relative);
			}
			return take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw failure("Read pack entry", throwable);
		}
	}

	// -- discovery and selection ---------------------------------------------

	public static final int DETECT_NONE = 0;
	public static final int DETECT_DIRECTORY = 1;
	public static final int DETECT_ZIP = 2;
	public static final int DETECT_LINK = 3;
	public static final int DETECT_KNOWN_DIRECTORY = 4;

	/** A folder entry: its kind and the kind's path (link target or pack path). */
	public record Detection(int kind, String path, String target) {
	}

	private static List<Detection> detections(byte[] bytes) {
		List<Detection> list = new ArrayList<>();
		MemorySegment buffer = MemorySegment.ofArray(bytes);
		long at = 0L;
		while (at < bytes.length) {
			int kind = bytes[(int)at];
			int pathLength = buffer.get(INT, at + 1L);
			String path = new String(bytes, (int)at + 5, pathLength, StandardCharsets.UTF_8);
			at += 5L + pathLength;
			int targetLength = buffer.get(INT, at);
			String target = new String(bytes, (int)at + 4, targetLength, StandardCharsets.UTF_8);
			at += 4L + targetLength;
			list.add(new Detection(kind, path, target));
		}
		return list;
	}

	/** `FolderRepositorySource.discoverPacks`' entries; IOException when the folder cannot be listed. */
	public static List<Detection> discover(String folder) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment error = arena.allocate(ERROR_CAPACITY);
			long status = (long)DISCOVER.invokeExact(utf8(arena, folder), length(folder), pointer, length, error, (long)ERROR_CAPACITY);
			if (status < 0L) {
				long size = Math.min(-status, ERROR_CAPACITY);
				throw new IOException(new String(error.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8));
			}
			return detections(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)));
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw failure("Discover packs", throwable);
		}
	}

	public static final int MODE_DETECT = 0;
	public static final int MODE_LINK_TARGET = 1;
	public static final int MODE_DIRECTORY_PACK = 2;

	/** One path's detection: links reported, a validated link's target, or a validated directory's pack check. */
	public static Detection detect(String path, int mode) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)DETECT.invokeExact(utf8(arena, path), length(path), mode, pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Detect pack failed: " + status);
			}
			return detections(take(pointer, length.get(ValueLayout.JAVA_LONG, 0))).getFirst();
		} catch (Throwable throwable) {
			throw failure("Detect pack", throwable);
		}
	}

	/** A folder inside an archive: each child's name, whether it is a directory and holds a `pack.mcmeta`. */
	public record ArchiveChild(String name, boolean directory, boolean hasPackMeta) {
	}

	@Nullable
	public static List<ArchiveChild> discoverArchive(String archive, String prefix) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)DISCOVER_ARCHIVE.invokeExact(utf8(arena, archive), length(archive), utf8(arena, prefix), length(prefix), pointer, length);
			if (status != 0) {
				return null;
			}
			byte[] bytes = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
			MemorySegment buffer = MemorySegment.ofArray(bytes);
			List<ArchiveChild> children = new ArrayList<>();
			long at = 0L;
			while (at < bytes.length) {
				int size = buffer.get(INT, at);
				String name = new String(bytes, (int)at + 4, size, StandardCharsets.UTF_8);
				int flags = bytes[(int)at + 4 + size];
				at += 5L + size;
				children.add(new ArchiveChild(name, (flags & 1) != 0, (flags & 2) != 0));
			}
			return children;
		} catch (Throwable throwable) {
			throw failure("Discover archive packs", throwable);
		}
	}

	/** `Pack.Position.insert`'s index for entries' (fixed, position ordinal) pairs. */
	public static int insertIndex(boolean[] fixed, int[] positions, int position, boolean opposite) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment configs = arena.allocate(Math.max(2, fixed.length * 2L));
			for (int index = 0; index < fixed.length; index++) {
				configs.set(ValueLayout.JAVA_BYTE, index * 2L, fixed[index] ? (byte)1 : (byte)0);
				configs.set(ValueLayout.JAVA_BYTE, index * 2L + 1L, (byte)positions[index]);
			}
			return (int)(long)INSERT_INDEX.invokeExact(configs, (long)fixed.length, position, opposite ? 1 : 0);
		} catch (Throwable throwable) {
			throw failure("Pack insert index", throwable);
		}
	}

	/** One available pack for selection: id and `PackSelectionConfig`. */
	public record Selectable(String id, boolean required, int position, boolean fixed) {
	}

	/** `PackRepository.rebuildSelected`: indices into `available`. */
	public static int[] rebuildSelected(List<Selectable> available, List<String> requested) {
		java.io.ByteArrayOutputStream records = new java.io.ByteArrayOutputStream();
		for (Selectable pack : available) {
			writeText(records, pack.id());
			records.write(pack.required() ? 1 : 0);
			records.write(pack.position());
			records.write(pack.fixed() ? 1 : 0);
		}
		byte[] bytes = records.toByteArray();
		String joined = String.join("\n", requested);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			MemorySegment input = bytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
			int status = (int)REBUILD_SELECTED.invokeExact(input, (long)bytes.length, utf8(arena, joined), length(joined), pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Rebuild selected packs failed: " + status);
			}
			byte[] result = take(pointer, length.get(ValueLayout.JAVA_LONG, 0));
			int[] indices = new int[result.length / 4];
			MemorySegment buffer = MemorySegment.ofArray(result);
			for (int index = 0; index < indices.length; index++) {
				indices[index] = buffer.get(INT, index * 4L);
			}
			return indices;
		} catch (Throwable throwable) {
			throw failure("Rebuild selected packs", throwable);
		}
	}

	// -- resource managers ---------------------------------------------------

	/** Answers Rust's filter questions for one manager's packs. */
	public interface FilterCheck {
		boolean namespaceFiltered(int pack, String namespace);

		boolean pathFiltered(int pack, String path);
	}

	private static int filter(long token, int pack, int kind, MemorySegment text, long length) {
		FilterCheck check = FILTERS.get(token);
		if (check == null) {
			return 0;
		}
		String value = new String(text.reinterpret(length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
		return (kind == 0 ? check.namespaceFiltered(pack, value) : check.pathFiltered(pack, value)) ? 1 : 0;
	}

	/** Registers a manager's filters; returns its token (0: none). */
	public static long registerFilters(@Nullable FilterCheck check) {
		if (check == null) {
			return 0L;
		}
		long token = NEXT_TOKEN.getAndIncrement();
		FILTERS.put(token, check);
		return token;
	}

	public static void unregisterFilters(long token) {
		if (token != 0L) {
			FILTERS.remove(token);
		}
	}

	public static long createManager(int type, long[] packs, boolean[] hasFilter, long token) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment handles = arena.allocate(ValueLayout.JAVA_LONG, Math.max(1, packs.length));
			MemorySegment flags = arena.allocate(Math.max(1, packs.length));
			for (int index = 0; index < packs.length; index++) {
				handles.setAtIndex(ValueLayout.JAVA_LONG, index, packs[index]);
				flags.set(ValueLayout.JAVA_BYTE, index, hasFilter[index] ? (byte)1 : (byte)0);
			}
			long handle = (long)RESOURCES_CREATE.invokeExact(type, handles, flags, (long)packs.length, token);
			if (handle == 0L) {
				throw new IllegalStateException("Rust rejected resource manager packs");
			}
			return handle;
		} catch (Throwable throwable) {
			throw failure("Create resource manager", throwable);
		}
	}

	public static void releaseManager(long handle) {
		try {
			RESOURCES_RELEASE.invokeExact(handle);
		} catch (Throwable throwable) {
			throw failure("Release resource manager", throwable);
		}
	}

	public static List<String> managerNamespaces(long handle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)RESOURCES_NAMESPACES.invokeExact(handle, pointer, length);
			if (status != 0) {
				throw new IllegalStateException("List namespaces failed: " + status);
			}
			return lines(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)));
		} catch (Throwable throwable) {
			throw failure("List resource namespaces", throwable);
		}
	}

	/** A located resource: pack index, stack position and entry. */
	public record Located(int pack, int position, long entry) {
	}

	@Nullable
	public static Located getResource(long handle, String namespace, String path) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(16, 8);
			int found = (int)RESOURCES_GET.invokeExact(handle, utf8(arena, namespace), length(namespace), utf8(arena, path), length(path), out);
			return found == 1 ? new Located(out.get(ValueLayout.JAVA_INT, 0), out.get(ValueLayout.JAVA_INT, 4), out.get(ValueLayout.JAVA_LONG, 8)) : null;
		} catch (Throwable throwable) {
			throw failure("Find resource", throwable);
		}
	}

	@Nullable
	public static Located stackMetadata(long handle, String namespace, String path, int position) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(16, 8);
			int found = (int)RESOURCES_STACK_METADATA.invokeExact(handle, utf8(arena, namespace), length(namespace), utf8(arena, path),
				length(path), position, out);
			return found == 1 ? new Located(out.get(ValueLayout.JAVA_INT, 0), position, out.get(ValueLayout.JAVA_LONG, 8)) : null;
		} catch (Throwable throwable) {
			throw failure("Find resource metadata", throwable);
		}
	}

	/** One listed file: pack index and entry, and its sidecar (`metadataPack` -1 for none). */
	public record Source(int pack, long entry, int metadataPack, long metadataEntry) {
	}

	/** Receives each listed location with its sources, lowest pack first. */
	public interface ListOutput {
		void accept(String namespace, String path, List<Source> sources);
	}

	public static void resourceStack(long handle, String namespace, String path, ListOutput output) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)RESOURCES_STACK.invokeExact(handle, utf8(arena, namespace), length(namespace), utf8(arena, path), length(path),
				pointer, length);
			if (status != 0) {
				throw new IllegalStateException("Resource stack failed: " + status);
			}
			decode(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)), output);
		} catch (Throwable throwable) {
			throw failure("Find resource stack", throwable);
		}
	}

	public static void listResources(long handle, String directory, boolean stacks, ListOutput output) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
			MemorySegment length = arena.allocate(ValueLayout.JAVA_LONG);
			int status = stacks
				? (int)RESOURCES_LIST_STACKS.invokeExact(handle, utf8(arena, directory), length(directory), pointer, length)
				: (int)RESOURCES_LIST.invokeExact(handle, utf8(arena, directory), length(directory), pointer, length);
			if (status != 0) {
				throw new IllegalStateException("List resources failed: " + status);
			}
			decode(take(pointer, length.get(ValueLayout.JAVA_LONG, 0)), output);
		} catch (Throwable throwable) {
			throw failure("List resources", throwable);
		}
	}

	private static void decode(byte[] bytes, ListOutput output) {
		if (bytes.length == 0) {
			return;
		}
		MemorySegment buffer = MemorySegment.ofArray(bytes);
		long at = 0L;
		int namespaceCount = buffer.get(INT, at);
		at += 4L;
		String[] namespaces = new String[namespaceCount];
		for (int index = 0; index < namespaceCount; index++) {
			int size = buffer.get(INT, at);
			namespaces[index] = new String(bytes, (int)at + 4, size, StandardCharsets.UTF_8);
			at += 4L + size;
		}
		int count = buffer.get(INT, at);
		at += 4L;
		for (int index = 0; index < count; index++) {
			String namespace = namespaces[buffer.get(INT, at)];
			int pathLength = buffer.get(INT, at + 4L);
			String path = new String(bytes, (int)at + 8, pathLength, StandardCharsets.UTF_8);
			at += 8L + pathLength;
			int sourceCount = buffer.get(INT, at);
			at += 4L;
			List<Source> sources = new ArrayList<>(sourceCount);
			for (int source = 0; source < sourceCount; source++) {
				int pack = Short.toUnsignedInt(buffer.get(SHORT, at));
				long entry = buffer.get(LONG, at + 4L);
				int metadataPack = buffer.get(INT, at + 12L);
				long metadataEntry = buffer.get(LONG, at + 16L);
				sources.add(new Source(pack, entry, metadataPack, metadataEntry));
				at += 24L;
			}
			output.accept(namespace, path, sources);
		}
	}
}

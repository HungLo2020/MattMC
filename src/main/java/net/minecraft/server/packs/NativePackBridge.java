package net.minecraft.server.packs;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;

final class NativePackBridge {
	static final int OK = 0;
	static final int OUTPUT_TOO_SMALL = -4;
	static final int INVALID_HANDLE = -5;
	static final int INVALID_PATH = -6;
	private static final long OPEN_RESULT_SIZE = 40L;
	private static final long OPEN_STATUS = 0L;
	private static final long OPEN_ERROR_KIND = 4L;
	private static final long OPEN_HANDLE = 8L;
	private static final long OPEN_ENTRIES_INDEXED = 16L;
	private static final long OPEN_NAMESPACES_INDEXED = 24L;
	private static final long OPEN_INDEX_NANOS = 32L;
	private static final long RESULT_SIZE = 56L;
	private static final long RESULT_STATUS = 0L;
	private static final long RESULT_ERROR_KIND = 4L;
	private static final long RESULT_PRESENT = 8L;
	private static final long RESULT_OUTPUT_LEN = 16L;
	private static final long RESULT_ENTRY_COUNT = 24L;
	private static final long RESULT_NAMESPACE_COUNT = 32L;
	private static final long RESULT_DURATION_NANOS = 40L;
	private static final long RESULT_BYTES_RETURNED = 48L;
	private static final long COUNTERS_SIZE = 64L;
	private static final FunctionDescriptor OPEN_DIRECTORY_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor OPEN_ZIP_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor CLOSE_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final FunctionDescriptor LIST_NAMESPACES_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor LIST_RESOURCES_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor EXISTS_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor ROOT_EXISTS_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor READ_RESOURCE_DESCRIPTOR = LIST_RESOURCES_DESCRIPTOR;
	private static final FunctionDescriptor READ_ROOT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor COUNTERS_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
	private static final MethodHandle OPEN_DIRECTORY = downcall("mattmc_pack_open_directory", OPEN_DIRECTORY_DESCRIPTOR);
	private static final MethodHandle OPEN_ZIP = downcall("mattmc_pack_open_zip", OPEN_ZIP_DESCRIPTOR);
	private static final MethodHandle CLOSE = downcall("mattmc_pack_close", CLOSE_DESCRIPTOR);
	private static final MethodHandle LIST_NAMESPACES = downcall("mattmc_pack_list_namespaces", LIST_NAMESPACES_DESCRIPTOR);
	private static final MethodHandle LIST_RESOURCES = downcall("mattmc_pack_list_resources", LIST_RESOURCES_DESCRIPTOR);
	private static final MethodHandle EXISTS = downcall("mattmc_pack_resource_exists", EXISTS_DESCRIPTOR);
	private static final MethodHandle ROOT_EXISTS = downcall("mattmc_pack_root_resource_exists", ROOT_EXISTS_DESCRIPTOR);
	private static final MethodHandle READ_RESOURCE = downcall("mattmc_pack_read_resource", READ_RESOURCE_DESCRIPTOR);
	private static final MethodHandle READ_ROOT = downcall("mattmc_pack_read_root_resource", READ_ROOT_DESCRIPTOR);
	private static final MethodHandle COUNTERS = downcall("mattmc_pack_counters", COUNTERS_DESCRIPTOR);

	private NativePackBridge() {
	}

	static OpenStats openDirectory(Path path) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			byte[] pathBytes = path.toString().getBytes(StandardCharsets.UTF_8);
			MemorySegment resultSegment = arena.allocate(OPEN_RESULT_SIZE, 8);
			int status = (int)OPEN_DIRECTORY.invokeExact(
				arena.allocateFrom(ValueLayout.JAVA_BYTE, pathBytes),
				(long)pathBytes.length,
				resultSegment
			);
			OpenStats stats = readOpenStats(resultSegment, status);
			check("Open directory pack", stats.status());
			return stats;
		} catch (Throwable throwable) {
			throw nativeFailure("Open directory pack", throwable);
		}
	}

	static OpenStats openZip(Path path, String prefix) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			byte[] pathBytes = path.toString().getBytes(StandardCharsets.UTF_8);
			byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
			MemorySegment prefixSegment = prefixBytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, prefixBytes);
			MemorySegment resultSegment = arena.allocate(OPEN_RESULT_SIZE, 8);
			int status = (int)OPEN_ZIP.invokeExact(
				arena.allocateFrom(ValueLayout.JAVA_BYTE, pathBytes),
				(long)pathBytes.length,
				prefixSegment,
				(long)prefixBytes.length,
				resultSegment
			);
			OpenStats stats = readOpenStats(resultSegment, status);
			check("Open zip pack", stats.status());
			return stats;
		} catch (Throwable throwable) {
			throw nativeFailure("Open zip pack", throwable);
		}
	}

	static void close(long handle) throws IOException {
		try {
			check("Close pack", (int)CLOSE.invokeExact(handle));
		} catch (Throwable throwable) {
			throw nativeFailure("Close pack", throwable);
		}
	}

	static List<String> listNamespaces(long handle, PackType type) throws IOException {
		return decodeStringTape(callOutput("listNamespaces", (arena, output, capacity, result) -> {
			byte[] typeBytes = type.getDirectory().getBytes(StandardCharsets.UTF_8);
			return (int)LIST_NAMESPACES.invokeExact(
				handle,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, typeBytes),
				(long)typeBytes.length,
				output,
				capacity,
				result
			);
		}, "List namespaces"));
	}

	static List<String> listResources(long handle, PackType type, String namespace, String prefix) throws IOException {
		return decodeStringTape(callOutput("listResources", (arena, output, capacity, result) -> {
			byte[] typeBytes = type.getDirectory().getBytes(StandardCharsets.UTF_8);
			byte[] namespaceBytes = namespace.getBytes(StandardCharsets.UTF_8);
			byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
			MemorySegment prefixSegment = prefixBytes.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, prefixBytes);
			return (int)LIST_RESOURCES.invokeExact(
				handle,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, typeBytes),
				(long)typeBytes.length,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, namespaceBytes),
				(long)namespaceBytes.length,
				prefixSegment,
				(long)prefixBytes.length,
				output,
				capacity,
				result
			);
		}, "List resources"));
	}

	static boolean exists(long handle, PackType type, String namespace, String path) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			byte[] typeBytes = type.getDirectory().getBytes(StandardCharsets.UTF_8);
			byte[] namespaceBytes = namespace.getBytes(StandardCharsets.UTF_8);
			byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)EXISTS.invokeExact(
				handle,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, typeBytes),
				(long)typeBytes.length,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, namespaceBytes),
				(long)namespaceBytes.length,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, pathBytes),
				(long)pathBytes.length,
				resultSegment
			);
			Result result = readResult(resultSegment, status);
			check("Resource exists", result.status());
			ResourcePackDiagnostics.nativeResult("exists", result);
			return result.present();
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Resource exists", throwable);
		}
	}

	static boolean rootExists(long handle, String path) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
			MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
			int status = (int)ROOT_EXISTS.invokeExact(
				handle,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, pathBytes),
				(long)pathBytes.length,
				resultSegment
			);
			Result result = readResult(resultSegment, status);
			check("Root resource exists", result.status());
			ResourcePackDiagnostics.nativeResult("rootExists", result);
			return result.present();
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Root resource exists", throwable);
		}
	}

	static byte[] readResource(long handle, PackType type, String namespace, String path) throws IOException {
		return callOutput("readResource", (arena, output, capacity, result) -> {
			byte[] typeBytes = type.getDirectory().getBytes(StandardCharsets.UTF_8);
			byte[] namespaceBytes = namespace.getBytes(StandardCharsets.UTF_8);
			byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
			return (int)READ_RESOURCE.invokeExact(
				handle,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, typeBytes),
				(long)typeBytes.length,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, namespaceBytes),
				(long)namespaceBytes.length,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, pathBytes),
				(long)pathBytes.length,
				output,
				capacity,
				result
			);
		}, "Read resource");
	}

	static byte[] readRootResource(long handle, String path) throws IOException {
		return callOutput("readRootResource", (arena, output, capacity, result) -> {
			byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
			return (int)READ_ROOT.invokeExact(
				handle,
				arena.allocateFrom(ValueLayout.JAVA_BYTE, pathBytes),
				(long)pathBytes.length,
				output,
				capacity,
				result
			);
		}, "Read root resource");
	}

	static Counters counters(long handle) throws IOException {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment resultSegment = arena.allocate(COUNTERS_SIZE, 8);
			int status = (int)COUNTERS.invokeExact(handle, resultSegment);
			check("Read pack counters", status);
			return new Counters(
				resultSegment.get(ValueLayout.JAVA_LONG, 0L),
				resultSegment.get(ValueLayout.JAVA_LONG, 8L),
				resultSegment.get(ValueLayout.JAVA_LONG, 16L),
				resultSegment.get(ValueLayout.JAVA_LONG, 24L),
				resultSegment.get(ValueLayout.JAVA_LONG, 32L),
				resultSegment.get(ValueLayout.JAVA_LONG, 40L),
				resultSegment.get(ValueLayout.JAVA_LONG, 48L),
				resultSegment.get(ValueLayout.JAVA_LONG, 56L)
			);
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw nativeFailure("Read pack counters", throwable);
		}
	}

	private static byte[] callOutput(String operation, OutputCall call, String label) throws IOException {
		long capacity = 4096L;
		Result result = null;
		for (int attempt = 0; attempt < 6; attempt++) {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment output = arena.allocate(capacity, 1);
				MemorySegment resultSegment = arena.allocate(RESULT_SIZE, 8);
				int status = call.invoke(arena, output, capacity, resultSegment);
				result = readResult(resultSegment, status);
				if (result.status() == OUTPUT_TOO_SMALL && result.outputLength() > capacity) {
					capacity = result.outputLength();
					continue;
				}
				check(label, result.status());
				ResourcePackDiagnostics.nativeResult(operation, result);
				return result.present() ? output.asSlice(0, result.outputLength()).toArray(ValueLayout.JAVA_BYTE) : null;
			} catch (IOException exception) {
				throw exception;
			} catch (Throwable throwable) {
				throw nativeFailure(label, throwable);
			}
		}
		throw new IOException(label + " failed after output growth attempts; last result=" + result);
	}

	static List<String> decodeStringTape(byte[] tape) throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(tape).order(ByteOrder.LITTLE_ENDIAN);
		if (buffer.getInt() != 0x4B435052) {
			throw new IOException("Invalid native pack tape magic");
		}
		int version = Short.toUnsignedInt(buffer.getShort());
		if (version != 1) {
			throw new IOException("Unsupported native pack tape version " + version);
		}
		buffer.getShort();
		int count = buffer.getInt();
		List<String> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int length = buffer.getInt();
			byte[] bytes = new byte[length];
			buffer.get(bytes);
			values.add(new String(bytes, StandardCharsets.UTF_8));
		}
		if (buffer.hasRemaining()) {
			throw new IOException("Trailing native pack tape bytes");
		}
		return values;
	}

	private static OpenStats readOpenStats(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, OPEN_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new OpenStats(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, OPEN_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_LONG, OPEN_HANDLE),
			resultSegment.get(ValueLayout.JAVA_LONG, OPEN_ENTRIES_INDEXED),
			resultSegment.get(ValueLayout.JAVA_LONG, OPEN_NAMESPACES_INDEXED),
			resultSegment.get(ValueLayout.JAVA_LONG, OPEN_INDEX_NANOS)
		);
	}

	private static Result readResult(MemorySegment resultSegment, int callStatus) {
		int status = resultSegment.get(ValueLayout.JAVA_INT, RESULT_STATUS);
		if (status == 0 && callStatus != 0) {
			status = callStatus;
		}
		return new Result(
			status,
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_ERROR_KIND),
			resultSegment.get(ValueLayout.JAVA_INT, RESULT_PRESENT) != 0,
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_OUTPUT_LEN),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_ENTRY_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_NAMESPACE_COUNT),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_DURATION_NANOS),
			resultSegment.get(ValueLayout.JAVA_LONG, RESULT_BYTES_RETURNED)
		);
	}

	private static void check(String label, int status) throws IOException {
		if (status != OK) {
			throw new IOException(label + " failed with native pack status " + status);
		}
	}

	private static IOException nativeFailure(String label, Throwable throwable) {
		if (throwable instanceof IOException exception) {
			return exception;
		}
		return new IOException(label + " native invocation failed", throwable);
	}

	private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
	}

	record OpenStats(int status, int errorKind, long handle, long entriesIndexed, long namespacesIndexed, long indexNanos) {
	}

	record Result(
		int status,
		int errorKind,
		boolean present,
		long outputLength,
		long entryCount,
		long namespaceCount,
		long durationNanos,
		long bytesReturned
	) {
	}

	record Counters(
		long listOps,
		long existsOps,
		long readOps,
		long bytesReturned,
		long invalidPathRejections,
		long staleHandleAttempts,
		long entriesIndexed,
		long namespacesIndexed
	) {
	}

	@FunctionalInterface
	private interface OutputCall {
		int invoke(Arena arena, MemorySegment output, long capacity, MemorySegment result) throws Throwable;
	}
}

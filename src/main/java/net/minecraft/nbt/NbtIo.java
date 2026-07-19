package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.profiling.storage.StoragePerfDiagnostics;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class NbtIo {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean RUST_SHADOW_COMPARE = Boolean.getBoolean("mattmc.dev.nbtShadowCompare");
	private static final NbtValidationDiagnostics VALIDATION_DIAGNOSTICS = NbtValidationDiagnostics.create();
	private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
		StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
	};

	public static CompoundTag readCompressed(Path path, NbtAccounter nbtAccounter) throws IOException {
		try (InputStream inputStream = new FastBufferedInputStream(Files.newInputStream(path))) {
			byte[] bytes = inputStream.readAllBytes();
			long started = StoragePerfDiagnostics.start();
			CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_GZIP, nbtAccounter);
			StoragePerfDiagnostics.recordNbtRead("rust", NativeNbt.FORMAT_GZIP, bytes.length, rust.sizeInBytes(), StoragePerfDiagnostics.elapsed(started));
			VALIDATION_DIAGNOSTICS.recordRead(NativeNbt.FORMAT_GZIP, bytes.length, rust.sizeInBytes(), path);
			shadowCompareRead(bytes, NativeNbt.FORMAT_GZIP, rust);
			return rust;
		}
	}

	private static DataInputStream createDecompressorStream(InputStream inputStream) throws IOException {
		return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(inputStream)));
	}

	public static CompoundTag readCompressed(InputStream inputStream, NbtAccounter nbtAccounter) throws IOException {
		byte[] bytes;
		try (inputStream) {
			bytes = inputStream.readAllBytes();
		}
		long started = StoragePerfDiagnostics.start();
		CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_GZIP, nbtAccounter);
		StoragePerfDiagnostics.recordNbtRead("rust", NativeNbt.FORMAT_GZIP, bytes.length, rust.sizeInBytes(), StoragePerfDiagnostics.elapsed(started));
		VALIDATION_DIAGNOSTICS.recordRead(NativeNbt.FORMAT_GZIP, bytes.length, rust.sizeInBytes(), null);
		shadowCompareRead(bytes, NativeNbt.FORMAT_GZIP, rust);
		return rust;
	}

	public static void parseCompressed(Path path, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
		InputStream inputStream = Files.newInputStream(path);

		try {
			InputStream inputStream2 = new FastBufferedInputStream(inputStream);

			try {
				parseCompressed(inputStream2, streamTagVisitor, nbtAccounter);
			} catch (Throwable var9) {
				try {
					inputStream2.close();
				} catch (Throwable var8) {
					var9.addSuppressed(var8);
				}

				throw var9;
			}

			inputStream2.close();
		} catch (Throwable var10) {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (Throwable var7) {
					var10.addSuppressed(var7);
				}
			}

			throw var10;
		}

		if (inputStream != null) {
			inputStream.close();
		}
	}

	public static void parseCompressed(InputStream inputStream, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
		DataInputStream dataInputStream = createDecompressorStream(inputStream);

		try {
			parse(dataInputStream, streamTagVisitor, nbtAccounter);
		} catch (Throwable var7) {
			if (dataInputStream != null) {
				try {
					dataInputStream.close();
				} catch (Throwable var6) {
					var7.addSuppressed(var6);
				}
			}

			throw var7;
		}

		if (dataInputStream != null) {
			dataInputStream.close();
		}
	}

	public static void writeCompressed(CompoundTag compoundTag, Path path) throws IOException {
		long started = StoragePerfDiagnostics.start();
		byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_GZIP);
		StoragePerfDiagnostics.recordNbtWrite("rust", NativeNbt.FORMAT_GZIP, compoundTag.sizeInBytes(), bytes.length, StoragePerfDiagnostics.elapsed(started));
		VALIDATION_DIAGNOSTICS.recordWrite(NativeNbt.FORMAT_GZIP, compoundTag.sizeInBytes(), bytes.length, path);
		shadowCompareWrite(compoundTag, NativeNbt.FORMAT_GZIP, bytes);
		try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS))) {
			outputStream.write(bytes);
		}
	}

	public static void writeCompressed(CompoundTag compoundTag, OutputStream outputStream) throws IOException {
		long started = StoragePerfDiagnostics.start();
		byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_GZIP);
		StoragePerfDiagnostics.recordNbtWrite("rust", NativeNbt.FORMAT_GZIP, compoundTag.sizeInBytes(), bytes.length, StoragePerfDiagnostics.elapsed(started));
		VALIDATION_DIAGNOSTICS.recordWrite(NativeNbt.FORMAT_GZIP, compoundTag.sizeInBytes(), bytes.length, null);
		shadowCompareWrite(compoundTag, NativeNbt.FORMAT_GZIP, bytes);
		try (outputStream) {
			outputStream.write(bytes);
		}
	}

	public static void write(CompoundTag compoundTag, Path path) throws IOException {
		long started = StoragePerfDiagnostics.start();
		byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_RAW);
		StoragePerfDiagnostics.recordNbtWrite("rust", NativeNbt.FORMAT_RAW, compoundTag.sizeInBytes(), bytes.length, StoragePerfDiagnostics.elapsed(started));
		VALIDATION_DIAGNOSTICS.recordWrite(NativeNbt.FORMAT_RAW, compoundTag.sizeInBytes(), bytes.length, path);
		shadowCompareWrite(compoundTag, NativeNbt.FORMAT_RAW, bytes);
		try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS))) {
			outputStream.write(bytes);
		}
	}

	@Nullable
	public static CompoundTag read(Path path) throws IOException {
		if (!Files.exists(path, new LinkOption[0])) {
			return null;
		} else {
			byte[] bytes = Files.readAllBytes(path);
			long started = StoragePerfDiagnostics.start();
			CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_RAW, NbtAccounter.unlimitedHeap());
			StoragePerfDiagnostics.recordNbtRead("rust", NativeNbt.FORMAT_RAW, bytes.length, rust.sizeInBytes(), StoragePerfDiagnostics.elapsed(started));
			VALIDATION_DIAGNOSTICS.recordRead(NativeNbt.FORMAT_RAW, bytes.length, rust.sizeInBytes(), path);
			shadowCompareRead(bytes, NativeNbt.FORMAT_RAW, rust);
			return rust;
		}
	}

	public static CompoundTag read(DataInput dataInput) throws IOException {
		return read(dataInput, NbtAccounter.unlimitedHeap());
	}

	public static CompoundTag read(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		long started = StoragePerfDiagnostics.start();
		Tag tag = readUnnamedTag(dataInput, nbtAccounter);
		if (tag instanceof CompoundTag) {
			CompoundTag compoundTag = (CompoundTag)tag;
			StoragePerfDiagnostics.recordNbtRead("java", -1, -1L, compoundTag.sizeInBytes(), StoragePerfDiagnostics.elapsed(started));
			return compoundTag;
		} else {
			throw new IOException("Root tag must be a named compound tag");
		}
	}

	public static void write(CompoundTag compoundTag, DataOutput dataOutput) throws IOException {
		long started = StoragePerfDiagnostics.start();
		writeUnnamedTagWithFallback(compoundTag, dataOutput);
		StoragePerfDiagnostics.recordNbtWrite("java", -1, compoundTag.sizeInBytes(), -1L, StoragePerfDiagnostics.elapsed(started));
	}

	public static void parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
		TagType<?> tagType = TagTypes.getType(dataInput.readByte());
		if (tagType == EndTag.TYPE) {
			if (streamTagVisitor.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
				streamTagVisitor.visitEnd();
			}
		} else {
			switch (streamTagVisitor.visitRootEntry(tagType)) {
				case HALT:
				default:
					break;
				case BREAK:
					StringTag.skipString(dataInput);
					tagType.skip(dataInput, nbtAccounter);
					break;
				case CONTINUE:
					StringTag.skipString(dataInput);
					tagType.parse(dataInput, streamTagVisitor, nbtAccounter);
			}
		}
	}

	public static Tag readAnyTag(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		byte b = dataInput.readByte();
		return (Tag)(b == 0 ? EndTag.INSTANCE : readTagSafe(dataInput, nbtAccounter, b));
	}

	public static void writeAnyTag(Tag tag, DataOutput dataOutput) throws IOException {
		dataOutput.writeByte(tag.getId());
		if (tag.getId() != 0) {
			tag.write(dataOutput);
		}
	}

	public static void writeUnnamedTag(Tag tag, DataOutput dataOutput) throws IOException {
		dataOutput.writeByte(tag.getId());
		if (tag.getId() != 0) {
			dataOutput.writeUTF("");
			tag.write(dataOutput);
		}
	}

	public static void writeUnnamedTagWithFallback(Tag tag, DataOutput dataOutput) throws IOException {
		writeUnnamedTag(tag, new NbtIo.StringFallbackDataOutput(dataOutput));
	}

	@VisibleForTesting
	public static Tag readUnnamedTag(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		byte b = dataInput.readByte();
		if (b == 0) {
			return EndTag.INSTANCE;
		} else {
			StringTag.skipString(dataInput);
			return readTagSafe(dataInput, nbtAccounter, b);
		}
	}

	private static Tag readTagSafe(DataInput dataInput, NbtAccounter nbtAccounter, byte b) {
		try {
			return TagTypes.getType(b).load(dataInput, nbtAccounter);
		} catch (IOException var6) {
			CrashReport crashReport = CrashReport.forThrowable(var6, "Loading NBT data");
			CrashReportCategory crashReportCategory = crashReport.addCategory("NBT Tag");
			crashReportCategory.setDetail("Tag type", b);
			throw new ReportedNbtException(crashReport);
		}
	}

	private static void shadowCompareRead(byte[] input, int compression, CompoundTag rust) {
		if (!RUST_SHADOW_COMPARE) {
			return;
		}
		try {
			CompoundTag java = compression == NativeNbt.FORMAT_GZIP
				? read(new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(input))), NbtAccounter.unlimitedHeap())
				: read(new DataInputStream(new ByteArrayInputStream(input)), NbtAccounter.unlimitedHeap());
			if (!java.equals(rust)) {
				LOGGER.error("Rust NBT whole-buffer read shadow mismatch for compression {}", compression);
				VALIDATION_DIAGNOSTICS.recordShadowMismatch("read", compression);
			} else {
				VALIDATION_DIAGNOSTICS.recordShadowMatch("read", compression);
			}
		} catch (Throwable throwable) {
			LOGGER.error("Java NBT shadow read failed for compression {}", compression, throwable);
			VALIDATION_DIAGNOSTICS.recordShadowError("read", compression, throwable);
		}
	}

	private static void shadowCompareWrite(CompoundTag tag, int compression, byte[] rustBytes) {
		if (!RUST_SHADOW_COMPARE) {
			return;
		}
		try {
			ByteArrayOutputStream javaOutput = new ByteArrayOutputStream();
			if (compression == NativeNbt.FORMAT_GZIP) {
				try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(javaOutput))) {
					write(tag, output);
				}
			} else {
				try (DataOutputStream output = new DataOutputStream(javaOutput)) {
					write(tag, output);
				}
			}
			CompoundTag javaTag = shadowRead(javaOutput.toByteArray(), compression);
			CompoundTag rustTag = shadowRead(rustBytes, compression);
			if (!javaTag.equals(rustTag)) {
				LOGGER.error("Rust NBT whole-buffer write shadow mismatch for compression {}", compression);
				VALIDATION_DIAGNOSTICS.recordShadowMismatch("write", compression);
			} else {
				VALIDATION_DIAGNOSTICS.recordShadowMatch("write", compression);
			}
		} catch (Throwable throwable) {
			LOGGER.error("NBT shadow write failed for compression {}", compression, throwable);
			VALIDATION_DIAGNOSTICS.recordShadowError("write", compression, throwable);
		}
	}

	private static CompoundTag shadowRead(byte[] input, int compression) throws IOException {
		return compression == NativeNbt.FORMAT_GZIP
			? read(new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(input))), NbtAccounter.unlimitedHeap())
			: read(new DataInputStream(new ByteArrayInputStream(input)), NbtAccounter.unlimitedHeap());
	}

	private static final class NbtValidationDiagnostics {
		private final Path statusPath;
		private int rustReads;
		private int rustWrites;
		private int gzipReads;
		private int gzipWrites;
		private int rawReads;
		private int rawWrites;
		private long bytesDecoded;
		private long bytesEncoded;
		private int shadowMatches;
		private int shadowMismatches;
		private int shadowErrors;
		private final java.util.Set<String> files = new java.util.TreeSet<>();
		private final java.util.List<String> failures = new java.util.ArrayList<>();

		private NbtValidationDiagnostics(Path statusPath) {
			this.statusPath = statusPath;
			Runtime.getRuntime().addShutdownHook(new Thread(this::writeStatus, "MattMC NBT validation status"));
		}

		static NbtValidationDiagnostics create() {
			String path = System.getProperty("mattmc.dev.nbtValidationStatus", "");
			return path.isBlank() ? new NbtValidationDiagnostics(null) : new NbtValidationDiagnostics(Path.of(path));
		}

		synchronized void recordRead(int compression, long encodedBytes, long decodedApproxBytes, @Nullable Path path) {
			this.rustReads++;
			this.bytesDecoded += encodedBytes;
			if (compression == NativeNbt.FORMAT_GZIP) {
				this.gzipReads++;
			} else if (compression == NativeNbt.FORMAT_RAW) {
				this.rawReads++;
			}
			this.recordFile(path);
		}

		synchronized void recordWrite(int compression, long decodedApproxBytes, long encodedBytes, @Nullable Path path) {
			this.rustWrites++;
			this.bytesEncoded += encodedBytes;
			if (compression == NativeNbt.FORMAT_GZIP) {
				this.gzipWrites++;
			} else if (compression == NativeNbt.FORMAT_RAW) {
				this.rawWrites++;
			}
			this.recordFile(path);
		}

		synchronized void recordShadowMatch(String operation, int compression) {
			this.shadowMatches++;
		}

		synchronized void recordShadowMismatch(String operation, int compression) {
			this.shadowMismatches++;
			this.failures.add(operation + " mismatch compression=" + compression);
		}

		synchronized void recordShadowError(String operation, int compression, Throwable throwable) {
			this.shadowErrors++;
			this.failures.add(operation + " error compression=" + compression + " type=" + throwable.getClass().getSimpleName());
		}

		private void recordFile(@Nullable Path path) {
			if (path != null) {
				this.files.add(path.toString().replace('\\', '/'));
			}
		}

		private synchronized void writeStatus() {
			if (this.statusPath == null) {
				return;
			}
			try {
				Path parent = this.statusPath.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.writeString(this.statusPath, this.toJson(), StandardCharsets.UTF_8);
			} catch (IOException exception) {
				LOGGER.error("Failed to write Rust NBT validation status", exception);
			}
		}

		private String toJson() {
			return "{\n"
				+ "  \"rustReads\": " + this.rustReads + ",\n"
				+ "  \"rustWrites\": " + this.rustWrites + ",\n"
				+ "  \"gzipReads\": " + this.gzipReads + ",\n"
				+ "  \"gzipWrites\": " + this.gzipWrites + ",\n"
				+ "  \"rawReads\": " + this.rawReads + ",\n"
				+ "  \"rawWrites\": " + this.rawWrites + ",\n"
				+ "  \"bytesDecoded\": " + this.bytesDecoded + ",\n"
				+ "  \"bytesEncoded\": " + this.bytesEncoded + ",\n"
				+ "  \"shadowCompareEnabled\": " + RUST_SHADOW_COMPARE + ",\n"
				+ "  \"shadowMatches\": " + this.shadowMatches + ",\n"
				+ "  \"shadowMismatches\": " + this.shadowMismatches + ",\n"
				+ "  \"shadowErrors\": " + this.shadowErrors + ",\n"
				+ "  \"files\": " + jsonArray(this.files) + ",\n"
				+ "  \"failures\": " + jsonArray(this.failures) + "\n"
				+ "}\n";
		}

		private static String jsonArray(Iterable<String> values) {
			StringBuilder builder = new StringBuilder("[");
			boolean first = true;
			for (String value : values) {
				if (!first) {
					builder.append(", ");
				}
				builder.append('"').append(jsonEscape(value)).append('"');
				first = false;
			}
			return builder.append(']').toString();
		}

		private static String jsonEscape(String value) {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				switch (c) {
					case '"' -> builder.append("\\\"");
					case '\\' -> builder.append("\\\\");
					case '\n' -> builder.append("\\n");
					case '\r' -> builder.append("\\r");
					case '\t' -> builder.append("\\t");
					default -> {
						if (c < 0x20) {
							builder.append(String.format("\\u%04x", (int)c));
						} else {
							builder.append(c);
						}
					}
				}
			}
			return builder.toString();
		}
	}

	public static class StringFallbackDataOutput extends DelegateDataOutput {
		public StringFallbackDataOutput(DataOutput dataOutput) {
			super(dataOutput);
		}

		@Override
		public void writeUTF(String string) throws IOException {
			try {
				super.writeUTF(string);
			} catch (UTFDataFormatException var3) {
				Util.logAndPauseIfInIde("Failed to write NBT String", var3);
				super.writeUTF("");
			}
		}
	}
}

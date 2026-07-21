package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;
import org.jetbrains.annotations.Nullable;

/**
 * Whole-buffer NBT reads and writes are Rust-authoritative through {@link NativeNbt}.
 *
 * <p>Java still owns the public {@link Tag}/{@link CompoundTag} object model,
 * codec integration, DFU-facing operations, and stream/visitor compatibility
 * APIs. Methods that accept {@link DataInput}, {@link DataOutput}, or a
 * {@link StreamTagVisitor} intentionally remain Java implementations for direct
 * callers that require streaming behavior instead of a complete byte buffer.
 */
public class NbtIo {
	private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
		StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
	};

	public static CompoundTag readCompressed(Path path, NbtAccounter nbtAccounter) throws IOException {
		try (InputStream inputStream = new FastBufferedInputStream(Files.newInputStream(path))) {
			byte[] bytes = inputStream.readAllBytes();
			long started = NbtIoDiagnostics.start();
			CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_GZIP, nbtAccounter);
			NbtIoDiagnostics.recordRustRead(bytes, NativeNbt.FORMAT_GZIP, rust, NbtIoDiagnostics.elapsed(started), path);
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
		long started = NbtIoDiagnostics.start();
		CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_GZIP, nbtAccounter);
		NbtIoDiagnostics.recordRustRead(bytes, NativeNbt.FORMAT_GZIP, rust, NbtIoDiagnostics.elapsed(started), null);
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
		long started = NbtIoDiagnostics.start();
		byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_GZIP);
		NbtIoDiagnostics.recordRustWrite(compoundTag, NativeNbt.FORMAT_GZIP, bytes, NbtIoDiagnostics.elapsed(started), path);
		try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path, SYNC_OUTPUT_OPTIONS))) {
			outputStream.write(bytes);
		}
	}

	public static void writeCompressed(CompoundTag compoundTag, OutputStream outputStream) throws IOException {
		long started = NbtIoDiagnostics.start();
		byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_GZIP);
		NbtIoDiagnostics.recordRustWrite(compoundTag, NativeNbt.FORMAT_GZIP, bytes, NbtIoDiagnostics.elapsed(started), null);
		try (outputStream) {
			outputStream.write(bytes);
		}
	}

	public static void write(CompoundTag compoundTag, Path path) throws IOException {
		long started = NbtIoDiagnostics.start();
		byte[] bytes = NativeNbt.write(compoundTag, NativeNbt.FORMAT_RAW);
		NbtIoDiagnostics.recordRustWrite(compoundTag, NativeNbt.FORMAT_RAW, bytes, NbtIoDiagnostics.elapsed(started), path);
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
			long started = NbtIoDiagnostics.start();
			CompoundTag rust = NativeNbt.read(bytes, NativeNbt.FORMAT_RAW, NbtAccounter.unlimitedHeap());
			NbtIoDiagnostics.recordRustRead(bytes, NativeNbt.FORMAT_RAW, rust, NbtIoDiagnostics.elapsed(started), path);
			return rust;
		}
	}

	public static CompoundTag read(DataInput dataInput) throws IOException {
		return read(dataInput, NbtAccounter.unlimitedHeap());
	}

	public static CompoundTag read(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
		long started = NbtIoDiagnostics.start();
		Tag tag = readUnnamedTag(dataInput, nbtAccounter);
		if (tag instanceof CompoundTag) {
			CompoundTag compoundTag = (CompoundTag)tag;
			NbtIoDiagnostics.recordJavaRead(compoundTag, NbtIoDiagnostics.elapsed(started));
			return compoundTag;
		} else {
			throw new IOException("Root tag must be a named compound tag");
		}
	}

	public static void write(CompoundTag compoundTag, DataOutput dataOutput) throws IOException {
		long started = NbtIoDiagnostics.start();
		writeUnnamedTagWithFallback(compoundTag, dataOutput);
		NbtIoDiagnostics.recordJavaWrite(compoundTag, NbtIoDiagnostics.elapsed(started));
	}

	static void write(DataOutput dataOutput, CompoundTag compoundTag) throws IOException {
		writeUnnamedTagWithFallback(compoundTag, dataOutput);
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

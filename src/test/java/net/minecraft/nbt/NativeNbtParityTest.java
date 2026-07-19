package net.minecraft.nbt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeNbtParityTest {
	private static final int OK = NativeNbt.OK;
	private static final int PARSE_ERROR = -2;
	private static final int COMPRESSION_ERROR = -5;

	@Test
	void generatedAllTagDocumentMatchesJavaRoundTrip() throws IOException {
		byte[] javaBytes = writeUncompressed(generatedAllTagDocument());
		NativeNbt.Result rustOriginal = NativeNbt.fingerprint(javaBytes);
		assertEquals(OK, rustOriginal.status());

		CompoundTag javaRead = readUncompressed(javaBytes);
		byte[] javaRoundTrip = writeUncompressed(javaRead);
		NativeNbt.Result rustAfterJava = NativeNbt.fingerprint(javaRoundTrip);

		assertEquals(rustOriginal.fingerprint(), rustAfterJava.fingerprint());
	}

	@Test
	void rustReencodedBytesAreReadableByJava() throws IOException {
		byte[] javaBytes = writeUncompressed(generatedAllTagDocument());
		NativeNbt.ReencodeResult rustEncoded = NativeNbt.reencode(javaBytes);

		assertEquals(OK, rustEncoded.result().status());
		assertEquals(NativeNbt.fingerprint(javaBytes).fingerprint(), NativeNbt.fingerprint(rustEncoded.bytes()).fingerprint());
		assertEquals(generatedAllTagDocument(), readUncompressed(rustEncoded.bytes()));
	}

	@Test
	void tapeDecodeBuildsJavaCompoundTree() throws IOException {
		CompoundTag root = generatedAllTagDocument();
		byte[] javaBytes = writeUncompressed(root);

		CompoundTag decoded = NativeNbt.read(javaBytes, NativeNbt.FORMAT_RAW, NbtAccounter.unlimitedHeap());

		assertEquals(root, decoded);
	}

	@Test
	void javaCompoundTreeEncodesThroughRustTape() throws IOException {
		CompoundTag root = generatedAllTagDocument();
		byte[] rustBytes = NativeNbt.write(root, NativeNbt.FORMAT_RAW);

		assertEquals(root, readUncompressed(rustBytes));
	}

	@Test
	void nativeTapeRoundTripsThroughCompressedFormats() throws IOException {
		CompoundTag root = generatedAllTagDocument();
		byte[] gzip = writeGzip(root);
		NativeNbt.ReencodeResult tape = NativeNbt.decodeToTape(
			gzip,
			NativeNbt.FORMAT_AUTO,
			new NativeNbt.CompressionLimits(0, 0),
			new NativeNbt.Limits(0, 0, 0, 0)
		);

		assertEquals(OK, tape.result().status());
		NativeNbt.ReencodeResult zlib = NativeNbt.encodeFromTape(
			tape.bytes(),
			NativeNbt.FORMAT_ZLIB,
			new NativeNbt.CompressionLimits(0, 0),
			new NativeNbt.Limits(0, 0, 0, 0)
		);
		assertEquals(OK, zlib.result().status());
		assertEquals(root, readZlib(zlib.bytes()));
	}

	@Test
	void modifiedUtf8EdgeCasesMatchJavaReadWrite() throws IOException {
		CompoundTag root = new CompoundTag();
		root.putString("nul", "\u0000");
		root.putString("supplementary", "hello \uD83D\uDE00");
		root.putString("unpairedHigh", new String(new char[]{'x', '\uD800', 'y'}));
		root.putString("unpairedLow", new String(new char[]{'x', '\uDC00', 'y'}));
		root.putString("maxEncoded", "a".repeat(65535));

		byte[] javaBytes = writeUncompressed(root);
		NativeNbt.ReencodeResult rustEncoded = NativeNbt.reencode(javaBytes);

		assertEquals(OK, rustEncoded.result().status());
		assertEquals(NativeNbt.fingerprint(javaBytes).fingerprint(), NativeNbt.fingerprint(rustEncoded.bytes()).fingerprint());
		assertEquals(root, readUncompressed(rustEncoded.bytes()));
	}

	@Test
	void duplicateKeysMatchJavaLastValueSemantics() throws IOException {
		byte[] duplicate = duplicateKeyDocument();
		CompoundTag javaRead = readUncompressed(duplicate);
		byte[] javaRoundTrip = writeUncompressed(javaRead);

		NativeNbt.Result rustOriginal = NativeNbt.fingerprint(duplicate);
		NativeNbt.Result rustAfterJava = NativeNbt.fingerprint(javaRoundTrip);

		assertEquals(OK, rustOriginal.status());
		assertEquals(OK, rustAfterJava.status());
		assertEquals(rustAfterJava.fingerprint(), rustOriginal.fingerprint());
		assertEquals(2, javaRead.getIntOr("a", 0));
	}

	@Test
	void malformedDocumentsFailSafely() throws IOException {
		List<byte[]> malformedInputs = List.of(
			new byte[0],
			new byte[]{10, 0},
			new byte[]{1, 0, 0, 0},
			negativeByteArrayLength(),
			nonEmptyListWithoutElementType(),
			trailingDataDocument()
		);

		for (byte[] malformed : malformedInputs) {
			NativeNbt.Result result = NativeNbt.fingerprint(malformed);
			assertNotEquals(OK, result.status(), "malformed input should fail");
		}
	}

	@Test
	void nativeLimitsRejectDeepOrLargeDocuments() throws IOException {
		byte[] nested = writeUncompressed(nestedDocument());
		byte[] largeArray = writeUncompressed(largeArrayDocument());

		assertEquals(PARSE_ERROR, NativeNbt.fingerprint(nested, new NativeNbt.Limits(2, 0, 0, 0)).status());
		assertEquals(PARSE_ERROR, NativeNbt.fingerprint(largeArray, new NativeNbt.Limits(0, 4, 0, 0)).status());
		assertEquals(PARSE_ERROR, NativeNbt.fingerprint(largeArray, new NativeNbt.Limits(0, 0, 16, 0)).status());
	}

	@Test
	void realStructureResourcesParseAfterJavaDecompression() throws IOException {
		List<String> resources = List.of(
			"/data/minecraft/structure/bastion/blocks/air.nbt",
			"/data/minecraft/structure/bastion/blocks/gold.nbt",
			"/data/minecraft/structure/ancient_city/city_center/city_center_1.nbt"
		);

		for (String resource : resources) {
			try (InputStream input = NativeNbtParityTest.class.getResourceAsStream(resource)) {
				assertTrue(input != null, "Missing resource " + resource);
				CompoundTag tag = readJavaGzip(input);
				byte[] uncompressed = writeUncompressed(tag);
				NativeNbt.ReencodeResult rustEncoded = NativeNbt.reencode(uncompressed);

				assertEquals(OK, rustEncoded.result().status(), resource);
				assertEquals(
					NativeNbt.fingerprint(uncompressed).fingerprint(),
					NativeNbt.fingerprint(rustEncoded.bytes()).fingerprint(),
					resource
				);
				assertEquals(tag, readUncompressed(rustEncoded.bytes()), resource);
			}
		}
	}

	@Test
	void rawByteEqualityHoldsForDeterministicRustRoundTrip() throws IOException {
		byte[] javaBytes = writeUncompressed(generatedAllTagDocument());
		NativeNbt.ReencodeResult rustEncoded = NativeNbt.reencode(javaBytes);

		assertEquals(OK, rustEncoded.result().status());
		assertArrayEquals(javaBytes, rustEncoded.bytes());
	}

	@Test
	void autoDetectsRawGzipAndZlibDocuments() throws IOException {
		CompoundTag tag = generatedAllTagDocument();
		byte[] raw = writeUncompressed(tag);
		byte[] gzip = writeGzip(tag);
		byte[] zlib = writeZlib(tag);
		long fingerprint = NativeNbt.compressedFingerprint(raw).fingerprint();

		NativeNbt.Result rawResult = NativeNbt.compressedFingerprint(raw);
		NativeNbt.Result gzipResult = NativeNbt.compressedFingerprint(gzip);
		NativeNbt.Result zlibResult = NativeNbt.compressedFingerprint(zlib);

		assertEquals(OK, rawResult.status());
		assertEquals(NativeNbt.FORMAT_RAW, rawResult.errorKind());
		assertEquals(OK, gzipResult.status());
		assertEquals(NativeNbt.FORMAT_GZIP, gzipResult.errorKind());
		assertEquals(OK, zlibResult.status());
		assertEquals(NativeNbt.FORMAT_ZLIB, zlibResult.errorKind());
		assertEquals(fingerprint, gzipResult.fingerprint());
		assertEquals(fingerprint, zlibResult.fingerprint());
	}

	@Test
	void rustRecompressionRoundTripsThroughJavaForAllOutputFormats() throws IOException {
		CompoundTag tag = generatedAllTagDocument();
		byte[] raw = writeUncompressed(tag);
		byte[] gzip = writeGzip(tag);
		byte[] zlib = writeZlib(tag);
		long fingerprint = NativeNbt.compressedFingerprint(raw).fingerprint();

		NativeNbt.ReencodeResult rawToGzip = NativeNbt.recompress(raw, NativeNbt.FORMAT_AUTO, NativeNbt.FORMAT_GZIP);
		NativeNbt.ReencodeResult gzipToZlib = NativeNbt.recompress(gzip, NativeNbt.FORMAT_AUTO, NativeNbt.FORMAT_ZLIB);
		NativeNbt.ReencodeResult zlibToRaw = NativeNbt.recompress(zlib, NativeNbt.FORMAT_AUTO, NativeNbt.FORMAT_RAW);

		assertEquals(OK, rawToGzip.result().status());
		assertEquals(tag, NbtIo.readCompressed(new ByteArrayInputStream(rawToGzip.bytes()), NbtAccounter.unlimitedHeap()));
		assertEquals(fingerprint, NativeNbt.compressedFingerprint(rawToGzip.bytes()).fingerprint());

		assertEquals(OK, gzipToZlib.result().status());
		assertEquals(tag, readZlib(gzipToZlib.bytes()));
		assertEquals(fingerprint, NativeNbt.compressedFingerprint(gzipToZlib.bytes()).fingerprint());

		assertEquals(OK, zlibToRaw.result().status());
		assertEquals(tag, readUncompressed(zlibToRaw.bytes()));
		assertEquals(fingerprint, NativeNbt.compressedFingerprint(zlibToRaw.bytes()).fingerprint());
	}

	@Test
	void gzipStructureResourcesRecompressWithIdenticalDecodedSemantics() throws IOException {
		List<String> resources = List.of(
			"/data/minecraft/structure/bastion/blocks/air.nbt",
			"/data/minecraft/structure/bastion/blocks/gold.nbt",
			"/data/minecraft/structure/ancient_city/city_center/city_center_1.nbt"
		);

		for (String resource : resources) {
			try (InputStream input = NativeNbtParityTest.class.getResourceAsStream(resource)) {
				assertTrue(input != null, "Missing resource " + resource);
				byte[] gzip = input.readAllBytes();
				CompoundTag javaTag = readJavaGzip(new ByteArrayInputStream(gzip));
				NativeNbt.Result original = NativeNbt.compressedFingerprint(gzip);
				NativeNbt.ReencodeResult recompressed = NativeNbt.recompress(gzip, NativeNbt.FORMAT_AUTO, NativeNbt.FORMAT_GZIP);

				assertEquals(OK, original.status(), resource);
				assertEquals(NativeNbt.FORMAT_GZIP, original.errorKind(), resource);
				assertEquals(OK, recompressed.result().status(), resource);
				assertEquals(
					javaTag,
					NbtIo.readCompressed(new ByteArrayInputStream(recompressed.bytes()), NbtAccounter.unlimitedHeap()),
					resource
				);
				assertEquals(original.fingerprint(), NativeNbt.compressedFingerprint(recompressed.bytes()).fingerprint(), resource);
			}
		}
	}

	@Test
	void compressedMalformedInputsFailSafely() throws IOException {
		byte[] gzip = writeGzip(generatedAllTagDocument());
		byte[] truncatedGzip = gzip.clone();
		truncatedGzip = java.util.Arrays.copyOf(truncatedGzip, truncatedGzip.length - 4);
		byte[] zlib = writeZlib(generatedAllTagDocument());
		byte[] trailingZlib = java.util.Arrays.copyOf(zlib, zlib.length + 1);
		trailingZlib[trailingZlib.length - 1] = 1;

		assertEquals(COMPRESSION_ERROR, NativeNbt.compressedFingerprint(truncatedGzip).status());
		assertEquals(COMPRESSION_ERROR, NativeNbt.compressedFingerprint(trailingZlib).status());
		assertEquals(
			COMPRESSION_ERROR,
			NativeNbt.compressedFingerprint(
				gzip,
				NativeNbt.FORMAT_AUTO,
				new NativeNbt.CompressionLimits(0, 8),
				new NativeNbt.Limits(0, 0, 0, 0)
			).status()
		);
		assertEquals(
			COMPRESSION_ERROR,
			NativeNbt.compressedFingerprint(
				gzip,
				99,
				new NativeNbt.CompressionLimits(0, 0),
				new NativeNbt.Limits(0, 0, 0, 0)
			).status()
		);
	}

	@Test
	void nbtIoWholeBufferCompressedPathIsRustBackedAndJavaReadable() throws IOException {
		CompoundTag root = generatedAllTagDocument();
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		NbtIo.writeCompressed(root, output);

		byte[] gzip = output.toByteArray();
		assertEquals(root, readJavaGzip(new ByteArrayInputStream(gzip)));
		assertEquals(root, NbtIo.readCompressed(new ByteArrayInputStream(gzip), NbtAccounter.unlimitedHeap()));
	}

	@Test
	void nbtIoWholeBufferRawPathIsRustBackedAndJavaReadable() throws IOException {
		CompoundTag root = generatedAllTagDocument();
		Path path = Files.createTempFile("mattmc-nbt-rust", ".dat");
		try {
			NbtIo.write(root, path);

			byte[] raw = Files.readAllBytes(path);
			assertEquals(root, readUncompressed(raw));
			assertEquals(root, NbtIo.read(path));
		} finally {
			Files.deleteIfExists(path);
		}
	}

	private static CompoundTag generatedAllTagDocument() {
		CompoundTag root = new CompoundTag();
		root.putByte("byte", (byte)-7);
		root.putShort("short", (short)-1234);
		root.putInt("int", 1234567);
		root.putLong("long", -9876543210L);
		root.putFloat("float", Float.intBitsToFloat(0x7FC01234));
		root.putDouble("double", Double.NEGATIVE_INFINITY);
		root.putByteArray("bytes", new byte[]{-1, 0, 1});
		root.putString("string", "hello\u0000\uD83D\uDE00");
		ListTag list = new ListTag();
		list.add(IntTag.valueOf(1));
		list.add(IntTag.valueOf(2));
		root.put("list", list);
		root.put("emptyList", new ListTag());
		CompoundTag nested = new CompoundTag();
		nested.putString("child", "value");
		root.put("compound", nested);
		root.putIntArray("ints", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
		root.putLongArray("longs", new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE});
		return root;
	}

	private static CompoundTag nestedDocument() {
		CompoundTag root = new CompoundTag();
		CompoundTag a = new CompoundTag();
		CompoundTag b = new CompoundTag();
		b.putInt("value", 1);
		a.put("b", b);
		root.put("a", a);
		return root;
	}

	private static CompoundTag largeArrayDocument() {
		CompoundTag root = new CompoundTag();
		root.putByteArray("large", new byte[]{1, 2, 3, 4, 5});
		return root;
	}

	private static byte[] writeUncompressed(CompoundTag tag) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		NbtIo.write(tag, new DataOutputStream(output));
		return output.toByteArray();
	}

	private static byte[] writeGzip(CompoundTag tag) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(output))) {
			NbtIo.write(tag, data);
		}
		return output.toByteArray();
	}

	private static byte[] writeZlib(CompoundTag tag) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (DataOutputStream data = new DataOutputStream(new DeflaterOutputStream(output))) {
			NbtIo.write(tag, data);
		}
		return output.toByteArray();
	}

	private static CompoundTag readUncompressed(byte[] bytes) throws IOException {
		return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.unlimitedHeap());
	}

	private static CompoundTag readZlib(byte[] bytes) throws IOException {
		return NbtIo.read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes))), NbtAccounter.unlimitedHeap());
	}

	private static CompoundTag readJavaGzip(InputStream input) throws IOException {
		return NbtIo.read(new DataInputStream(new GZIPInputStream(input)), NbtAccounter.unlimitedHeap());
	}

	private static byte[] duplicateKeyDocument() throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(output);
		data.writeByte(10);
		data.writeUTF("");
		data.writeByte(3);
		data.writeUTF("a");
		data.writeInt(1);
		data.writeByte(3);
		data.writeUTF("a");
		data.writeInt(2);
		data.writeByte(0);
		return output.toByteArray();
	}

	private static byte[] negativeByteArrayLength() {
		return new byte[]{10, 0, 0, 7, 0, 1, 'b', -1, -1, -1, -1};
	}

	private static byte[] nonEmptyListWithoutElementType() {
		return new byte[]{10, 0, 0, 9, 0, 1, 'l', 0, 0, 0, 0, 1};
	}

	private static byte[] trailingDataDocument() throws IOException {
		byte[] bytes = writeUncompressed(new CompoundTag());
		byte[] trailing = new byte[bytes.length + 1];
		System.arraycopy(bytes, 0, trailing, 0, bytes.length);
		trailing[trailing.length - 1] = 1;
		return trailing;
	}
}

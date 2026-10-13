package com.seibel.distanthorizons.core.sql.dto;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.reflect.Field;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The native load path (one Rust contributor decode, whole-buffer feeds,
 * one decompression of the compression blob) against the Java control
 * ({@code -Dmattmc.dh.javaCodec=true}) on the same saved rows.
 */
class FullDataSourceV2NativeLoadTest {
	private static final EDhApiDataCompressionMode[] MODES = {
		EDhApiDataCompressionMode.UNCOMPRESSED, EDhApiDataCompressionMode.Z_STD_BLOCK, EDhApiDataCompressionMode.LZ4};

	private static FullDataSourceV2 sample(long seed) throws Exception {
		long pos = DhSectionPos.encode((byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0);
		FullDataSourceV2 source = FullDataSourceV2.createEmpty(pos);
		Random random = new Random(seed);
		for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
			if (random.nextInt(3) == 0) {
				var points = new LongArrayList();
				int bottom = 0;
				for (int i = 0, n = 1 + random.nextInt(4); i < n; i++) {
					int height = 1 + random.nextInt(20);
					points.add(FullDataPointUtil.encode(random.nextInt(5), height, bottom, (byte) random.nextInt(16), (byte) random.nextInt(16)));
					bottom += height;
				}
				source.setSingleColumn(points, x, z,
					random.nextBoolean() ? EDhApiWorldGenerationStep.DOWN_SAMPLED : EDhApiWorldGenerationStep.LIGHT,
					EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
			}
			source.setSemanticHorizontalUniformity(x, z, random.nextBoolean());
			if (random.nextInt(4) == 0) {
				LongList[] contributors = new LongList[4];
				for (int i = 0; i < 4; i++) {
					int kind = random.nextInt(5);
					if (kind == 0) continue; // null column
					int size = kind == 1 ? 0 : kind == 4 ? FullDataSourceV2.MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_POINTS : 1 + random.nextInt(9);
					var column = new LongArrayList();
					for (int p = 0; p < size; p++) column.add(random.nextLong());
					contributors[i] = column;
				}
				source.setSemanticHorizontalContributors(x, z, contributors);
			}
		}
		return source;
	}

	private static void setNative(boolean enabled) throws Exception {
		Field field = Class.forName("net.minecraft.world.level.chunk.NativeDhCodec").getDeclaredField("enabled");
		field.setAccessible(true);
		field.setBoolean(null, enabled);
	}

	/** Decodes {@code dto} with the native path or the Java control; returns the source or the thrown exception. */
	private static Object decode(FullDataSourceV2DTO dto, boolean nativePath) throws Exception {
		setNative(nativePath);
		try { return dto.createUnitTestDataSource(); }
		catch (Exception e) { return e; }
		finally { setNative(true); }
	}

	private static void assertSameSource(FullDataSourceV2 expected, FullDataSourceV2 actual) {
		for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) {
			int index = x * 64 + z;
			LongList a = expected.getColumnAtRelPos(x, z), b = actual.getColumnAtRelPos(x, z);
			assertEquals(a == null, b == null, "column presence " + index);
			if (a != null) assertEquals(new LongArrayList(a), new LongArrayList(b), "column " + index);
			assertEquals(expected.hasSemanticHorizontalUniformity(x, z), actual.hasSemanticHorizontalUniformity(x, z), "uniformity " + index);
			LongList[] ca = expected.getSemanticHorizontalContributors(x, z), cb = actual.getSemanticHorizontalContributors(x, z);
			assertEquals(ca == null, cb == null, "contributor presence " + index);
			if (ca != null) for (int i = 0; i < 4; i++) {
				assertEquals(ca[i] == null, cb[i] == null, "contributor column presence " + index + "/" + i);
				if (ca[i] != null) assertEquals(new LongArrayList(ca[i]), new LongArrayList(cb[i]), "contributor column " + index + "/" + i);
			}
		}
		assertEquals(expected.columnGenerationSteps, actual.columnGenerationSteps);
		assertEquals(expected.columnWorldCompressionMode, actual.columnWorldCompressionMode);
	}

	private static void assertSameOutcome(FullDataSourceV2DTO dto, String label) throws Exception {
		Object java = decode(dto, false), rust = decode(dto, true);
		try {
			if (java instanceof Exception expected) {
				assertInstanceOf(Exception.class, rust, label + ": native decoded what Java rejected: " + expected);
				Exception actual = (Exception) rust;
				assertEquals(expected.getClass(), actual.getClass(), label);
				assertEquals(expected.getMessage(), actual.getMessage(), label);
				assertEquals(expected.getCause() == null ? null : expected.getCause().getClass(),
					actual.getCause() == null ? null : actual.getCause().getClass(), label);
			} else {
				assertInstanceOf(FullDataSourceV2.class, rust, label + ": native rejected what Java decoded: " + rust);
				assertSameSource((FullDataSourceV2) java, (FullDataSourceV2) rust);
			}
		} finally {
			if (java instanceof FullDataSourceV2 source) source.close();
			if (rust instanceof FullDataSourceV2 source) source.close();
		}
	}

	@Test
	void nativeLoadMatchesTheJavaControlForEveryCompressionMode() throws Exception {
		for (long seed = 1; seed <= 3; seed++) {
			try (FullDataSourceV2 source = sample(seed)) {
				for (var mode : MODES) {
					try (FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateFromDataSource(source, mode)) {
						Object decoded = decode(dto, true);
						assertInstanceOf(FullDataSourceV2.class, decoded, mode + " decodes: " + decoded);
						try (var rust = (FullDataSourceV2) decoded) { assertSameSource(source, rust); }
						assertSameOutcome(dto, mode + " seed " + seed);
					}
				}
			}
		}
	}

	@Test
	void truncatedOrOverBoundSidecarsFailExactlyAsInJava() throws Exception {
		try (FullDataSourceV2 source = sample(7);
			 FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateFromDataSource(source, EDhApiDataCompressionMode.UNCOMPRESSED)) {
			var contributors = dto.compressedSemanticHorizontalContributorByteArray;
			byte[] original = contributors.toByteArray();
			for (int cut : new int[] {0, 1, 2, 3, 17, original.length / 2, original.length - 9, original.length - 1}) {
				contributors.size(cut);
				assertSameOutcome(dto, "contributors cut at " + cut);
				contributors.size(0); contributors.addElements(0, original);
			}
			// A column size over the saved bound, in the first present cell.
			int first = 0;
			while (original[first] == 0) first++;
			byte[] over = original.clone();
			over[first + 1] = 0x01; over[first + 2] = 0x01; // 257
			contributors.size(0); contributors.addElements(0, over);
			assertSameOutcome(dto, "contributor size over bound");
			contributors.size(0); contributors.addElements(0, original);

			var compression = dto.compressedWorldCompressionModeByteArray;
			byte[] planes = compression.toByteArray();
			for (int cut : new int[] {0, 100, 4096, 4096 + 100, planes.length - 1}) {
				compression.size(cut);
				assertSameOutcome(dto, "compression/uniformity cut at " + cut);
				compression.size(0); compression.addElements(0, planes);
			}
			var data = dto.compressedDataByteArray;
			byte[] points = data.toByteArray();
			for (int cut : new int[] {0, 5, points.length / 3, points.length - 1}) {
				data.size(cut);
				assertSameOutcome(dto, "points cut at " + cut);
				data.size(0); data.addElements(0, points);
			}
		}
	}
}

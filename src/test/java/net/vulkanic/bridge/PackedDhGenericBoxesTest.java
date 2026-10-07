package net.vulkanic.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.List;
import org.junit.jupiter.api.Test;

class PackedDhGenericBoxesTest {
	private static List<VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord> records() {
		return List.of(
			new VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord(-1, -2, -3, 4, 5, 6, 0xff112233, 0x00f000f0,
				0.8F, 0.8F, 0.6F, 0.6F, 1.0F, 0.5F, true, 7, 1),
			new VulkanicGalBridge.WorldDistantHorizonsGenericBoxRecord(10, 20, 30, 11, 21, 31, 0x80ffffff, 0,
				1, 1, 1, 1, 1, 1, false, 0, 65535));
	}

	@Test
	void packedBoxesEncodeExactlyLikeRecordsAndRoundTrip() {
		var packed = new VulkanicGalBridge.PackedDhGenericBoxes();
		records().forEach(packed::add);
		assertEquals(records(), List.copyOf(packed));
		try (Arena arena = Arena.ofConfined()) {
			byte[] fromRecords = VulkanicGalBridge.encodeDistantHorizonsGenericBoxes(arena, records())
				.toArray(ValueLayout.JAVA_BYTE);
			byte[] fromPacked = VulkanicGalBridge.encodeDistantHorizonsGenericBoxes(arena, packed)
				.toArray(ValueLayout.JAVA_BYTE);
			assertArrayEquals(fromRecords, fromPacked);
		}
		var copy = new VulkanicGalBridge.PackedDhGenericBoxes();
		copy.addAllPacked(packed);
		assertEquals(records(), List.copyOf(copy));
	}

	@Test
	void packedBoxesKeepRecordValidation() {
		var packed = new VulkanicGalBridge.PackedDhGenericBoxes();
		assertThrows(IllegalArgumentException.class, () -> packed.addBox(1, 0, 0, 0, 0, 0, 0, 0,
			1, 1, 1, 1, 1, 1, false, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> packed.addBox(0, 0, 0, 1, 1, 1, 0, 0,
			1, 1, 1, 1, 1, Float.NaN, false, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> packed.addBox(0, 0, 0, 1, 1, 1, 0, 0,
			1, 1, 1, 1, 1, 1, false, 256, 0));
		assertEquals(0, packed.size());
	}
}

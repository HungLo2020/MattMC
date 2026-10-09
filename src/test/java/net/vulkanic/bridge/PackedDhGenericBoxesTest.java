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
    @Test void nativeInstancesCopyEpochsPinOwnersAndNeverReencodeProjectedOrigins() throws Exception {
        var state=new net.vulkanic.world.NativeDhCloudGroupState(2048,0,0,0);
        state.prepare(1000,6,0,70,0,0,0,1,128,320);
        long epoch=state.poseEpoch();
        var packed=new VulkanicGalBridge.PackedDhGenericBoxes();
        packed.addNativeCloudGroupInstance(7,1,state,0,false,1,1,1,1,1,1,2);
        var copy=new VulkanicGalBridge.PackedDhGenericBoxes();
        copy.addAllPacked(packed);
        packed.clear();
        state.prepare(2000,6,0,70,0,0,0,1,128,320);
        var encode=VulkanicGalBridge.PackedDhGenericBoxes.class.getDeclaredMethod("encodeInstances",Arena.class);
        encode.setAccessible(true);
        var owners=VulkanicGalBridge.PackedDhGenericBoxes.class.getDeclaredField("instanceCloudOwners");
        owners.setAccessible(true);
        assertEquals(state,((Object[])owners.get(copy))[0]);
        try(var arena=Arena.ofConfined()) {
            var bytes=(java.lang.foreign.MemorySegment)encode.invoke(copy,arena);
            assertEquals(88,bytes.byteSize());
            assertEquals(2,bytes.get(ValueLayout.JAVA_INT,44),"native origin flag must not enable SSAO");
            for(int i=0;i<3;i++) assertEquals(0,bytes.get(ValueLayout.JAVA_DOUBLE,16+i*8));
            assertEquals(state.ownerAddress(),bytes.get(ValueLayout.JAVA_LONG,72));
            assertEquals(epoch,bytes.get(ValueLayout.JAVA_LONG,80),"copy holds the requested frame, not the latest projection");
        }
        copy.clear();
        assertEquals(null,((Object[])owners.get(copy))[0],"clearing frame storage must release CPU owners");
    }

}

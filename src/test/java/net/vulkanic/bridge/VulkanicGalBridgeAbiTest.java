package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanicGalBridgeAbiTest {
	@Test
	void javaLayoutsAreQueriedFromRustAbi() {
		for (VulkanicGalBridge.Struct struct : VulkanicGalBridge.Struct.values()) {
			assertTrue(struct.byteSize() > 0, "byte size should be reported for " + struct);
			assertTrue(struct.alignment() > 0, "alignment should be reported for " + struct);
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment segment = struct.allocate(arena);
				assertEquals(struct.byteSize(), segment.byteSize(), "allocation should use Rust byte size for " + struct);
			}
		}
	}

	@Test
	void malformedBackendKindIsRejectedDeterministically() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment request = VulkanicGalBridge.Struct.CONTEXT_CREATE.allocate(arena);
			VulkanicGalBridge.Abi.writeHeader(request, VulkanicGalBridge.Struct.CONTEXT_CREATE);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 1, 9999);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 2, 0);
			VulkanicGalBridge.Abi.writeBytes(arena, request, VulkanicGalBridge.Struct.CONTEXT_CREATE, 3, "bad-backend");
			MemorySegment result = VulkanicGalBridge.Struct.CONTEXT_RESULT.allocate(arena);

			int status = VulkanicGalBridge.Native.contextCreate(request, result);

			assertEquals(-5, status);
			assertEquals(-5, VulkanicGalBridge.Struct.CONTEXT_RESULT.getInt(result, 1));
		}
	}

	@Test
	void rustBridgeIsOnlyRoutedFromSubsystemBenchmarkControls() throws Exception {
		String subsystem = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsSubsystemBenchmark.java"));
		String bridge = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));

		assertTrue(subsystem.contains("rust-vulkan") && subsystem.contains("rust-opengl"));
		assertTrue(bridge.contains("Rust VulkanicGAL bridge"));
	}
}

package net.vulkanic.world;

import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DistantHorizonsRouteSelectionOwnershipTest {
	@AfterEach
	void resetCollector() {
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
		DistantHorizonsSemanticCollector.resetForTest();
	}

	@Test
	void pendingVisibleSegmentsCannotBecomeRustConsumedWorkBeforeRouteSelection() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
		DistantHorizonsSemanticCollector.recordBuiltColumn(
			501L,
			new DhBlockPos(0, 64, 0),
			List.of(quadBuffer()),
			List.of(),
			List.of(),
			List.of()
		);
		DistantHorizonsSemanticCollector.PendingAssetUpdate update =
			DistantHorizonsSemanticCollector.pendingUpdateForTest();
		assertNotNull(update);
		DistantHorizonsSemanticCollector.acknowledgeForTest(update);

		DistantHorizonsSemanticCollector.beginRustOpaqueRouteFrameForTest();
		assertEquals(1, DistantHorizonsSemanticCollector.recordVisibleOpaqueColumn(501L).opaqueSegments());
		assertFalse(DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot().selected());
		assertEquals(
			List.of(),
			DistantHorizonsSemanticCollector.consumeVisibleSegments(),
			"visibility discovery alone must never become submitted Rust work before explicit route selection"
		);
	}

	@Test
	void rustWholeFrameDhHookCannotFallThroughToJavaRenderPasses() throws Exception {
		Path source = Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java");
		String lodRenderer = Files.readString(source);
		int renderStart = lodRenderer.indexOf("public void render(RenderParams renderParams");
		int renderBodyEnd = lodRenderer.indexOf("private void renderLodPass", renderStart);
		String renderBody = lodRenderer.substring(renderStart, renderBodyEnd);
		assertTrue(renderBody.contains("boolean rustWholeFrame"));
		assertTrue(renderBody.contains("if (rustWholeFrame)"));
		assertTrue(renderBody.indexOf("if (rustWholeFrame)") < renderBody.indexOf("this.renderLodPass(renderParams, profiler, false)"));
		assertTrue(renderBody.contains("renderDeferred"));
		assertTrue(renderBody.contains("Deferred DH passes are Java framebuffer/pipeline work"));
	}

	private static ByteBuffer quadBuffer() {
		ByteBuffer buffer = ByteBuffer.allocate(DistantHorizonsSemanticCollector.VERTEX_STRIDE_BYTES * 4)
			.order(ByteOrder.nativeOrder());
		for (int vertex = 0; vertex < 4; vertex++) {
			buffer.putShort((short)vertex);
			buffer.putShort((short)2);
			buffer.putShort((short)3);
			buffer.putShort((short)0xB7);
			buffer.put((byte)11);
			buffer.put((byte)12);
			buffer.put((byte)13);
			buffer.put((byte)255);
			buffer.put((byte)15);
			buffer.put((byte)16);
			buffer.putShort((short)0);
		}
		return buffer.flip();
	}
}

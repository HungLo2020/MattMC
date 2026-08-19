package net.minecraft.client.gui.render;

import net.vulkanic.bridge.RustGalFrameScheduler;
import net.vulkanic.gui.GuiFillDirection;
import net.vulkanic.gui.GuiRenderStratum;
import net.vulkanic.gui.RustGalGuiElementRenderState;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiRendererRustGalOrderingTest {
	@Test
	void contiguousRustRunsStopAtJavaDrawsAndRangeBoundaries() {
		RustGalGuiElementRenderState first = rustElement(1L);
		RustGalGuiElementRenderState second = rustElement(2L);
		RustGalGuiElementRenderState third = rustElement(3L);
		GuiRenderer.Draw javaDraw = new GuiRenderer.Draw(null, 0, null, 0, null, null, null, "test-java-draw");
		List<GuiRenderer.DrawStep> draws = List.of(
			new GuiRenderer.RustGalDraw(first),
			new GuiRenderer.RustGalDraw(second),
			javaDraw,
			new GuiRenderer.RustGalDraw(third)
		);

		assertEquals(List.of(first, second), GuiRenderer.contiguousRustGalDrawGroup(draws, 0, draws.size()),
			"borrowed OpenGL may batch adjacent Rust work but must stop before an intervening Java draw");
		assertEquals(List.of(first), GuiRenderer.contiguousRustGalDrawGroup(draws, 0, 1),
			"a before/after-blur draw-range boundary must cap the Rust submission even when another Rust draw follows globally");
		assertEquals(List.of(third), GuiRenderer.contiguousRustGalDrawGroup(draws, 3, draws.size()));
		assertEquals(List.of(), GuiRenderer.contiguousRustGalDrawGroup(draws, 2, draws.size()),
			"a Java draw is never absorbed into a Rust submission group");
	}

	@Test
	void borrowedOpenGlExecutionNoLongerHoistsTheWholeRustGuiFrame() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));

		assertFalse(source.contains("rustGalFrameElements()"),
			"partial-frame OpenGL must not gather every Rust GUI element and submit it at the first marker");
		assertFalse(source.contains("rustGalFrameExecuted"),
			"partial-frame OpenGL must not suppress later Rust draw positions with a one-shot frame flag");
		assertTrue(source.contains("contiguousRustGalDrawGroup(this.draws, k, j)"));
		assertTrue(source.contains("RustGalFrameCoordinator.executeGuiFrame(minecraft, rustGalDrawGroup)"));
		assertTrue(source.contains("k += rustGalDrawGroup.size();"));
	}

	private static RustGalGuiElementRenderState rustElement(long sequence) {
		GuiRenderStratum stratum = GuiRenderStratum.GUI_TEXT;
		RustGalFrameScheduler.Token token = new RustGalFrameScheduler.Token(
			sequence, sequence, 1L, stratum.id(), stratum.order()
		);
		return new RustGalGuiElementRenderState(
			token,
			stratum,
			"test.gui.text",
			-1,
			-1.0F,
			GuiFillDirection.NONE,
			0,
			0,
			8,
			8,
			320,
			180
		);
	}
}

package net.minecraft.client.gui.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiRendererRustGalOrderingTest {
	@Test
	void contiguousRustRunPartitionerStopsAtJavaAndRangeBoundaries() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		int helperStart = source.indexOf(
			"static List<RustGalGuiElementRenderState> contiguousRustGalDrawGroup"
		);
		int helperEnd = source.indexOf("\n\tprivate void executeDrawRange(", helperStart);

		assertTrue(helperStart >= 0 && helperEnd > helperStart,
			"borrowed-OpenGL run partitioning must remain explicit in GuiRenderer");
		String helper = source.substring(helperStart, helperEnd);
		assertTrue(helper.contains("if (start < 0 || end < start || end > draws.size())"),
			"run grouping must reject invalid range bounds");
		assertTrue(helper.contains("for (int index = start; index < end; index++)"),
			"the caller's before/after-blur draw range must cap the Rust group");
		assertTrue(helper.contains("if (!(draws.get(index) instanceof GuiRenderer.RustGalDraw rustGalDraw))"),
			"any intervening Java draw must terminate the current Rust group");
		assertTrue(helper.contains("break;"),
			"grouping must stop at Java work rather than skip across it");
		assertTrue(helper.contains("group.add(rustGalDraw.element());"));
		assertTrue(helper.contains("return List.copyOf(group);"));
	}

	@Test
	void borrowedOpenGlExecutionPreservesEachRustRunAtItsJavaDrawPosition() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));

		assertFalse(source.contains("rustGalFrameElements()"),
			"partial-frame OpenGL must not gather every Rust GUI element and submit it at the first marker");
		assertTrue(source.contains("MutableBoolean rustGalFrameExecuted = new MutableBoolean(false);"),
			"execution state must be local to one before/after-blur draw range");
		assertTrue(source.contains("contiguousRustGalDrawGroup(this.draws, k, j)"),
			"each Rust submission must be capped by the current Java draw position and draw-range boundary");
		assertTrue(source.contains("RustGalFrameCoordinator.executeGuiFrame(minecraft, rustGalDrawGroup)"));
		assertTrue(source.contains("rustGalFrameExecuted.setTrue();"));
		assertTrue(source.contains("rustGalFrameExecuted.setFalse();"),
			"Java work must reset the run marker so a later Rust text group executes instead of being suppressed");
		int rustBranch = source.indexOf("if (step instanceof GuiRenderer.RustGalDraw)");
		int groupSelection = source.indexOf("contiguousRustGalDrawGroup(this.draws, k, j)", rustBranch);
		int execute = source.indexOf("RustGalFrameCoordinator.executeGuiFrame(minecraft, rustGalDrawGroup)", groupSelection);
		int markExecuted = source.indexOf("rustGalFrameExecuted.setTrue();", execute);
		int resetForJava = source.indexOf("rustGalFrameExecuted.setFalse();", markExecuted);
		int javaRun = source.indexOf("int start = k;", resetForJava);
		assertTrue(rustBranch >= 0 && groupSelection > rustBranch && execute > groupSelection && markExecuted > execute,
			"the current contiguous Rust run must be selected and submitted before it is marked executed");
		assertTrue(resetForJava > markExecuted && javaRun > resetForJava,
			"the run marker must reset before the compositor resumes Java rendering");
	}
}

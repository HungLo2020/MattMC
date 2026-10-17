package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import net.vulkanic.world.DistantHorizonsSemanticCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** The DH build completes on a worker and can finish after its section closed. */
class LodRenderSectionLifecycleTest {
	@BeforeEach
	void enableCapture() {
		System.setProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY, "true");
	}

	@AfterEach
	void clearCapture() {
		System.clearProperty(DistantHorizonsSemanticCollector.CAPTURE_PROPERTY);
	}

	@Test
	void buildFinishingAfterCloseReleasesItsLeaseInsteadOfInstalling() {
		long pos = 9101L;
		LodRenderSection section = section(pos);
		LodBufferContainer installed = buildContainer(pos, 0xff557733);
		section.installBuiltContainer(installed);
		section.close();
		assertFalse(installed.rustSemanticBuildLifecycleCurrent());

		LodBufferContainer late = buildContainer(pos, 0xff885533);
		long lateGeneration = generationOf(late);
		assertTrue(DistantHorizonsSemanticCollector.hasColumn(pos, lateGeneration));
		section.installBuiltContainer(late);

		assertSame(installed, section.bufferContainer, "a closed section must not adopt a late build");
		assertFalse(late.rustSemanticBuildLifecycleCurrent());
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(pos, lateGeneration),
			"the late build's lease must be released, or its column stays current forever");
	}

	@Test
	void buildFinishingBeforeCloseIsInstalledAndRetiredByClose() {
		long pos = 9102L;
		LodRenderSection section = section(pos);
		LodBufferContainer first = buildContainer(pos, 0xff557733);
		section.installBuiltContainer(first);
		LodBufferContainer second = buildContainer(pos, 0xff885533);
		long secondGeneration = generationOf(second);
		section.installBuiltContainer(second);
		assertSame(second, section.bufferContainer);
		assertFalse(first.rustSemanticBuildLifecycleCurrent(), "the swap closes the previous container");
		assertTrue(second.rustSemanticBuildLifecycleCurrent());

		section.close();
		assertFalse(DistantHorizonsSemanticCollector.hasColumn(pos, secondGeneration));
	}

    @Test
    void retainedVisibilityTracksActualContainerSwapsAndClose() {
        long pos=com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)6,0,0);
        try(var owner=new net.vulkanic.world.NativeDhVisibility(8,6,256,0,0)) {
            long token=owner.event(1,pos,1,0);
            var section=section(pos);section.bindNativeVisibility(owner,token);section.setRenderingEnabled(true);
            var first=buildContainer(pos,0xff557733);section.installBuiltContainer(first);
            assertEquals(generationOf(first),owner.inspect(null,0F,1F)[5]);
            var second=buildContainer(pos,0xff885533);section.installBuiltContainer(second);
            assertEquals(generationOf(second),owner.inspect(null,0F,1F)[5]);
            first.close();
            assertEquals(generationOf(second),owner.inspect(null,0F,1F)[5],"An older close cannot invalidate its replacement");
            second.close();
            assertEquals(4,owner.inspect(null,0F,1F).length,"Closing the installed container invalidates its visibility immediately");
            section.close();
        }
    }

    @Test
    void replacedSectionCannotPublishOrInvalidateItsFormerBinding() {
        long pos=com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)6,0,0);
        try(var owner=new net.vulkanic.world.NativeDhVisibility(8,6,256,0,0)) {
            var old=section(pos);old.bindNativeVisibility(owner,owner.event(1,pos,1,0));old.setRenderingEnabled(true);
            old.installBuiltContainer(buildContainer(pos,0xff557733));
            var current=section(pos);current.bindNativeVisibility(owner,owner.event(1,pos,1,0));current.setRenderingEnabled(true);
            var latest=buildContainer(pos,0xff885533);current.installBuiltContainer(latest);
            old.close();old.setRenderingEnabled(true);
            assertEquals(generationOf(latest),owner.inspect(null,0F,1F)[5]);
            current.close();assertEquals(4,owner.inspect(null,0F,1F).length);
        }
    }

	@Test
	void parentRetirementCannotOverwriteBuildCompletedDuringOldContainerClose() {
		long pos = 9103L;
		LodRenderSection section = section(pos);
		LodBufferContainer first = mock(LodBufferContainer.class);
		when(first.renderDataReady()).thenReturn(true);
		section.installBuiltContainer(first);
		LodBufferContainer replacement = buildContainer(pos, 0xff885533);
		doAnswer(invocation -> {
			section.installBuiltContainer(replacement);
			return null;
		}).when(first).close();

		section.retireBuiltContainer();
		assertSame(replacement, section.bufferContainer,
			"retirement must detach the old container before its close can overlap a worker install");
		assertTrue(replacement.rustSemanticBuildLifecycleCurrent());
		section.close();
		assertFalse(replacement.rustSemanticBuildLifecycleCurrent());
	}

	@Test
	void parentRetirementUpdatesRetainedVisibilityBeforeNextBuild() {
		long pos = com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte) 6, 0, 0);
		try (var owner = new net.vulkanic.world.NativeDhVisibility(8, 6, 256, 0, 0)) {
			LodRenderSection section = section(pos);
			section.bindNativeVisibility(owner, owner.event(1, pos, 1, 0));
			section.setRenderingEnabled(true);
			LodBufferContainer first = buildContainer(pos, 0xff557733);
			section.installBuiltContainer(first);
			section.retireBuiltContainer();
			assertEquals(4, owner.inspect(null, 0F, 1F).length);
			assertFalse(first.rustSemanticBuildLifecycleCurrent());
			LodBufferContainer replacement = buildContainer(pos, 0xff885533);
			section.installBuiltContainer(replacement);
			assertEquals(generationOf(replacement), owner.inspect(null, 0F, 1F)[5]);
			section.close();
		}
	}

	private static LodRenderSection section(long pos) {
		return new LodRenderSection(pos, mock(LodQuadTree.class), mock(IDhClientLevel.class), null, new AtomicInteger());
	}

	private static LodBufferContainer buildContainer(long pos, int color) {
		LodQuadBuilder builder = new LodQuadBuilder(false, null);
		builder.addQuadUp((short) 0, (short) 1, (short) 0, (short) 1, (short) 1,
			color, (byte) 1, (byte) 15, (byte) 0);
		LodBufferContainer container = new LodBufferContainer(pos, new DhBlockPos(0, 64, 0));
		assertEquals(container, container.makeAndUploadBuffersAsync(builder).join());
		assertTrue(container.rustSemanticBuildLifecycleCurrent());
		return container;
	}

	private static long generationOf(LodBufferContainer container) {
		long generation = container.rustSemanticColumnGeneration();
		assertTrue(generation > 0L);
		return generation;
	}
}

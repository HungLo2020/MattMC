package net.vulkanic.world;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RustGalWholeFrameTerrainSourceTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void semanticShellAdvancesClientLightStateBeforeTerrainAdmission() throws Exception {
        String shell = Files.readString(ROOT.resolve("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
        String level = Files.readString(ROOT.resolve("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
        int advance = shell.indexOf("this.minecraft.levelRenderer.advanceRustWholeFrameLightState();");
        int terrain = shell.indexOf("enqueueRustGalStaticTerrainForWholeFrame");

        assertTrue(advance >= 0 && terrain > advance,
                "the Rust terrain source must observe light updates from the same semantic frame");
        assertTrue(level.contains("this.level.pollLightUpdates();"));
		assertTrue(level.contains("this.level.hasPendingLightUpdates()"),
				"the Rust terrain bootstrap must drain already-queued client light work before snapshotting terrain");
		assertTrue(level.contains("final int maxDrainPasses = 64;"),
				"the Rust terrain bootstrap must keep client-light draining bounded");
		assertTrue(level.contains("lightEngine.hasLightWork()")
				&& level.contains("lightEngine.runLightUpdates();"),
				"the Rust terrain bootstrap must advance actual light work without re-notifying already-settled light state");
    }

	@Test
	void invalidationsOutsideSemanticBuildDomainCannotBlockTerrainReadiness() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int method = source.indexOf("private void admitInvalidatedSections");
		int nonCandidate = source.indexOf("if (!this.isCandidate(section, frustum))", method);
		int remove = source.indexOf("iterator.remove();", nonCandidate);

		assertTrue(method >= 0 && nonCandidate > method && remove > nonCandidate,
				"out-of-height or otherwise non-candidate dirty sections must leave the Rust terrain readiness domain");
	}

	@Test
	void visibleInvalidationsTransferTheirClaimedQueueGateIntoPendingCpuWork() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int method = source.indexOf("private void admitInvalidatedSections");
		int claim = source.indexOf("!this.queued.add(key)", method);
		int pending = source.indexOf("this.pending.addLast(section);", claim);
		int remove = source.indexOf("iterator.remove();", pending);

		assertTrue(method >= 0 && claim > method && pending > claim && remove > pending,
			"a visible invalidated section claimed by the queued set must also enter the pending CPU-build deque");
	}

	@Test
	void invalidationRevokesCachedTerrainReadinessBeforeTheNextEnqueue() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int invalidation = source.indexOf("public void invalidate(int sectionX, int sectionY, int sectionZ)");
		int surface = source.indexOf("wholeFrameSurfaceQueueDrained = false;", invalidation);
		int terrain = source.indexOf("wholeFrameTerrainQueueDrained = false;", surface);
		int capture = source.indexOf("invalidateRustWholeFrameTerrainReadiness()", terrain);

		assertTrue(invalidation >= 0 && surface > invalidation && terrain > surface && capture > terrain,
			"dirty source data must revoke the prior-frame readiness receipt before deterministic capture can use it");
	}

	@Test
	void renderDistanceChangesResetTheSemanticTerrainFrontier() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int radius = source.indexOf("int horizontalRadius = this.configuredHorizontalRadius();");
		int reset = source.indexOf("horizontalRadius != this.lastHorizontalRadius", radius);
		int clear = source.indexOf("this.propagatedIncomingDirections.clear();", reset);
		int evict = source.indexOf("this.evictOutsideWindow(cameraSection, horizontalRadius);", reset);

		assertTrue(radius >= 0 && reset > radius && clear > reset && evict > clear,
				"a changed effective render distance must discard the old semantic visibility domain before admission");
	}

	@Test
	void submissionUsesOnlyTheCurrentPortalReachedTerrainDomain() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int submission = source.indexOf("var visibleSections = new ArrayList<RenderSection>(this.propagatedIncomingDirections.size() + 26)");
		int reached = source.indexOf("this.propagatedIncomingDirections.containsKey(entry.getLongKey())", submission);
		int enqueue = source.indexOf("RustGalTerrainRenderer.enqueueWholeFrameTerrainSections(visibleSections", submission);

		assertTrue(submission >= 0 && reached > submission && enqueue > reached,
				"the Rust terrain producer must submit the portal-reached semantic domain, not its retained CPU mesh cache");
	}

	@Test
	void submissionIncludesSodiumsSemanticNearbySectionException() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int submission = source.indexOf("var visibleSections = new ArrayList<RenderSection>");
		int nearby = source.indexOf("this.addNearbyVisibleSections(visibleSections, visibleKeys);", submission);
		int method = source.indexOf("private void addNearbyVisibleSections");
		int enlargedBounds = source.indexOf("OcclusionCuller.CHUNK_SECTION_SIZE_NEARBY", method);

		assertTrue(submission >= 0 && nearby > submission && method > nearby && enlargedBounds > method,
				"the Rust-owned semantic source must retain Sodium's enlarged-bounds nearby-section pass");
	}

	@Test
	void visibilityUsesSodiumsExactCpuViewportPredicate() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int visibility = source.indexOf("private boolean isVisible(SectionPos section, Frustum frustum)");
		int viewport = source.indexOf("this.viewport = viewportProvider.sodium$createViewport()", 0);
		int predicate = source.indexOf("currentViewport.isBoxVisible(", visibility);
		int sectionExtent = source.indexOf("OcclusionCuller.CHUNK_SECTION_SIZE", predicate);

		assertTrue(visibility >= 0 && viewport >= 0 && predicate > visibility && sectionExtent > predicate,
				"the Rust terrain visibility graph must use Sodium's exact CPU viewport predicate");
	}

	@Test
	void visibilityUsesSodiumsCylindricalRenderDistanceEnvelope() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int visibility = source.indexOf("private boolean isVisible(SectionPos section, Frustum frustum)");
		int distance = source.indexOf("this.isWithinRenderDistance(currentViewport, section)", visibility);
		int envelope = source.indexOf("this.nearestToZero(originX - 1, originX + 17)", distance);
		int cylindrical = source.indexOf("(deltaX * deltaX) + (deltaZ * deltaZ)", envelope);

		assertTrue(visibility >= 0 && distance > visibility && envelope > distance && cylindrical > envelope,
				"semantic terrain selection must retain Frozen's model-envelope-aware cylindrical distance gate");
	}

	@Test
	void portalTraversalUsesRustCameraAwareOcclusionAfterTheRoot() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int frontier = source.indexOf("private void drainVisibilityFrontier");
		int root = source.indexOf("getVisibilityConnections(section.getVisibilityData(), GraphDirectionSet.NONE, false)", frontier);
		int camera = source.indexOf("getVisibilityConnectionsForCamera(section.getVisibilityData(), incomingDirections", frontier);
		assertTrue(frontier >= 0 && root > frontier && camera > root,
				"the semantic terrain frontier must retain Sodium's root rule and Rust camera-aware portal filtering");
	}

	@Test
	void portalTraversalAppliesTheIndependentCpuAdjacentMaskBeforeNeighborAdmission() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"
		));
		int outward = source.indexOf("outgoing &= this.outwardDirections(sectionPos);");
		int adjacent = source.indexOf("outgoing &= this.residentAdjacentMask(sectionPos);", outward);
		int admission = source.indexOf("this.admitSection(neighbor", adjacent);
		assertTrue(adjacent > outward);
		assertTrue(admission > adjacent,
			"the independent source must constrain portals before admitting neighbors");
		assertTrue(source.contains("private int residentAdjacentMask(SectionPos section)"));
		assertTrue(source.contains("this.level.hasChunk(neighbor.getX(), neighbor.getZ())"));
	}

	@Test
	void portalTraversalMergesEachBreadthFirstWaveBeforeExpandingIt() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int readQueue = source.indexOf("private final ArrayDeque<SectionPos> propagationRead");
		int frontier = source.indexOf("private void drainVisibilityFrontier");
		int snapshot = source.indexOf("while ((queuedSection = this.propagationPending.pollFirst()) != null)", frontier);
		int read = source.indexOf("while ((sectionPos = this.propagationRead.pollFirst()) != null)", frontier);

		assertTrue(readQueue >= 0 && frontier > readQueue && snapshot > frontier && read > snapshot,
				"portal neighbors must enter a separate write wave so their incoming directions merge before expansion");
	}

	@Test
	void portalTraversalDoesNotReopenASectionAfterItsWaveWasConsumed() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int admitted = source.indexOf("if (this.sections.containsKey(key))");
		int finalVisitGuard = source.indexOf("if (!this.propagatedIncomingDirections.containsKey(key))", admitted);
		int request = source.indexOf("this.requestPropagation(key);", finalVisitGuard);

		assertTrue(admitted >= 0 && finalVisitGuard > admitted && request > finalVisitGuard,
				"a completed semantic section must merge same-wave portals but never reopen after its Frozen-equivalent BFS visit");
	}

	@Test
	void quiescentResidentTerrainRebuildsTheFrameLocalPortalGraph() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int refreshCondition = source.indexOf("else if (this.canRefreshResidentVisibility())");
		int reset = source.indexOf("this.resetVisibilityFrontier();", refreshCondition);
		int fullDrain = source.indexOf("this.drainVisibilityFrontierFully(frustum);", reset);
		int method = source.indexOf("private void drainVisibilityFrontierFully");
		int loop = source.indexOf("while (!this.propagationRead.isEmpty() || !this.propagationPending.isEmpty())", method);

		assertTrue(refreshCondition >= 0 && reset > refreshCondition && fullDrain > reset && method > fullDrain && loop > method,
				"a quiescent independent source must rebuild and fully drain its own frame-local portal graph");
	}

	@Test
	void wholeFrameBuildsRetainAnimatedSpriteProvenanceBeforeRustAdmission() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int completion = source.indexOf("private void drainCompletedBuilds");
		int provenance = source.indexOf("StaticTerrainParityDiagnostics.recordChunkBuildOutput(output)", completion);
		int admission = source.indexOf("RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(output)", completion);

		assertTrue(completion >= 0 && provenance > completion && admission > provenance,
				"whole-frame terrain must retain immutable mesh sprite provenance before Rust takes the semantic payload");
	}

	@Test
	void managerDoesNotRaceWholeFrameSourceForRustTerrainAdmission() throws Exception {
		String manager = Files.readString(ROOT.resolve("src/main/java/net/sodium/client/render/chunk/RenderSectionManager.java"));
		int wholeFrame = manager.indexOf("if (!rustWholeFrame)");
		int managerAdmission = manager.indexOf("RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(chunkBuildOutput)");
		int bookkeeping = manager.indexOf("this.updateSectionInfo(result.render, chunkBuildOutput.info)", wholeFrame);

		assertTrue(wholeFrame >= 0 && managerAdmission < 0 && bookkeeping > wholeFrame,
				"only the independent semantic source may admit whole-frame Rust terrain; the manager may retain CPU bookkeeping");
	}

	@Test
	void portalTraversalTraceObservesFinalMasksWithoutOwningTraversal() throws Exception {
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		int frontier = source.indexOf("private void drainVisibilityFrontier");
		int beforeMask = source.indexOf("int outgoingBeforeOutwardMask = outgoing", frontier);
		int outwardMask = source.indexOf("outgoing &= this.outwardDirections(sectionPos)", frontier);
		int trace = source.indexOf("StaticTerrainParityDiagnostics.recordPortalTraversal", frontier);

		assertTrue(frontier >= 0 && beforeMask > frontier && outwardMask > beforeMask && trace > outwardMask,
				"portal tracing must observe finalized semantic masks after culling, without affecting their calculation");
	}

	@Test
	void terrainSelectionDistanceMatchesFrozensOpaqueFogRule() {
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 256.0f, true));
		assertEquals(48.5f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 48.0f, true));
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 0.5f, 48.0f, true));
		assertEquals(128.0f,
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(128.0f, 1.0f, 48.0f, false));
	}

	@Test
	void semanticTerrainCullingReceivesTheVanillaFogRecord() throws Exception {
		String shell = Files.readString(ROOT.resolve("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String level = Files.readString(ROOT.resolve("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String source = Files.readString(ROOT.resolve("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));

		assertTrue(shell.contains("wholeFrameFog") && shell.contains("useFogOcclusion"),
			"the semantic frame must pass vanilla fog and the matching user culling option to terrain admission");
		assertTrue(level.contains("RustGalWholeFrameTerrainSource.terrainSelectionDistance("),
			"the LevelRenderer boundary must reduce the fog record to explicit terrain-selection distance");
		assertTrue(source.contains("this.terrainSelectionDistance")
			&& source.contains("Mth.equal(fogAlpha, 1.0f)"),
			"the Rust-owned source must apply Frozen's fully-opaque-fog culling rule without renderer state");
	}

}

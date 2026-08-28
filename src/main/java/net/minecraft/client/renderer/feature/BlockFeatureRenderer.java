package net.minecraft.client.renderer.feature;

import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;

@Environment(EnvType.CLIENT)
public class BlockFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();

	public void render(
		SubmitNodeCollection submitNodeCollection,
		MultiBufferSource.BufferSource bufferSource,
		BlockRenderDispatcher blockRenderDispatcher,
		OutlineBufferSource outlineBufferSource
	) {
		this.render(submitNodeCollection, bufferSource, blockRenderDispatcher, outlineBufferSource, false);
	}

	/**
	 * Dispatches the extracted block-feature queue. The semantic-only mode is
	 * used by the Rust frame collector: route branches copy explicit mesh data,
	 * while Java compatibility draws remain unavailable to the presenter.
	 */
	public void render(
		SubmitNodeCollection submitNodeCollection,
		MultiBufferSource.BufferSource bufferSource,
		BlockRenderDispatcher blockRenderDispatcher,
		OutlineBufferSource outlineBufferSource,
		boolean semanticOnly
	) {
		boolean vulkanSelected = net.vulkanic.VulkanicAPI.isVulkanBackendSelected();
		boolean rustWholeFrame = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
		if ((vulkanSelected && !rustWholeFrame) || ((vulkanSelected || rustWholeFrame) && !semanticOnly)) {
			throw new IllegalStateException(
				vulkanSelected && !rustWholeFrame
					? "Java block-feature rendering is unavailable while Rust owns whole-frame presentation; selected Vulkan route is unavailable until Rust whole-frame admission"
					: "Java block-feature rendering is unavailable while Rust owns whole-frame presentation"
			);
		}
		if (semanticOnly && net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& (!WorldRenderRoutePolicy.currentFallingBlockRoute().usesRustWholeFrameVulkan()
				|| !WorldRenderRoutePolicy.currentPistonMovingBlockRoute().usesRustWholeFrameVulkan()
				|| !WorldRenderRoutePolicy.currentBlockDisplayRoute().usesRustWholeFrameVulkan()
				|| !WorldRenderRoutePolicy.currentPrimedTntRoute().usesRustWholeFrameVulkan()
				|| !WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan())) {
			throw new IllegalStateException(
				"Rust semantic block-feature collection requires complete Rust ownership for every block-feature family"
			);
		}
		for (SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit : submitNodeCollection.getMovingBlockSubmits()) {
			MovingBlockRenderState movingBlockRenderState = movingBlockSubmit.movingBlockRenderState();
			BlockState blockState = movingBlockRenderState.blockState;
			WorldRenderRoutePolicy.Route fallingBlockRoute = WorldRenderRoutePolicy.currentFallingBlockRoute();
			boolean fallingBlock = movingBlockSubmit.source() == SubmitNodeStorage.MovingBlockSubmitSource.FALLING_BLOCK;
			boolean piston = movingBlockSubmit.source() == SubmitNodeStorage.MovingBlockSubmitSource.PISTON;
			WorldRenderRoutePolicy.Route pistonRoute = WorldRenderRoutePolicy.currentPistonMovingBlockRoute();
			if (fallingBlock && this.routeMovingBlock(
				blockRenderDispatcher,
				movingBlockSubmit,
				blockState,
				fallingBlockRoute,
				"falling-block"
			)) {
				continue;
			}
			if (piston && this.routeMovingBlock(
				blockRenderDispatcher,
				movingBlockSubmit,
				blockState,
				pistonRoute,
				"piston"
			)) {
				continue;
			}
			if ((VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) && !fallingBlock && !piston) {
				WorldRenderRoutePolicy.Route unknownRoute = WorldRenderRoutePolicy.currentMaterialRoute();
				if (unknownRoute.usesRustWholeFrameVulkan()
					&& RustGalWorldPrimitiveRenderer.enqueueUnknownMovingBlock(blockRenderDispatcher, movingBlockSubmit)) {
					this.recordMovingBlockRoute("moving-block", "rust-vulkan-whole-frame", blockState, true, true, false);
					continue;
				}
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"moving-block", "rust-vulkan-unavailable:" + blockState.getBlockHolder().getRegisteredName()
				);
				if (WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan()) {
					throw new IllegalStateException(
						"Rust whole-frame moving-block route has no semantic source for "
							+ blockState.getBlockHolder().getRegisteredName()
					);
				}
				continue;
			}
			List<BlockModelPart> list = blockRenderDispatcher.getBlockModel(blockState)
				.collectParts(RandomSource.create(blockState.getSeed(movingBlockRenderState.randomSeedPos)));
			PoseStack poseStack = new PoseStack();
			poseStack.mulPose(movingBlockSubmit.pose());
			blockRenderDispatcher.getModelRenderer()
				.tesselateBlock(
					movingBlockRenderState,
					list,
					blockState,
					movingBlockRenderState.blockPos,
					poseStack,
					bufferSource.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(blockState)),
					false,
					OverlayTexture.NO_OVERLAY
				);
				if (fallingBlock) {
					RustGalWorldPrimitiveRenderer.recordFallingBlockRouteDecision(
						"java-legacy",
						blockState,
					false,
					false,
					true
				);
				GraphicsFrameBenchmark.recordFallingBlockRouteDecision("java-legacy", blockState);
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"falling-block",
						"java-legacy:" + blockState.getBlockHolder().getRegisteredName()
					);
				}
				if (piston) {
					RustGalWorldPrimitiveRenderer.recordMovingBlockRouteDecision(
						"piston",
						"java-legacy",
						blockState,
						false,
						false,
						true
					);
					GraphicsFrameBenchmark.recordMovingBlockRouteDecision("piston", "java-legacy", blockState);
					GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
						"piston",
						"java-legacy:" + blockState.getBlockHolder().getRegisteredName()
					);
				}
			}
				this.renderOpenGlPendingMeshInstancesInCurrentScope("minecraft.world.moving-block");

		for (SubmitNodeStorage.BlockSubmit blockSubmit : submitNodeCollection.getBlockSubmits()) {
			this.poseStack.pushPose();
			this.poseStack.last().set(blockSubmit.pose());
			if (blockSubmit.source() == SubmitNodeStorage.BlockSubmitSource.PRIMED_TNT) {
				this.routePrimedTntBlock(blockRenderDispatcher, bufferSource, outlineBufferSource, blockSubmit);
			} else {
				WorldRenderRoutePolicy.Route blockDisplayRoute = WorldRenderRoutePolicy.currentBlockDisplayRoute();
				if (blockDisplayRoute == WorldRenderRoutePolicy.Route.DISABLED) {
					GraphicsFrameBenchmark.recordSubmittedWorkIdentity("block-display", "disabled:" + blockSubmit.state().getBlockHolder().getRegisteredName());
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
						throw new IllegalStateException("Rust whole-frame block-display route is unavailable while Rust owns presentation");
					}
				} else {
					boolean rustBlockDisplayQueued = RustGalWorldPrimitiveRenderer.enqueueBlockDisplay(
						blockRenderDispatcher, blockSubmit,
						net.vulkanic.VulkanicAPI.isVulkanBackendSelected());
					if (!rustBlockDisplayQueued && blockDisplayRoute.usesRustWholeFrameVulkan()) {
						rustBlockDisplayQueued = RustGalWorldPrimitiveRenderer.enqueueBlockDisplay(
							blockRenderDispatcher, blockSubmit, true);
					}
					if (!rustBlockDisplayQueued && !blockDisplayRoute.usesRustWholeFrameVulkan()) {
					GraphicsFrameBenchmark.recordSubmittedWorkIdentity("block-display", "java-legacy:" + blockSubmit.state().getBlockHolder().getRegisteredName());
					this.renderJavaBlockSubmit(blockRenderDispatcher, bufferSource, outlineBufferSource, blockSubmit);
					} else if (!rustBlockDisplayQueued && blockDisplayRoute.usesRustWholeFrameVulkan()) {
					GraphicsFrameBenchmark.recordSubmittedWorkIdentity("block-display", "rust-vulkan-unavailable:" + blockSubmit.state().getBlockHolder().getRegisteredName());
					throw new IllegalStateException(
						"Rust whole-frame block-display route has no semantic mesh for "
							+ blockSubmit.state().getBlockHolder().getRegisteredName()
							+ " (reason=" + RustGalWorldPrimitiveRenderer.lastBlockDisplayAdmissionFailure() + ")"
					);
				}
				}
			}

			this.poseStack.popPose();
		}
			this.renderOpenGlPendingMeshInstancesInCurrentScope("minecraft.entity.block-display");

		for (SubmitNodeStorage.BlockModelSubmit blockModelSubmit : submitNodeCollection.getBlockModelSubmits()) {
			WorldRenderRoutePolicy.Route blockModelRoute = WorldRenderRoutePolicy.currentMaterialRoute();
			if (blockModelRoute.usesRustWholeFrameVulkan()) {
				boolean queued = RustGalWorldPrimitiveRenderer.enqueueBlockModelMesh(blockModelSubmit);
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"block-model", queued ? "rust-vulkan-whole-frame" : "rust-vulkan-unavailable"
				);
				if (!queued) {
					throw new IllegalStateException("Rust whole-frame block-model route has no semantic mesh");
				}
				continue;
			}
			if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				&& !blockModelRoute.usesJavaCompatibility()) {
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"block-model", "rust-vulkan-unavailable"
				);
				if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
					|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
					throw new IllegalStateException("Rust whole-frame block-model route is unavailable while Rust owns presentation");
				}
				continue;
			}
			if (!blockModelRoute.usesRustWholeFrameVulkan()) {
				ModelBlockRenderer.renderModel(
					blockModelSubmit.pose(),
					bufferSource.getBuffer(blockModelSubmit.renderType()),
					blockModelSubmit.model(),
					blockModelSubmit.r(),
					blockModelSubmit.g(),
					blockModelSubmit.b(),
					blockModelSubmit.lightCoords(),
					blockModelSubmit.overlayCoords()
				);
				if (blockModelSubmit.outlineColor() != 0) {
					outlineBufferSource.setColor(blockModelSubmit.outlineColor());
					ModelBlockRenderer.renderModel(
						blockModelSubmit.pose(),
						outlineBufferSource.getBuffer(blockModelSubmit.renderType()),
						blockModelSubmit.model(),
						blockModelSubmit.r(),
						blockModelSubmit.g(),
						blockModelSubmit.b(),
						blockModelSubmit.lightCoords(),
						blockModelSubmit.overlayCoords()
					);
				}
			}
		}
	}

	private void routePrimedTntBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		SubmitNodeStorage.BlockSubmit blockSubmit
	) {
		String blockIdentity = blockSubmit.state().getBlockHolder().getRegisteredName();
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentPrimedTntRoute();
		if (route == WorldRenderRoutePolicy.Route.DISABLED) {
			RustGalWorldPrimitiveRenderer.recordMovingBlockRouteDecision(
				"primed-tnt", "disabled", blockSubmit.state(), false, false, false
			);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity("primed-tnt", "disabled:" + blockIdentity);
			if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame Primed TNT route is unavailable while Rust owns presentation");
			}
			return;
		}
		boolean eligible = RustGalWorldPrimitiveRenderer.isPrimedTntMeshEligible(blockSubmit);
		if ((!eligible || route == WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY)
			&& !route.usesRustWholeFrameVulkan()) {
			RustGalWorldPrimitiveRenderer.recordMovingBlockRouteDecision(
				"primed-tnt", "java-legacy", blockSubmit.state(), false, false, true
			);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity("primed-tnt", "java-legacy:" + blockIdentity);
			this.renderJavaBlockSubmit(blockRenderDispatcher, bufferSource, outlineBufferSource, blockSubmit);
			return;
		}
		if (!eligible) {
			RustGalWorldPrimitiveRenderer.recordMovingBlockRouteDecision(
				"primed-tnt", "rust-vulkan-unavailable", blockSubmit.state(), false, false, false
			);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity("primed-tnt", "rust-vulkan-unavailable:" + blockIdentity);
			throw new IllegalStateException("Rust whole-frame Primed TNT route has no semantic mesh for " + blockIdentity);
		}
		if (!RustGalWorldPrimitiveRenderer.enqueuePrimedTntBlock(blockRenderDispatcher, blockSubmit)) {
			throw new IllegalStateException("Rust Primed TNT route selected without a submitted mesh instance");
		}
		RustGalWorldPrimitiveRenderer.recordMovingBlockRouteDecision(
			"primed-tnt",
			route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
			blockSubmit.state(),
			true,
			true,
			false
		);
		GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
			"primed-tnt",
			"rust:" + blockIdentity
		);
	}

	private void renderJavaBlockSubmit(
		BlockRenderDispatcher blockRenderDispatcher,
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		SubmitNodeStorage.BlockSubmit blockSubmit
	) {
		blockRenderDispatcher.renderSingleBlock(blockSubmit.state(), this.poseStack, bufferSource, blockSubmit.lightCoords(), blockSubmit.overlayCoords());
		if (blockSubmit.outlineColor() != 0) {
			outlineBufferSource.setColor(blockSubmit.outlineColor());
			blockRenderDispatcher.renderSingleBlock(blockSubmit.state(), this.poseStack, outlineBufferSource, blockSubmit.lightCoords(), blockSubmit.overlayCoords());
		}
	}

	private boolean routeMovingBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit,
		BlockState blockState,
		WorldRenderRoutePolicy.Route route,
		String provenance
	) {
		if (route == WorldRenderRoutePolicy.Route.DISABLED) {
			this.recordMovingBlockRoute(provenance, "disabled", blockState, false, false, false);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
				provenance,
				"disabled:" + this.blockIdentity(blockState)
			);
			if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame " + provenance + " route is unavailable while Rust owns presentation");
			}
			return true;
		}
		boolean queued = "falling-block".equals(provenance)
			? RustGalWorldPrimitiveRenderer.enqueueFallingBlock(blockRenderDispatcher, movingBlockSubmit)
			: RustGalWorldPrimitiveRenderer.enqueuePistonMovingBlock(blockRenderDispatcher, movingBlockSubmit);
		if (!queued) {
			if (route.usesRustWholeFrameVulkan()) {
				this.recordMovingBlockRoute(provenance, "rust-vulkan-unavailable", blockState, false, false, false);
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					provenance,
					"rust-vulkan-unavailable:" + this.blockIdentity(blockState)
				);
				// A selected Rust whole-frame route must never claim ownership of
				// work that failed semantic extraction. Returning true here used to
				// omit the moving block while still presenting the frame. Abort the
				// route explicitly; Java rendering is not a same-frame fallback.
				throw new IllegalStateException(
					"Rust Vulkan whole-frame " + provenance
						+ " route selected but semantic mesh extraction was unavailable for "
						+ this.blockIdentity(blockState)
				);
			}
			return false;
		}
		String rustRoute = route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl";
		this.recordMovingBlockRoute(provenance, rustRoute, blockState, true, true, false);
		return true;
	}

	private void recordMovingBlockRoute(
		String provenance,
		String route,
		BlockState blockState,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		if ("falling-block".equals(provenance)) {
			RustGalWorldPrimitiveRenderer.recordFallingBlockRouteDecision(route, blockState, rustSelected, rustQueued, javaDrawn);
			GraphicsFrameBenchmark.recordFallingBlockRouteDecision(route, blockState);
		} else {
			RustGalWorldPrimitiveRenderer.recordMovingBlockRouteDecision(provenance, route, blockState, rustSelected, rustQueued, javaDrawn);
			GraphicsFrameBenchmark.recordMovingBlockRouteDecision(provenance, route, blockState);
		}
	}

	private String blockIdentity(BlockState blockState) {
		return blockState == null ? "missing" : blockState.getBlockHolder().getRegisteredName();
	}

	private void renderOpenGlPendingMeshInstancesInCurrentScope(String producerLabel) {
		if (!WorldRenderRoutePolicy.currentBlockDisplayRoute().usesRustOpenGl()
			&& !WorldRenderRoutePolicy.currentFallingBlockRoute().usesRustOpenGl()
			&& !WorldRenderRoutePolicy.currentPistonMovingBlockRoute().usesRustOpenGl()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int drawFramebuffer = VulkanicAPI.getDrawFramebufferBinding();
		if (drawFramebuffer != 0) {
			try (RenderPass ignored = VulkanicAPI.createRenderPass(
				() -> "Rust GAL indexed world mesh",
				drawFramebuffer,
				minecraft.getMainRenderTarget().useDepth
			)) {
				RustGalWorldPrimitiveRenderer.renderOpenGlPendingMeshInstances(minecraft, producerLabel);
			}
			return;
		}
		RenderTarget target = minecraft.getMainRenderTarget();
		try (RenderPass ignored = VulkanicAPI.createRenderPass(
			() -> "Rust GAL indexed world mesh",
			target.getColorTextureView(),
			OptionalInt.empty(),
			target.useDepth ? target.getDepthTextureView() : null,
			OptionalDouble.empty()
		)) {
			RustGalWorldPrimitiveRenderer.renderOpenGlPendingMeshInstances(minecraft, producerLabel);
		}
	}
}

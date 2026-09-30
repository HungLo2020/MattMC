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

	public void render(
		SubmitNodeCollection submitNodeCollection,
		MultiBufferSource.BufferSource bufferSource,
		BlockRenderDispatcher blockRenderDispatcher,
		OutlineBufferSource outlineBufferSource,
		boolean semanticOnly
	) {
		if ((!semanticOnly)) {
			throw new IllegalStateException(
				"Java block-feature rendering is unavailable while Rust owns whole-frame presentation"
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
			if (!fallingBlock && !piston) {
				if (RustGalWorldPrimitiveRenderer.enqueueUnknownMovingBlock(blockRenderDispatcher, movingBlockSubmit)) {
					this.recordMovingBlockRoute("moving-block", "rust-vulkan-whole-frame", blockState, true, true, false);
					continue;
				}
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"moving-block", "rust-vulkan-unavailable:" + blockState.getBlockHolder().getRegisteredName()
				);
				throw new IllegalStateException(
					"Rust whole-frame moving-block route has no semantic source for "
						+ blockState.getBlockHolder().getRegisteredName()
				);
			}
		}

		for (SubmitNodeStorage.BlockSubmit blockSubmit : submitNodeCollection.getBlockSubmits()) {
			this.poseStack.pushPose();
			this.poseStack.last().set(blockSubmit.pose());
			if (blockSubmit.source() == SubmitNodeStorage.BlockSubmitSource.PRIMED_TNT) {
				this.routePrimedTntBlock(blockRenderDispatcher, bufferSource, outlineBufferSource, blockSubmit);
			} else {
					boolean rustBlockDisplayQueued = RustGalWorldPrimitiveRenderer.enqueueBlockDisplay(
						blockRenderDispatcher, blockSubmit,
						true);
					if (!rustBlockDisplayQueued) {
						rustBlockDisplayQueued = RustGalWorldPrimitiveRenderer.enqueueBlockDisplay(
							blockRenderDispatcher, blockSubmit, true);
					}
					if (!rustBlockDisplayQueued) {
					GraphicsFrameBenchmark.recordSubmittedWorkIdentity("block-display", "rust-vulkan-unavailable:" + blockSubmit.state().getBlockHolder().getRegisteredName());
					throw new IllegalStateException(
						"Rust whole-frame block-display route has no semantic mesh for "
							+ blockSubmit.state().getBlockHolder().getRegisteredName()
							+ " (reason=" + RustGalWorldPrimitiveRenderer.lastBlockDisplayAdmissionFailure() + ")"
					);
				}
			}

			this.poseStack.popPose();
		}
			this.renderOpenGlPendingMeshInstancesInCurrentScope("minecraft.entity.block-display");

		for (SubmitNodeStorage.BlockModelSubmit blockModelSubmit : submitNodeCollection.getBlockModelSubmits()) {
			boolean queued = RustGalWorldPrimitiveRenderer.enqueueBlockModelMesh(blockModelSubmit);
			GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
				"block-model", queued ? "rust-vulkan-whole-frame" : "rust-vulkan-unavailable"
			);
			if (!queued) {
				throw new IllegalStateException("Rust whole-frame block-model route has no semantic mesh");
			}
			continue;
		}
	}

	private void routePrimedTntBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		MultiBufferSource.BufferSource bufferSource,
		OutlineBufferSource outlineBufferSource,
		SubmitNodeStorage.BlockSubmit blockSubmit
	) {
		String blockIdentity = blockSubmit.state().getBlockHolder().getRegisteredName();
		boolean eligible = RustGalWorldPrimitiveRenderer.isPrimedTntMeshEligible(blockSubmit);
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
			"rust-vulkan-whole-frame",
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
			throw new IllegalStateException("Rust whole-frame " + provenance + " route is unavailable while Rust owns presentation");
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
		return;
	}
}

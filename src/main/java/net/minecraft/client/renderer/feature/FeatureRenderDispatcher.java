package net.minecraft.client.renderer.feature;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.AtlasManager;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;

@Environment(EnvType.CLIENT)
public class FeatureRenderDispatcher implements AutoCloseable {
	private final SubmitNodeStorage submitNodeStorage;
	private final BlockRenderDispatcher blockRenderDispatcher;
	private final MultiBufferSource.BufferSource bufferSource;
	private final AtlasManager atlasManager;
	private final OutlineBufferSource outlineBufferSource;
	private final MultiBufferSource.BufferSource crumblingBufferSource;
	private final Font font;
	private final ShadowFeatureRenderer shadowFeatureRenderer = new ShadowFeatureRenderer();
	private final FlameFeatureRenderer flameFeatureRenderer = new FlameFeatureRenderer();
	private final ModelFeatureRenderer modelFeatureRenderer = new ModelFeatureRenderer();
	private final ModelPartFeatureRenderer modelPartFeatureRenderer = new ModelPartFeatureRenderer();
	private final NameTagFeatureRenderer nameTagFeatureRenderer = new NameTagFeatureRenderer();
	private final TextFeatureRenderer textFeatureRenderer = new TextFeatureRenderer();
	private final HitboxFeatureRenderer hitboxFeatureRenderer = new HitboxFeatureRenderer();
	private final LeashFeatureRenderer leashFeatureRenderer = new LeashFeatureRenderer();
	private final ItemFeatureRenderer itemFeatureRenderer = new ItemFeatureRenderer();
	private final CustomFeatureRenderer customFeatureRenderer = new CustomFeatureRenderer();
	private final BlockFeatureRenderer blockFeatureRenderer = new BlockFeatureRenderer();
	public final ParticleFeatureRenderer particleFeatureRenderer = new ParticleFeatureRenderer(); // Made public for Iris particle rendering integration

	public FeatureRenderDispatcher(
		SubmitNodeStorage submitNodeStorage,
		BlockRenderDispatcher blockRenderDispatcher,
		MultiBufferSource.BufferSource bufferSource,
		AtlasManager atlasManager,
		OutlineBufferSource outlineBufferSource,
		MultiBufferSource.BufferSource bufferSource2,
		Font font
	) {
		this.submitNodeStorage = submitNodeStorage;
		this.blockRenderDispatcher = blockRenderDispatcher;
		this.bufferSource = bufferSource;
		this.atlasManager = atlasManager;
		this.outlineBufferSource = outlineBufferSource;
		this.crumblingBufferSource = bufferSource2;
		this.font = font;
	}

	public void renderAllFeatures() {
		for (SubmitNodeCollection submitNodeCollection : this.submitNodeStorage.getSubmitsPerOrder().values()) {
			if (WorldRenderRoutePolicy.currentEntityShadowRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityShadowSemantics(submitNodeCollection.getShadowSubmits());
			} else if (WorldRenderRoutePolicy.currentEntityShadowRoute().usesJavaCompatibility()) {
				this.shadowFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			}
			this.modelFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource, this.crumblingBufferSource);
			this.modelPartFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource, this.crumblingBufferSource);
			if (WorldRenderRoutePolicy.currentEntityFlameRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityFlameSemantics(submitNodeCollection.getFlameSubmits(), this.atlasManager);
			} else if (!WorldRenderRoutePolicy.currentEntityFlameRoute().equals(WorldRenderRoutePolicy.Route.DISABLED)) {
				this.flameFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.atlasManager);
			}
			if (WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()) {
				var text = RustGalWorldPrimitiveRenderer.collectWorldTextSemantics(
					submitNodeCollection.getNameTagSubmits().semanticSnapshot(), this.font
				);
				if (!text.fullySupported()) {
					throw new IllegalStateException(
						"Rust world-text route selected but name-tag semantic extraction was unsupported"
					);
				}
				var ordinaryText = RustGalWorldPrimitiveRenderer.collectWorldTextSemantics(
					submitNodeCollection.getTextSubmits(), this.font
				);
				if (!ordinaryText.fullySupported()) {
					throw new IllegalStateException(
						"Rust world-text route selected but ordinary text requires unsupported outline or polygon-offset semantics"
					);
				}
			} else if (!WorldRenderRoutePolicy.currentWorldTextRoute().equals(WorldRenderRoutePolicy.Route.DISABLED)) {
				this.nameTagFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.font);
				this.textFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			}
			this.hitboxFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			if (WorldRenderRoutePolicy.currentEntityLeashRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityLeashSemantics(submitNodeCollection.getLeashSubmits());
			} else if (WorldRenderRoutePolicy.currentEntityLeashRoute().usesJavaCompatibility()) {
				this.leashFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			}
			this.itemFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource);
			this.blockFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource);
			this.customFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			this.particleFeatureRenderer.render(submitNodeCollection);
		}

		this.submitNodeStorage.clear();
	}

	/**
	 * Collects copied world-text semantics from the real extracted submit lists for the
	 * Rust-owned whole-frame route. This intentionally performs no Java draw
	 * and does not clear the lists; the normal block-only whole-frame dispatcher
	 * remains responsible for the selected indexed-mesh producer work.
	 */
	public void collectRustWorldTextSemanticsForWholeFrame() {
		this.collectRustWorldTextSemanticsForWholeFrame(this.submitNodeStorage);
	}

	/**
	 * Consumes an isolated name-tag and ordinary-text submit queue built by real
	 * entity and block-entity callbacks. Keeping it separate from the
	 * block-feature queue prevents semantic extraction from retaining unrelated
	 * Java draw work.
	 */
	public void collectRustWorldTextSemanticsForWholeFrame(SubmitNodeStorage textSubmitStorage) {
		if (!WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		for (SubmitNodeCollection submitNodeCollection : textSubmitStorage.getSubmitsPerOrder().values()) {
			var text = RustGalWorldPrimitiveRenderer.collectWorldTextSemantics(
				submitNodeCollection.getNameTagSubmits().semanticSnapshot(), this.font
			);
			if (!text.fullySupported()) {
				throw new IllegalStateException(
					"Rust world-text route selected but real name-tag semantic extraction was unsupported"
				);
			}
			var ordinaryText = RustGalWorldPrimitiveRenderer.collectWorldTextSemantics(
				submitNodeCollection.getTextSubmits(), this.font
			);
			if (!ordinaryText.fullySupported()) {
				throw new IllegalStateException(
					"Rust world-text route selected but real ordinary text requires unsupported outline or polygon-offset semantics"
				);
			}
		}
	}

	public void renderBlockFeaturesOnly() {
		for (SubmitNodeCollection submitNodeCollection : this.submitNodeStorage.getSubmitsPerOrder().values()) {
			if (WorldRenderRoutePolicy.currentEntityShadowRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityShadowSemantics(submitNodeCollection.getShadowSubmits());
			}
			if (WorldRenderRoutePolicy.currentEntityFlameRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityFlameSemantics(submitNodeCollection.getFlameSubmits(), this.atlasManager);
			}
			if (WorldRenderRoutePolicy.currentEntityLeashRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityLeashSemantics(submitNodeCollection.getLeashSubmits());
			}
			this.blockFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource);
		}

		this.submitNodeStorage.clear();
	}

	public void endFrame() {
		this.particleFeatureRenderer.endFrame();
	}

	public SubmitNodeStorage getSubmitNodeStorage() {
		return this.submitNodeStorage;
	}

	public void close() {
		this.particleFeatureRenderer.close();
	}
}

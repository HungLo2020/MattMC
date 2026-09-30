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

	/** Copies debug hitbox boxes and view vectors into Rust-owned line semantics. */
	public void collectRustHitboxSemantics(SubmitNodeStorage hitboxSubmitStorage) {
		for (SubmitNodeCollection collection : hitboxSubmitStorage.getSubmitsPerOrder().values()) {
			for (SubmitNodeStorage.HitboxSubmit submit : collection.getHitboxSubmits()) {
				org.joml.Matrix4f pose = new org.joml.Matrix4f(submit.pose());
				for (net.minecraft.client.renderer.entity.state.HitboxRenderState box : submit.hitboxesRenderState().hitboxes()) {
					float x0 = (float)(box.x0() + box.offsetX()), y0 = (float)(box.y0() + box.offsetY()), z0 = (float)(box.z0() + box.offsetZ());
					float x1 = (float)(box.x1() + box.offsetX()), y1 = (float)(box.y1() + box.offsetY()), z1 = (float)(box.z1() + box.offsetZ());
					float[] edges = {
						x0,y0,z0, x1,y0,z0,  x1,y0,z0, x1,y0,z1,  x1,y0,z1, x0,y0,z1,  x0,y0,z1, x0,y0,z0,
						x0,y1,z0, x1,y1,z0,  x1,y1,z0, x1,y1,z1,  x1,y1,z1, x0,y1,z1,  x0,y1,z1, x0,y1,z0,
						x0,y0,z0, x0,y1,z0,  x1,y0,z0, x1,y1,z0,  x1,y0,z1, x1,y1,z1,  x0,y0,z1, x0,y1,z1
					};
					int color = net.minecraft.util.ARGB.colorFromFloat(1.0F, box.red(), box.green(), box.blue());
					if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(pose, edges, color, 1.0F)) {
						throw new IllegalStateException("Rust debug-line route selected without a semantic line stream");
					}
				}
				var view = submit.hitboxesRenderState();
				float[] viewLine = {0.0F, submit.entityRenderState().eyeHeight, 0.0F, (float)view.viewX(), (float)view.viewY(), (float)view.viewZ()};
				if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(pose, viewLine, 0xff0000ff, 1.0F)) {
					throw new IllegalStateException("Rust debug-line route selected without a view-vector stream");
				}
			}
		}
	}

	/** Fails closed when debug hitboxes exist but their Rust line route is disabled. */
	public void validateRustHitboxRoute(SubmitNodeStorage hitboxSubmitStorage) {
		collectRustHitboxSemantics(hitboxSubmitStorage);
		return;
	}

	public void renderBlockFeaturesOnly() {
		// The whole-frame collector calls this method after semantic entity and
		// block-feature submission.  Dispatch each feature through its route
		// policy: Rust-owned families are copied into the pending semantic frame,
		// while Java compatibility remains available only outside Rust Vulkan.
		for (SubmitNodeCollection submitNodeCollection : this.submitNodeStorage.getSubmitsPerOrder().values()) {
			RustGalWorldPrimitiveRenderer.collectEntityShadowSemantics(submitNodeCollection.getShadowSubmits());
			RustGalWorldPrimitiveRenderer.collectEntityFlameSemantics(submitNodeCollection.getFlameSubmits(), this.atlasManager);
			RustGalWorldPrimitiveRenderer.collectEntityLeashSemantics(submitNodeCollection.getLeashSubmits());
			this.blockFeatureRenderer.render(
				submitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource, true
			);
		}

		this.submitNodeStorage.clear();
	}

	public void endFrame() {
		return;
	}

	public SubmitNodeStorage getSubmitNodeStorage() {
		return this.submitNodeStorage;
	}

	public void close() {
		this.particleFeatureRenderer.close();
	}
}

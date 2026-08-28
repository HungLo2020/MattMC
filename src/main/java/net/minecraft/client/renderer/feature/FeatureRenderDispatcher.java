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
	private static boolean rustPresenterActive() {
		return net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
	}

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
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java feature rendering is unavailable while Rust owns whole-frame presentation");
		}
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Java Vulkan feature rendering is unavailable until the Rust whole-frame entity route is admitted");
		}
		for (SubmitNodeCollection submitNodeCollection : this.submitNodeStorage.getSubmitsPerOrder().values()) {
			if (WorldRenderRoutePolicy.currentEntityShadowRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityShadowSemantics(submitNodeCollection.getShadowSubmits());
			} else if (WorldRenderRoutePolicy.currentEntityShadowRoute().usesJavaCompatibility()) {
				this.shadowFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !submitNodeCollection.getShadowSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame entity-shadow route is unavailable while Rust owns presentation");
			}
			if (!rustPresenterActive()
				|| WorldRenderRoutePolicy.currentMaterialRoute().usesJavaCompatibility()) {
				// Rust-owned model/model-part submissions have already crossed as
				// copied indexed semantics. Do not invoke the Java feature renderers
				// (or their Iris state hooks) on the Rust presenter, even when a stale
				// queue happens to be empty.
				this.modelFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource, this.crumblingBufferSource);
				this.modelPartFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource, this.crumblingBufferSource);
			}
			if (WorldRenderRoutePolicy.currentEntityFlameRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityFlameSemantics(submitNodeCollection.getFlameSubmits(), this.atlasManager);
			} else if (!WorldRenderRoutePolicy.currentEntityFlameRoute().equals(WorldRenderRoutePolicy.Route.DISABLED)) {
				this.flameFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.atlasManager);
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !submitNodeCollection.getFlameSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame entity-flame route is unavailable while Rust owns presentation");
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
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& (submitNodeCollection.getNameTagSubmits().totalSubmitCount() != 0 || !submitNodeCollection.getTextSubmits().isEmpty())) {
				throw new IllegalStateException("Rust whole-frame world-text route is unavailable while Rust owns presentation");
			}
			if (!rustPresenterActive()
				|| WorldRenderRoutePolicy.currentMaterialRoute().usesJavaCompatibility()) {
				this.hitboxFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			}
			if (WorldRenderRoutePolicy.currentEntityLeashRoute().usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityLeashSemantics(submitNodeCollection.getLeashSubmits());
			} else if (WorldRenderRoutePolicy.currentEntityLeashRoute().usesJavaCompatibility()) {
				this.leashFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !submitNodeCollection.getLeashSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame entity-leash route is unavailable while Rust owns presentation");
			}
			if (!rustPresenterActive()
				|| WorldRenderRoutePolicy.currentMaterialRoute().usesJavaCompatibility()) {
				this.itemFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.outlineBufferSource);
			}
			this.blockFeatureRenderer.render(submitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource);
			if (!rustPresenterActive()
				|| WorldRenderRoutePolicy.currentMaterialRoute().usesJavaCompatibility()) {
				this.customFeatureRenderer.render(submitNodeCollection, this.bufferSource);
			}
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

	/** Copies debug hitbox boxes and view vectors into Rust-owned line semantics. */
	public void collectRustHitboxSemantics(SubmitNodeStorage hitboxSubmitStorage) {
		if (!WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				for (SubmitNodeCollection collection : hitboxSubmitStorage.getSubmitsPerOrder().values()) {
					if (!collection.getHitboxSubmits().isEmpty()) {
						throw new IllegalStateException("Rust whole-frame debug-hitbox route is unavailable while Rust owns presentation");
					}
				}
			}
			return;
		}
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
		if (WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			collectRustHitboxSemantics(hitboxSubmitStorage);
			return;
		}
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) return;
		for (SubmitNodeCollection collection : hitboxSubmitStorage.getSubmitsPerOrder().values()) {
			if (!collection.getHitboxSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame debug-hitbox route is unavailable while Rust owns presentation");
			}
		}
	}

	public void renderBlockFeaturesOnly() {
		// The whole-frame collector calls this method after semantic entity and
		// block-feature submission.  Dispatch each feature through its route
		// policy: Rust-owned families are copied into the pending semantic frame,
		// while Java compatibility remains available only outside Rust Vulkan.
		for (SubmitNodeCollection submitNodeCollection : this.submitNodeStorage.getSubmitsPerOrder().values()) {
			WorldRenderRoutePolicy.Route shadowRoute = WorldRenderRoutePolicy.currentEntityShadowRoute();
			if (shadowRoute.usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityShadowSemantics(submitNodeCollection.getShadowSubmits());
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !submitNodeCollection.getShadowSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame entity-shadow route is unavailable while Rust owns presentation");
			}
			WorldRenderRoutePolicy.Route flameRoute = WorldRenderRoutePolicy.currentEntityFlameRoute();
			if (flameRoute.usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityFlameSemantics(submitNodeCollection.getFlameSubmits(), this.atlasManager);
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !submitNodeCollection.getFlameSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame entity-flame route is unavailable while Rust owns presentation");
			}
			WorldRenderRoutePolicy.Route leashRoute = WorldRenderRoutePolicy.currentEntityLeashRoute();
			if (leashRoute.usesRustWholeFrameVulkan()) {
				RustGalWorldPrimitiveRenderer.collectEntityLeashSemantics(submitNodeCollection.getLeashSubmits());
			} else if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !submitNodeCollection.getLeashSubmits().isEmpty()) {
				throw new IllegalStateException("Rust whole-frame entity-leash route is unavailable while Rust owns presentation");
			}
			this.blockFeatureRenderer.render(
				submitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource, true
			);
		}

		this.submitNodeStorage.clear();
	}

	public void endFrame() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			return;
		}
		this.particleFeatureRenderer.endFrame();
	}

	public SubmitNodeStorage getSubmitNodeStorage() {
		return this.submitNodeStorage;
	}

	public void close() {
		this.particleFeatureRenderer.close();
	}
}

package net.minecraft.client.renderer.feature;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;

@Environment(EnvType.CLIENT)
public class CustomFeatureRenderer {
	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java custom feature rendering is unavailable while Rust owns whole-frame presentation");
		}
		CustomFeatureRenderer.Storage storage = submitNodeCollection.getCustomGeometrySubmits();

		for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entry : storage.customGeometrySubmits.entrySet()) {
			VertexConsumer vertexConsumer = bufferSource.getBuffer((RenderType)entry.getKey());

			for (SubmitNodeStorage.CustomGeometrySubmit customGeometrySubmit : (List<SubmitNodeStorage.CustomGeometrySubmit>)entry.getValue()) {
				// Iris: Set model storage before rendering
				((net.irisshaders.iris.mixinterface.ModelStorage) (Object) customGeometrySubmit).iris$set();
				SubmitNodeCollector.CustomGeometryRenderer renderer = customGeometrySubmit.customGeometryRenderer();
				if (renderer instanceof SubmitNodeCollector.ImmediateCustomGeometryRenderer immediateRenderer) {
					bufferSource.endBatch((RenderType)entry.getKey());
					immediateRenderer.render(customGeometrySubmit.pose(), (RenderType)entry.getKey(), bufferSource);
					vertexConsumer = bufferSource.getBuffer((RenderType)entry.getKey());
				} else {
					renderer.render(customGeometrySubmit.pose(), vertexConsumer);
				}
			}
		}
		
		// Iris: Clear captured rendering state
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentEntity(0);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
	}

	@Environment(EnvType.CLIENT)
	public static class Storage {
		final Map<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> customGeometrySubmits = new HashMap();
		private final Set<RenderType> customGeometrySubmitsUsage = new ObjectOpenHashSet<>();

		public void add(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
			List<SubmitNodeStorage.CustomGeometrySubmit> list = (List<SubmitNodeStorage.CustomGeometrySubmit>)this.customGeometrySubmits
				.computeIfAbsent(renderType, renderTypex -> new ArrayList());
			list.add(new SubmitNodeStorage.CustomGeometrySubmit(poseStack.last().copy(), customGeometryRenderer));
		}

		public void clear() {
			for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entry : this.customGeometrySubmits.entrySet()) {
				if (!((List)entry.getValue()).isEmpty()) {
					this.customGeometrySubmitsUsage.add((RenderType)entry.getKey());
					((List)entry.getValue()).clear();
				}
			}
		}

		public int totalSubmitCount() {
			int total = 0;
			for (List<SubmitNodeStorage.CustomGeometrySubmit> submits : this.customGeometrySubmits.values()) {
				total += submits.size();
			}
			return total;
		}

		public void endFrame() {
			this.customGeometrySubmits.keySet().removeIf(renderType -> !this.customGeometrySubmitsUsage.contains(renderType));
			this.customGeometrySubmitsUsage.clear();
		}
	}
}

package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;

@Environment(EnvType.CLIENT)
public class CustomFeatureRenderer {
	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		CustomFeatureRenderer.Storage storage = submitNodeCollection.getCustomGeometrySubmits();

		for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entry : storage.customGeometrySubmits.entrySet()) {
			VertexConsumer vertexConsumer = bufferSource.getBuffer((RenderType)entry.getKey());

			for (SubmitNodeStorage.CustomGeometrySubmit customGeometrySubmit : (List<SubmitNodeStorage.CustomGeometrySubmit>)entry.getValue()) {
				customGeometrySubmit.customGeometryRenderer().render(customGeometrySubmit.pose(), vertexConsumer);
			}
		}
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

		public void endFrame() {
			this.customGeometrySubmits.keySet().removeIf(renderType -> !this.customGeometrySubmitsUsage.contains(renderType));
			this.customGeometrySubmitsUsage.clear();
		}
	}
}

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
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelBakery;

@Environment(EnvType.CLIENT)
public class ModelPartFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();

	@Environment(EnvType.CLIENT)
	public static class Storage {
		final Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> modelPartSubmits = new HashMap();
		private final Set<RenderType> modelPartSubmitsUsage = new ObjectOpenHashSet<>();

		public void add(RenderType renderType, SubmitNodeStorage.ModelPartSubmit modelPartSubmit) {
			((List)this.modelPartSubmits.computeIfAbsent(renderType, renderTypex -> new ArrayList())).add(modelPartSubmit);
		}

		public void clear() {
			for (Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> entry : this.modelPartSubmits.entrySet()) {
				if (!((List)entry.getValue()).isEmpty()) {
					this.modelPartSubmitsUsage.add((RenderType)entry.getKey());
					((List)entry.getValue()).clear();
				}
			}
		}

		public int totalSubmitCount() {
			int total = 0;
			for (List<SubmitNodeStorage.ModelPartSubmit> submits : this.modelPartSubmits.values()) {
				total += submits.size();
			}
			return total;
		}

		public void endFrame() {
			this.modelPartSubmits.keySet().removeIf(renderType -> !this.modelPartSubmitsUsage.contains(renderType));
			this.modelPartSubmitsUsage.clear();
		}
	}
}

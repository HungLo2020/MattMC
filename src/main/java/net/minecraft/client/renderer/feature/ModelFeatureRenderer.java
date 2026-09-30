package net.minecraft.client.renderer.feature;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.resources.model.ModelBakery;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ModelFeatureRenderer {
	private final PoseStack poseStack = new PoseStack();

	@Environment(EnvType.CLIENT)
	public record CrumblingOverlay(int progress, PoseStack.Pose cameraPose) {
	}

	@Environment(EnvType.CLIENT)
	public static class Storage {
		final Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> opaqueModelSubmits = new HashMap();
		final List<SubmitNodeStorage.TranslucentModelSubmit<?>> translucentModelSubmits = new ArrayList();
		private final Set<RenderType> usedModelSubmitBuckets = new ObjectOpenHashSet<>();

		public void add(RenderType renderType, SubmitNodeStorage.ModelSubmit<?> modelSubmit) {
			if (renderType.pipeline().getBlendFunction().isEmpty()) {
				((List)this.opaqueModelSubmits.computeIfAbsent(renderType, renderTypex -> new ArrayList())).add(modelSubmit);
			} else {
				Vector3f vector3f = modelSubmit.pose().pose().transformPosition(new Vector3f());
				this.translucentModelSubmits.add(new SubmitNodeStorage.TranslucentModelSubmit<>(modelSubmit, renderType, vector3f));
			}
		}

		public void clear() {
			this.translucentModelSubmits.clear();

			for (Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> entry : this.opaqueModelSubmits.entrySet()) {
				List<SubmitNodeStorage.ModelSubmit<?>> list = (List<SubmitNodeStorage.ModelSubmit<?>>)entry.getValue();
				if (!list.isEmpty()) {
					this.usedModelSubmitBuckets.add((RenderType)entry.getKey());
					list.clear();
				}
			}
		}

		public int totalSubmitCount() {
			int total = this.translucentModelSubmits.size();
			for (List<SubmitNodeStorage.ModelSubmit<?>> submits : this.opaqueModelSubmits.values()) {
				total += submits.size();
			}
			return total;
		}

		public void endFrame() {
			this.opaqueModelSubmits.keySet().removeIf(renderType -> !this.usedModelSubmitBuckets.contains(renderType));
			this.usedModelSubmitBuckets.clear();
		}
	}
}

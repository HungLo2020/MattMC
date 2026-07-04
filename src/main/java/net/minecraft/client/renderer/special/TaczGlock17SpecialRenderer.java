package net.minecraft.client.renderer.special;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class TaczGlock17SpecialRenderer implements NoDataSpecialModelRenderer {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("tacz", "textures/gun/uv/glock_17.png");
	private final Model.Simple model;

	public TaczGlock17SpecialRenderer() {
		this.model = new Model.Simple(createBodyLayer().bakeRoot(), RenderType::entityCutoutNoCull);
	}

	private static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition root = meshDefinition.getRoot();
		PartDefinition gun = root.addOrReplaceChild("gun", CubeListBuilder.create(), PartPose.offset(0.0F, 16.0F, 0.0F));
		gun.addOrReplaceChild("slide", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -7.0F, -18.0F, 14.0F, 4.0F, 25.0F), PartPose.ZERO);
		gun.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(80, 0).addBox(-3.0F, -6.25F, -20.5F, 6.0F, 2.0F, 8.0F), PartPose.ZERO);
		gun.addOrReplaceChild("frame", CubeListBuilder.create().texOffs(0, 38).addBox(-6.0F, -3.2F, -15.0F, 12.0F, 3.5F, 19.0F), PartPose.ZERO);
		gun.addOrReplaceChild("rail", CubeListBuilder.create().texOffs(68, 38).addBox(-5.0F, 0.0F, -14.0F, 10.0F, 1.8F, 12.0F), PartPose.ZERO);
		gun.addOrReplaceChild("trigger_guard", CubeListBuilder.create().texOffs(118, 30).addBox(-4.0F, 0.0F, -5.5F, 8.0F, 6.0F, 2.0F), PartPose.ZERO);
		gun.addOrReplaceChild("trigger", CubeListBuilder.create().texOffs(142, 30).addBox(-1.0F, 1.5F, -5.0F, 2.0F, 4.0F, 1.5F), PartPose.ZERO);
		gun.addOrReplaceChild("grip", CubeListBuilder.create().texOffs(0, 70).addBox(-5.0F, -0.5F, 0.5F, 10.0F, 15.0F, 7.0F), PartPose.rotation(-0.22F, 0.0F, 0.0F));
		gun.addOrReplaceChild("magazine", CubeListBuilder.create().texOffs(54, 70).addBox(-4.0F, 8.0F, 1.0F, 8.0F, 9.0F, 5.5F), PartPose.rotation(-0.22F, 0.0F, 0.0F));
		gun.addOrReplaceChild("front_sight", CubeListBuilder.create().texOffs(104, 0).addBox(-1.5F, -8.0F, -17.0F, 3.0F, 1.0F, 2.0F), PartPose.ZERO);
		gun.addOrReplaceChild("rear_sight", CubeListBuilder.create().texOffs(118, 0).addBox(-2.5F, -8.2F, 2.5F, 5.0F, 1.2F, 2.0F), PartPose.ZERO);
		return LayerDefinition.create(meshDefinition, 1024, 1024);
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.scale(0.82F, 0.82F, 0.82F);
		submitNodeCollector.submitModelPart(this.model.root(), poseStack, this.model.renderType(TEXTURE), i, j, null, false, bl, -1, null, k);
		poseStack.popPose();
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.scale(0.82F, 0.82F, 0.82F);
		this.model.root().getExtentsForGui(poseStack, set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<TaczGlock17SpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new TaczGlock17SpecialRenderer.Unbaked());

		@Override
		public MapCodec<TaczGlock17SpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new TaczGlock17SpecialRenderer();
		}
	}
}

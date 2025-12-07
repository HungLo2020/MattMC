package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BedRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.Direction;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.DoubleBlockCombiner.NeighborCombineResult;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class BedRenderer implements BlockEntityRenderer<BedBlockEntity, BedRenderState> {
	private final MaterialSet materials;
	private final Model.Simple headModel;
	private final Model.Simple footModel;

	public BedRenderer(BlockEntityRendererProvider.Context context) {
		this(context.materials(), context.entityModelSet());
	}

	public BedRenderer(SpecialModelRenderer.BakingContext bakingContext) {
		this(bakingContext.materials(), bakingContext.entityModelSet());
	}

	public BedRenderer(MaterialSet materialSet, EntityModelSet entityModelSet) {
		this.materials = materialSet;
		this.headModel = new Model.Simple(entityModelSet.bakeLayer(ModelLayers.BED_HEAD), RenderType::entitySolid);
		this.footModel = new Model.Simple(entityModelSet.bakeLayer(ModelLayers.BED_FOOT), RenderType::entitySolid);
	}

	public static LayerDefinition createHeadLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 6.0F), PartPose.ZERO);
		partDefinition.addOrReplaceChild(
			"left_leg",
			CubeListBuilder.create().texOffs(50, 6).addBox(0.0F, 6.0F, 0.0F, 3.0F, 3.0F, 3.0F),
			PartPose.rotation((float) (Math.PI / 2), 0.0F, (float) (Math.PI / 2))
		);
		partDefinition.addOrReplaceChild(
			"right_leg",
			CubeListBuilder.create().texOffs(50, 18).addBox(-16.0F, 6.0F, 0.0F, 3.0F, 3.0F, 3.0F),
			PartPose.rotation((float) (Math.PI / 2), 0.0F, (float) Math.PI)
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	public static LayerDefinition createFootLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();
		partDefinition.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 22).addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 6.0F), PartPose.ZERO);
		partDefinition.addOrReplaceChild(
			"left_leg", CubeListBuilder.create().texOffs(50, 0).addBox(0.0F, 6.0F, -16.0F, 3.0F, 3.0F, 3.0F), PartPose.rotation((float) (Math.PI / 2), 0.0F, 0.0F)
		);
		partDefinition.addOrReplaceChild(
			"right_leg",
			CubeListBuilder.create().texOffs(50, 12).addBox(-16.0F, 6.0F, -16.0F, 3.0F, 3.0F, 3.0F),
			PartPose.rotation((float) (Math.PI / 2), 0.0F, (float) (Math.PI * 3.0 / 2.0))
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}

	public BedRenderState createRenderState() {
		return new BedRenderState();
	}

	public void extractRenderState(
		BedBlockEntity bedBlockEntity, BedRenderState bedRenderState, float f, Vec3 vec3, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(bedBlockEntity, bedRenderState, f, vec3, crumblingOverlay);
		bedRenderState.color = bedBlockEntity.getColor();
		bedRenderState.facing = (Direction)bedBlockEntity.getBlockState().getValue(BedBlock.FACING);
		bedRenderState.isHead = bedBlockEntity.getBlockState().getValue(BedBlock.PART) == BedPart.HEAD;
		if (bedBlockEntity.getLevel() != null) {
			NeighborCombineResult<? extends BedBlockEntity> neighborCombineResult = DoubleBlockCombiner.combineWithNeigbour(
				BlockEntityType.BED,
				BedBlock::getBlockType,
				BedBlock::getConnectedDirection,
				ChestBlock.FACING,
				bedBlockEntity.getBlockState(),
				bedBlockEntity.getLevel(),
				bedBlockEntity.getBlockPos(),
				(levelAccessor, blockPos) -> false
			);
			bedRenderState.lightCoords = ((Int2IntFunction)neighborCombineResult.apply(new BrightnessCombiner())).get(bedRenderState.lightCoords);
		}
	}

	public void submit(BedRenderState bedRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		Material material = Sheets.getBedMaterial(bedRenderState.color);
		this.submitPiece(
			poseStack,
			submitNodeCollector,
			bedRenderState.isHead ? this.headModel : this.footModel,
			bedRenderState.facing,
			material,
			bedRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			false,
			bedRenderState.breakProgress,
			0
		);
	}

	public void submitSpecial(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, Material material, int k) {
		this.submitPiece(poseStack, submitNodeCollector, this.headModel, Direction.SOUTH, material, i, j, false, null, k);
		this.submitPiece(poseStack, submitNodeCollector, this.footModel, Direction.SOUTH, material, i, j, true, null, k);
	}

	private void submitPiece(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		Model.Simple simple,
		Direction direction,
		Material material,
		int i,
		int j,
		boolean bl,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		int k
	) {
		poseStack.pushPose();
		preparePose(poseStack, bl, direction);
		submitNodeCollector.submitModel(
			simple, Unit.INSTANCE, poseStack, material.renderType(RenderType::entitySolid), i, j, -1, this.materials.get(material), k, crumblingOverlay
		);
		poseStack.popPose();
	}

	private static void preparePose(PoseStack poseStack, boolean bl, Direction direction) {
		poseStack.translate(0.0F, 0.5625F, bl ? -1.0F : 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F + direction.toYRot()));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
	}

	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		preparePose(poseStack, false, Direction.SOUTH);
		this.headModel.root().getExtentsForGui(poseStack, set);
		poseStack.setIdentity();
		preparePose(poseStack, true, Direction.SOUTH);
		this.footModel.root().getExtentsForGui(poseStack, set);
	}
}

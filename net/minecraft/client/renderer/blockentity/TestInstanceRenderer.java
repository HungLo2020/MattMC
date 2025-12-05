package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityWithBoundingBoxRenderState;
import net.minecraft.client.renderer.blockentity.state.TestInstanceRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity.ErrorMarker;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TestInstanceRenderer implements BlockEntityRenderer<TestInstanceBlockEntity, TestInstanceRenderState> {
	private static final float ERROR_PADDING = 0.02F;
	private final BeaconRenderer<TestInstanceBlockEntity> beacon = new BeaconRenderer();
	private final BlockEntityWithBoundingBoxRenderer<TestInstanceBlockEntity> box = new BlockEntityWithBoundingBoxRenderer();
	private final Font font;
	private final EntityRenderDispatcher entityRenderer;

	public TestInstanceRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
		this.entityRenderer = context.entityRenderer();
	}

	public TestInstanceRenderState createRenderState() {
		return new TestInstanceRenderState();
	}

	public void extractRenderState(
		TestInstanceBlockEntity testInstanceBlockEntity,
		TestInstanceRenderState testInstanceRenderState,
		float f,
		Vec3 vec3,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(testInstanceBlockEntity, testInstanceRenderState, f, vec3, crumblingOverlay);
		testInstanceRenderState.beaconRenderState = new BeaconRenderState();
		BlockEntityRenderState.extractBase(testInstanceBlockEntity, testInstanceRenderState.beaconRenderState, crumblingOverlay);
		BeaconRenderer.extract(testInstanceBlockEntity, testInstanceRenderState.beaconRenderState, f, vec3);
		testInstanceRenderState.blockEntityWithBoundingBoxRenderState = new BlockEntityWithBoundingBoxRenderState();
		BlockEntityRenderState.extractBase(testInstanceBlockEntity, testInstanceRenderState.blockEntityWithBoundingBoxRenderState, crumblingOverlay);
		BlockEntityWithBoundingBoxRenderer.extract(testInstanceBlockEntity, testInstanceRenderState.blockEntityWithBoundingBoxRenderState);
		testInstanceRenderState.errorMarkers.clear();

		for (ErrorMarker errorMarker : testInstanceBlockEntity.getErrorMarkers()) {
			testInstanceRenderState.errorMarkers.add(new ErrorMarker(errorMarker.pos().subtract(testInstanceBlockEntity.getBlockPos()), errorMarker.text()));
		}
	}

	public void submit(
		TestInstanceRenderState testInstanceRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		this.beacon.submit(testInstanceRenderState.beaconRenderState, poseStack, submitNodeCollector, cameraRenderState);
		this.box.submit(testInstanceRenderState.blockEntityWithBoundingBoxRenderState, poseStack, submitNodeCollector, cameraRenderState);

		for (ErrorMarker errorMarker : testInstanceRenderState.errorMarkers) {
			this.submitErrorMarker(poseStack, submitNodeCollector, errorMarker, cameraRenderState);
		}
	}

	private void submitErrorMarker(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ErrorMarker errorMarker, CameraRenderState cameraRenderState) {
		BlockPos blockPos = errorMarker.pos();
		submitNodeCollector.order(1).submitCustomGeometry(poseStack, RenderType.debugFilledBox(), (pose, vertexConsumer) -> {
			float fx = blockPos.getX() - 0.02F;
			float g = blockPos.getY() - 0.02F;
			float h = blockPos.getZ() - 0.02F;
			float ix = blockPos.getX() + 1.0F + 0.02F;
			float j = blockPos.getY() + 1.0F + 0.02F;
			float k = blockPos.getZ() + 1.0F + 0.02F;
			PoseStack poseStackx = new PoseStack();
			poseStackx.last().set(pose);
			ShapeRenderer.addChainedFilledBoxVertices(poseStackx, vertexConsumer, fx, g, h, ix, j, k, 1.0F, 0.0F, 0.0F, 0.375F);
		});
		FormattedCharSequence formattedCharSequence = errorMarker.text().getVisualOrderText();
		int i = this.font.width(formattedCharSequence);
		float f = 0.01F;
		poseStack.pushPose();
		poseStack.translate(blockPos.getX() + 0.5F, blockPos.getY() + 1.2F, blockPos.getZ() + 0.5F);
		poseStack.mulPose(cameraRenderState.orientation);
		poseStack.scale(0.01F, -0.01F, 0.01F);
		submitNodeCollector.order(2).submitText(poseStack, -i / 2.0F, 0.0F, formattedCharSequence, false, Font.DisplayMode.SEE_THROUGH, 15728880, -1, 0, 0);
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return this.beacon.shouldRenderOffScreen() || this.box.shouldRenderOffScreen();
	}

	@Override
	public int getViewDistance() {
		return Math.max(this.beacon.getViewDistance(), this.box.getViewDistance());
	}

	public boolean shouldRender(TestInstanceBlockEntity testInstanceBlockEntity, Vec3 vec3) {
		return this.beacon.shouldRender(testInstanceBlockEntity, vec3) || this.box.shouldRender(testInstanceBlockEntity, vec3);
	}
}

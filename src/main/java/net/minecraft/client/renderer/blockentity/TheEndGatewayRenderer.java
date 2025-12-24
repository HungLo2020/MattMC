package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.EndGatewayRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TheEndGatewayRenderer extends AbstractEndPortalRenderer<TheEndGatewayBlockEntity, EndGatewayRenderState> {
	private static final ResourceLocation BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/end_gateway_beam.png");

	public EndGatewayRenderState createRenderState() {
		return new EndGatewayRenderState();
	}

	public void extractRenderState(
		TheEndGatewayBlockEntity theEndGatewayBlockEntity,
		EndGatewayRenderState endGatewayRenderState,
		float f,
		Vec3 vec3,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		super.extractRenderState(theEndGatewayBlockEntity, endGatewayRenderState, f, vec3, crumblingOverlay);
		Level level = theEndGatewayBlockEntity.getLevel();
		if (theEndGatewayBlockEntity.isSpawning() || theEndGatewayBlockEntity.isCoolingDown() && level != null) {
			endGatewayRenderState.scale = theEndGatewayBlockEntity.isSpawning()
				? theEndGatewayBlockEntity.getSpawnPercent(f)
				: theEndGatewayBlockEntity.getCooldownPercent(f);
			double d = theEndGatewayBlockEntity.isSpawning() ? theEndGatewayBlockEntity.getLevel().getMaxY() : 50.0;
			endGatewayRenderState.scale = Mth.sin(endGatewayRenderState.scale * (float) Math.PI);
			endGatewayRenderState.height = Mth.floor(endGatewayRenderState.scale * d);
			endGatewayRenderState.color = theEndGatewayBlockEntity.isSpawning() ? DyeColor.MAGENTA.getTextureDiffuseColor() : DyeColor.PURPLE.getTextureDiffuseColor();
			endGatewayRenderState.animationTime = theEndGatewayBlockEntity.getLevel() != null
				? Math.floorMod(theEndGatewayBlockEntity.getLevel().getGameTime(), 40) + f
				: 0.0F;
		} else {
			endGatewayRenderState.height = 0;
		}
	}

	public void submit(
		EndGatewayRenderState endGatewayRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		if (endGatewayRenderState.height > 0) {
			BeaconRenderer.submitBeaconBeam(
				poseStack,
				submitNodeCollector,
				BEAM_LOCATION,
				endGatewayRenderState.scale,
				endGatewayRenderState.animationTime,
				-endGatewayRenderState.height,
				endGatewayRenderState.height * 2,
				endGatewayRenderState.color,
				0.15F,
				0.175F
			);
		}

		super.submit(endGatewayRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	@Override
	protected float getOffsetUp() {
		return 1.0F;
	}

	@Override
	protected float getOffsetDown() {
		return 0.0F;
	}

	@Override
	protected RenderType renderType() {
		return RenderType.endGateway();
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}

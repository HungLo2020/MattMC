// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector4f;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.*;

/**
 * Provides common player and world state uniforms.
 * 
 * Based on IRIS's CommonUniforms.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/CommonUniforms.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (~20 uniforms):
 * - Player state: hideGUI, isRightHanded, is_sneaking, is_sprinting, is_hurt, is_invisible, is_burning, is_on_ground
 * - Effects: blindness, darknessFactor, nightVision, isEyeInWater
 * - World state: screenBrightness, eyeBrightness, rainStrength, skyColor
 * - Constants: entityColor, blockEntityId, currentRenderedItemId, pi
 */
public final class CommonUniforms {
	private static final Minecraft client = Minecraft.getInstance();
	private static final Vector2i ZERO_VECTOR_2i = new Vector2i();
	private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

	private CommonUniforms() {
	}

	public static void addCommonUniforms(UniformHolder uniforms) {
		uniforms
			// GUI and player state
			.uniform1b(PER_FRAME, "hideGUI", () -> client.options.hideGui)
			.uniform1b(PER_FRAME, "isRightHanded", () -> client.options.mainHand().get() == HumanoidArm.RIGHT)
			.uniform1i(PER_FRAME, "isEyeInWater", CommonUniforms::isEyeInWater)
			.uniform1f(PER_FRAME, "blindness", CommonUniforms::getBlindness)
			.uniform1f(PER_FRAME, "darknessFactor", CommonUniforms::getDarknessFactor)
			.uniform1f(PER_FRAME, "nightVision", CommonUniforms::getNightVision)
			.uniform1b(PER_FRAME, "is_sneaking", CommonUniforms::isSneaking)
			.uniform1b(PER_FRAME, "is_sprinting", CommonUniforms::isSprinting)
			.uniform1b(PER_FRAME, "is_hurt", CommonUniforms::isHurt)
			.uniform1b(PER_FRAME, "is_invisible", CommonUniforms::isInvisible)
			.uniform1b(PER_FRAME, "is_burning", CommonUniforms::isBurning)
			.uniform1b(PER_FRAME, "is_on_ground", CommonUniforms::isOnGround)
			// Screen and world state
			.uniform1f(PER_FRAME, "screenBrightness", () -> client.options.gamma().get())
			.uniform2i(PER_FRAME, "eyeBrightness", CommonUniforms::getEyeBrightness)
			.uniform1f(PER_TICK, "rainStrength", CommonUniforms::getRainStrength)
			.uniform3d(PER_FRAME, "skyColor", CommonUniforms::getSkyColor)
			// Constants
			.uniform4f(ONCE, "entityColor", () -> new Vector4f(0, 0, 0, 0))
			.uniform1i(ONCE, "blockEntityId", () -> -1)
			.uniform1i(ONCE, "currentRenderedItemId", () -> -1)
			.uniform1f(ONCE, "pi", () -> (float) Math.PI);
	}

	private static boolean isOnGround() {
		return client.player != null && client.player.onGround();
	}

	private static boolean isHurt() {
		if (client.player != null) {
			return client.player.hurtTime > 0;
		} else {
			return false;
		}
	}

	private static boolean isInvisible() {
		if (client.player != null) {
			return client.player.isInvisible();
		} else {
			return false;
		}
	}

	private static boolean isBurning() {
		if (client.player != null) {
			return client.player.isOnFire();
		} else {
			return false;
		}
	}

	private static boolean isSneaking() {
		if (client.player != null) {
			return client.player.isCrouching();
		} else {
			return false;
		}
	}

	private static boolean isSprinting() {
		if (client.player != null) {
			return client.player.isSprinting();
		} else {
			return false;
		}
	}

	private static Vector3d getSkyColor() {
		if (client.level == null || client.getCameraEntity() == null) {
			return ZERO_VECTOR_3d;
		}

		// Note: IRIS uses CapturedRenderingState.INSTANCE.getTickDelta()
		int skyColor = client.level.getSkyColor(client.getCameraEntity().position(), client.getDeltaTracker().getGameTimeDeltaTicks());

		return new Vector3d(ARGB.redFloat(skyColor), ARGB.greenFloat(skyColor), ARGB.blueFloat(skyColor));
	}

	static float getBlindness() {
		Entity cameraEntity = client.getCameraEntity();

		if (cameraEntity instanceof LivingEntity) {
			MobEffectInstance blindness = ((LivingEntity) cameraEntity).getEffect(MobEffects.BLINDNESS);

			if (blindness != null) {
				if (blindness.isInfiniteDuration()) {
					return 1.0f;
				} else {
					return Math.clamp(0.0F, 1.0F, blindness.getDuration() / 20.0F);
				}
			}
		}

		return 0.0F;
	}

	static float getDarknessFactor() {
		Entity cameraEntity = client.getCameraEntity();

		if (cameraEntity instanceof LivingEntity) {
			MobEffectInstance darkness = ((LivingEntity) cameraEntity).getEffect(MobEffects.DARKNESS);

			if (darkness != null) {
				// Note: IRIS uses CapturedRenderingState.INSTANCE.getTickDelta()
				return darkness.getBlendFactor((LivingEntity) cameraEntity, client.getDeltaTracker().getGameTimeDeltaTicks());
			}
		}

		return 0.0F;
	}

	static float getRainStrength() {
		if (client.level == null) {
			return 0f;
		}

		// Note: IRIS uses CapturedRenderingState.INSTANCE.getTickDelta()
		return Math.clamp(0.0F, 1.0F,
			client.level.getRainLevel(client.getDeltaTracker().getGameTimeDeltaTicks()));
	}

	private static Vector2i getEyeBrightness() {
		if (client.getCameraEntity() == null || client.level == null) {
			return ZERO_VECTOR_2i;
		}

		Vec3 feet = client.getCameraEntity().position();
		Vec3 eyes = new Vec3(feet.x, client.getCameraEntity().getEyeY(), feet.z);
		BlockPos eyeBlockPos = BlockPos.containing(eyes);

		int blockLight = client.level.getBrightness(LightLayer.BLOCK, eyeBlockPos);
		int skyLight = client.level.getBrightness(LightLayer.SKY, eyeBlockPos);

		return new Vector2i(blockLight * 16, skyLight * 16);
	}

	private static float getNightVision() {
		Entity cameraEntity = client.getCameraEntity();

		if (cameraEntity instanceof LivingEntity livingEntity) {
			try {
				// Note: IRIS uses CapturedRenderingState.INSTANCE.getTickDelta()
				float nightVisionStrength =
					GameRenderer.getNightVisionScale(livingEntity, client.getDeltaTracker().getGameTimeDeltaTicks());

				if (nightVisionStrength > 0) {
					return Math.clamp(0.0F, 1.0F, nightVisionStrength);
				}
			} catch (NullPointerException e) {
				return 0.0F;
			}
		}

		// Conduit power gives the player a sort-of night vision effect when underwater
		if (client.player != null && client.player.hasEffect(MobEffects.CONDUIT_POWER)) {
			float underwaterVisibility = client.player.getWaterVision();

			if (underwaterVisibility > 0.0f) {
				return Math.clamp(0.0F, 1.0F, underwaterVisibility);
			}
		}

		return 0.0F;
	}

	static int isEyeInWater() {
		FogType submersionType = client.gameRenderer.getMainCamera().getFluidInCamera();

		if (submersionType == FogType.WATER) {
			return 1;
		} else if (submersionType == FogType.LAVA) {
			return 2;
		} else if (submersionType == FogType.POWDER_SNOW) {
			return 3;
		} else {
			return 0;
		}
	}
}

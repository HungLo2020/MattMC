// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.Objects;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Implements uniforms relating to celestial bodies (sun, moon, shadows).
 * 
 * COPIED from IRIS's CelestialUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/CelestialUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (7 uniforms):
 * - sunAngle: Angle of the sun in the sky (0.0-1.0)
 * - sunPosition: 3D position vector of the sun
 * - moonPosition: 3D position vector of the moon
 * - shadowAngle: Angle used for shadow calculations
 * - shadowLightPosition: Position of primary shadow light (sun or moon)
 * - endFlashPosition: Position of end portal flash (End dimension only)
 * - upPosition: Up direction vector
 */
public final class CelestialUniforms {
	private static final Vector4f ZERO = new Vector4f();
	private final float sunPathRotation;

	public CelestialUniforms(float sunPathRotation) {
		this.sunPathRotation = sunPathRotation;
	}

	public static float getSunAngle() {
		float skyAngle = getSkyAngle();

		if (skyAngle < 0.75F) {
			return skyAngle + 0.25F;
		} else {
			return skyAngle - 0.75F;
		}
	}

	private static float getShadowAngle() {
		float shadowAngle = getSunAngle();

		if (!isDay()) {
			shadowAngle -= 0.5F;
		}

		return shadowAngle;
	}

	private static Vector4f getUpPosition() {
		Vector4f upVector = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);

		// Get the current GBuffer model view matrix, since that is the basis of the celestial model view matrix
		Matrix4fc gbufferMV = CapturedRenderingState.INSTANCE.getGbufferModelView();
		if (gbufferMV == null) {
			return upVector;
		}
		
		Matrix4f preCelestial = new Matrix4f(gbufferMV);

		// Apply the fixed -90.0F degrees rotation to mirror the same transformation in renderSky.
		// But, notably, skip the rotation by the skyAngle.
		preCelestial.rotate(Axis.YP.rotationDegrees(-90.0F));

		// Use this matrix to transform the vector.
		upVector = preCelestial.transform(upVector);

		return upVector;
	}

	public static boolean isDay() {
		// Determine whether it is day or night based on the sky angle.
		return getSunAngle() <= 0.5;
	}

	private static ClientLevel getWorld() {
		return Objects.requireNonNull(Minecraft.getInstance().level);
	}

	private static float getSkyAngle() {
		return getWorld().getTimeOfDay(CapturedRenderingState.INSTANCE.getTickDelta());
	}

	public void addCelestialUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1f(PER_FRAME, "sunAngle", CelestialUniforms::getSunAngle)
			.uniformTruncated3f(PER_FRAME, "sunPosition", this::getSunPosition)
			.uniformTruncated3f(PER_FRAME, "moonPosition", this::getMoonPosition)
			.uniform1f(PER_FRAME, "shadowAngle", CelestialUniforms::getShadowAngle)
			.uniformTruncated3f(PER_FRAME, "shadowLightPosition", this::getShadowLightPosition)
			.uniformTruncated3f(PER_FRAME, "endFlashPosition", () -> {
				if (Minecraft.getInstance().level.dimension() == Level.END) {
					return getEndFlashPosition();
				} else {
					return ZERO;
				}
			})
			.uniformTruncated3f(PER_FRAME, "upPosition", CelestialUniforms::getUpPosition);
	}

	private Vector4f getSunPosition() {
		return getCelestialPosition(100.0F);
	}

	private Vector4f getMoonPosition() {
		return getCelestialPosition(-100.0F);
	}

	private Vector4f getEndFlashPosition() {
		// Note: End flash requires EndFlashState from level
		// Simplified for MattMC - returns zero vector for now
		return ZERO;
	}

	public Vector4f getShadowLightPosition() {
		// Simplified: just return sun or moon position based on time of day
		// IRIS checks for End dimension and end flash support
		return isDay() ? getSunPosition() : getMoonPosition();
	}

	private Vector4f getCelestialPosition(float y) {
		Vector4f position = new Vector4f(0.0F, y, 0.0F, 0.0F);

		Matrix4fc gbufferMV = CapturedRenderingState.INSTANCE.getGbufferModelView();
		if (gbufferMV == null) {
			return position;
		}
		
		Matrix4f celestial = new Matrix4f(gbufferMV);

		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
		celestial.rotate(Axis.YP.rotationDegrees(-90.0F));
		celestial.rotate(Axis.ZP.rotationDegrees(sunPathRotation));
		celestial.rotate(Axis.XP.rotationDegrees(getSkyAngle() * 360.0F));

		position = celestial.transform(position);

		return position;
	}
}

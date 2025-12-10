// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.ONCE;
import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Implements uniforms relating to camera position and movement.
 * 
 * COPIED VERBATIM from IRIS's CameraUniforms.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/CameraUniforms.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (9 uniforms):
 * - near: Near plane distance (always 0.05)
 * - far: Far plane distance (render distance in blocks)
 * - cameraPosition: Current camera position (shifted for precision)
 * - eyeAltitude: Y coordinate of camera
 * - previousCameraPosition: Camera position from previous frame
 * - cameraPositionInt: Integer part of camera position
 * - cameraPositionFract: Fractional part of camera position
 * - previousCameraPositionInt: Integer part of previous camera position
 * - previousCameraPositionFract: Fractional part of previous camera position
 */
public class CameraUniforms {
	private static final Minecraft client = Minecraft.getInstance();

	private CameraUniforms() {
	}

	public static void addCameraUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier) {
		CameraPositionTracker tracker = new CameraPositionTracker(notifier);

		uniforms
			.uniform1f(ONCE, "near", () -> 0.05f)
			.uniform1f(PER_FRAME, "far", CameraUniforms::getRenderDistanceInBlocks)
			.uniform3d(PER_FRAME, "cameraPosition", tracker::getCurrentCameraPosition)
			.uniform1f(PER_FRAME, "eyeAltitude", tracker::getCurrentCameraPositionY)
			.uniform3d(PER_FRAME, "previousCameraPosition", tracker::getPreviousCameraPosition)
			.uniform3i(PER_FRAME, "cameraPositionInt", () -> getCameraPositionInt(getUnshiftedCameraPosition()))
			.uniform3f(PER_FRAME, "cameraPositionFract", () -> getCameraPositionFract(getUnshiftedCameraPosition()))
			.uniform3i(PER_FRAME, "previousCameraPositionInt", () -> getCameraPositionInt(tracker.getPreviousCameraPositionUnshifted()))
			.uniform3f(PER_FRAME, "previousCameraPositionFract", () -> getCameraPositionFract(tracker.getPreviousCameraPositionUnshifted()));
	}

	private static float getRenderDistanceInBlocks() {
		return client.options.getEffectiveRenderDistance() * 16;
	}

	public static Vector3d getUnshiftedCameraPosition() {
		// Convert Vec3 to Vector3d
		var pos = client.gameRenderer.getMainCamera().getPosition();
		return new Vector3d(pos.x, pos.y, pos.z);
	}

	public static Vector3f getCameraPositionFract(Vector3d originalPos) {
		return new Vector3f(
			(float) (originalPos.x - Math.floor(originalPos.x)),
			(float) (originalPos.y - Math.floor(originalPos.y)),
			(float) (originalPos.z - Math.floor(originalPos.z))
		);
	}

	public static Vector3i getCameraPositionInt(Vector3d originalPos) {
		return new Vector3i(
			(int) Math.floor(originalPos.x),
			(int) Math.floor(originalPos.y),
			(int) Math.floor(originalPos.z)
		);
	}

	static class CameraPositionTracker {
		/**
		 * Value range of cameraPosition. We want this to be small enough that precision is maintained when we convert
		 * from a double to a float, but big enough that shifts happen infrequently, since each shift corresponds with
		 * a noticeable change in shader animations and similar. 1000024 is the number used by Optifine for walking (however this is too much, so we choose 30000),
		 * with an extra 1024 check for if the user has teleported between camera positions.
		 */
		private static final double WALK_RANGE = 30000;
		private static final double TP_RANGE = 1000;
		private final Vector3d shift = new Vector3d();
		private Vector3d previousCameraPosition = new Vector3d();
		private Vector3d currentCameraPosition = new Vector3d();
		private Vector3d previousCameraPositionUnshifted = new Vector3d();
		private Vector3d currentCameraPositionUnshifted = new Vector3d();

		CameraPositionTracker(FrameUpdateNotifier notifier) {
			notifier.addListener(this::update);
		}

		private static double getShift(double value, double prevValue) {
			if (Math.abs(value) > WALK_RANGE || Math.abs(value - prevValue) > TP_RANGE) {
				// Only shift by increments of WALK_RANGE - this is required for some packs (like SEUS PTGI) to work properly
				return -(value - (value % WALK_RANGE));
			} else {
				return 0.0;
			}
		}

		private void update() {
			previousCameraPosition = currentCameraPosition;
			previousCameraPositionUnshifted = currentCameraPositionUnshifted;
			currentCameraPosition = getUnshiftedCameraPosition().add(shift);
			currentCameraPositionUnshifted = getUnshiftedCameraPosition();

			updateShift();
		}

		/**
		 * Updates our shift values to try to keep |x| < 30000 and |z| < 30000, to maintain precision with cameraPosition.
		 * Since our actual range is 60000x60000, this means that we won't excessively move back and forth when moving
		 * around a chunk border.
		 */
		private void updateShift() {
			double dX = getShift(currentCameraPosition.x, previousCameraPosition.x);
			double dZ = getShift(currentCameraPosition.z, previousCameraPosition.z);

			if (dX != 0.0 || dZ != 0.0) {
				applyShift(dX, dZ);
			}
		}

		/**
		 * Shifts all current and future positions by the given amount. This is done in such a way that the difference
		 * between cameraPosition and previousCameraPosition remains the same, since they are completely arbitrary
		 * to the shader for the most part.
		 */
		private void applyShift(double dX, double dZ) {
			shift.x += dX;
			currentCameraPosition.x += dX;
			previousCameraPosition.x += dX;

			shift.z += dZ;
			currentCameraPosition.z += dZ;
			previousCameraPosition.z += dZ;
		}

		public Vector3d getCurrentCameraPosition() {
			return currentCameraPosition;
		}

		public Vector3d getPreviousCameraPosition() {
			return previousCameraPosition;
		}

		public Vector3d getPreviousCameraPositionUnshifted() {
			return previousCameraPositionUnshifted;
		}

		public float getCurrentCameraPositionY() {
			return (float) currentCameraPosition.y;
		}
	}
}

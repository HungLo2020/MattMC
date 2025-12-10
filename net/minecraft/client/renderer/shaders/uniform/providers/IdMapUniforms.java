// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Provides uniforms for held item IDs and light values.
 * 
 * Based on IRIS's IdMapUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/IdMapUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (6 uniforms):
 * - heldItemId, heldItemId2: Item IDs for main/off hand
 * - heldBlockLightValue, heldBlockLightValue2: Light emission values
 * - heldBlockLightColor, heldBlockLightColor2: Light colors
 * 
 * Note: This is a simplified version. Full IRIS version requires:
 * - IdMap for item ID mapping
 * - IrisItemLightProvider for light values
 * - NamespacedId for item identification
 * These will be implemented when full shader pack support is added.
 */
public final class IdMapUniforms {
	private static final Vector3f DEFAULT_LIGHT_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);

	private IdMapUniforms() {
	}

	public static void addIdMapUniforms(FrameUpdateNotifier notifier, UniformHolder uniforms) {
		HeldItemSupplier mainHandSupplier = new HeldItemSupplier(InteractionHand.MAIN_HAND);
		HeldItemSupplier offHandSupplier = new HeldItemSupplier(InteractionHand.OFF_HAND);
		notifier.addListener(mainHandSupplier::update);
		notifier.addListener(offHandSupplier::update);

		uniforms
			.uniform1i(PER_FRAME, "heldItemId", mainHandSupplier::getIntID)
			.uniform1i(PER_FRAME, "heldItemId2", offHandSupplier::getIntID)
			.uniform1i(PER_FRAME, "heldBlockLightValue", mainHandSupplier::getLightValue)
			.uniform1i(PER_FRAME, "heldBlockLightValue2", offHandSupplier::getLightValue)
			.uniform3f(PER_FRAME, "heldBlockLightColor", mainHandSupplier::getLightColor)
			.uniform3f(PER_FRAME, "heldBlockLightColor2", offHandSupplier::getLightColor);
	}

	/**
	 * Provides the currently held item, and its light value, in the given hand as a uniform.
	 * 
	 * Note: Simplified version - full IRIS version uses item.properties ID map.
	 */
	private static class HeldItemSupplier {
		private final InteractionHand hand;
		private int intID;
		private int lightValue;
		private Vector3f lightColor;

		HeldItemSupplier(InteractionHand hand) {
			this.hand = hand;
			this.intID = -1;
			this.lightValue = 0;
			this.lightColor = DEFAULT_LIGHT_COLOR;
		}

		private void invalidate() {
			intID = -1;
			lightValue = 0;
			lightColor = DEFAULT_LIGHT_COLOR;
		}

		public void update() {
			LocalPlayer player = Minecraft.getInstance().player;

			if (player == null) {
				// Not valid when the player doesn't exist
				invalidate();
				return;
			}

			ItemStack heldStack = player.getItemInHand(hand);

			if (heldStack == null || heldStack.isEmpty()) {
				invalidate();
				return;
			}

			// Simplified ID mapping - full IRIS version uses IdMap
			// For now, use a simple hash of the item's registry name
			intID = heldStack.getItem().toString().hashCode();

			// Simplified light value - full IRIS version uses IrisItemLightProvider
			// For now, return 0 (no light emission)
			lightValue = 0;

			// Default light color
			lightColor = DEFAULT_LIGHT_COLOR;
		}

		public int getIntID() {
			return intID;
		}

		public int getLightValue() {
			return lightValue;
		}

		public Vector3f getLightColor() {
			return lightColor;
		}
	}
}

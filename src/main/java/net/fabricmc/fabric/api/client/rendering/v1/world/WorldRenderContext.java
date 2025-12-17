/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.api.client.rendering.v1.world;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.TickRateManager;
import com.mojang.blaze3d.vertex.PoseStack;

@ApiStatus.NonExtendable
public interface WorldRenderContext extends WorldTerrainRenderContext {
	RenderBuffers commandQueue();

	PoseStack matrices();

	/**
	 * The {@code MultiBufferSource} instance being used by the world renderer for most non-terrain renders.
	 * Generally this will be better for most use cases because quads for the same layer can be buffered
	 * incrementally and then drawn all at once by the world renderer.
	 *
	 * <p>IMPORTANT - all vertex coordinates sent to consumers should be relative to the camera to
	 * be consistent with other quads emitted by the world renderer and other mods.  If this isn't
	 * possible, caller should use a separate "immediate" instance.
	 *
	 * <p>Renders that cannot draw in one of the supported events must be drawn directly to the frame buffer,
	 * preferably in {@link WorldRenderEvents#END_MAIN} to avoid being overdrawn or cleared.
	 */
	MultiBufferSource consumers();

	/**
	 * @deprecated Use extraction phase instead. This method provides legacy compatibility for mods
	 * built against Fabric API 0.115.0 (MC 1.21.1). Returns identity matrix in MC 1.21.10+.
	 */
	@Deprecated
	default Matrix4f projectionMatrix() {
		return new Matrix4f(); // Identity matrix
	}

	/**
	 * @deprecated Use extraction phase instead. This method provides legacy compatibility for mods
	 * built against Fabric API 0.115.0 (MC 1.21.1). Returns identity matrix in MC 1.21.10+.
	 */
	@Deprecated
	default Matrix4f positionMatrix() {
		return new Matrix4f(); // Identity matrix
	}

	/**
	 * @deprecated Use {@link WorldExtractionContext#world()} during extraction phase instead.
	 * This method provides legacy compatibility for mods built against Fabric API 0.115.0 (MC 1.21.1).
	 */
	@Deprecated
	default ClientLevel world() {
		return null;
	}

	/**
	 * @deprecated Use {@link WorldExtractionContext#tickCounter()} during extraction phase instead.
	 * This method provides legacy compatibility for mods built against Fabric API 0.115.0 (MC 1.21.1).
	 */
	@Deprecated
	default TickRateManager tickCounter() {
		return null;
	}
	
	/**
	 * Legacy compatibility method for Fabric API 0.115.0 (MC 1.21.1).
	 * Returns partial tick delta for rendering interpolation.
	 * In MC 1.21.10+, this should be obtained from the render context's frame data.
	 * For now, returns 1.0f as a safe default (full tick).
	 * 
	 * @deprecated In MC 1.21.10+, use proper frame-based rendering instead.
	 */
	@Deprecated
	default float getGameTimeDeltaTicks() {
		return 1.0f; // Default to full tick for compatibility
	}
}

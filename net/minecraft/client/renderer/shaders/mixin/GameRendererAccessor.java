package net.minecraft.client.renderer.shaders.mixin;

import net.minecraft.client.renderer.PostChain;

/**
 * Stub interface for GameRenderer mixin accessor
 * Full implementation requires mixin system
 */
public interface GameRendererAccessor {
	PostChain getPostEffect();
	void setPostEffect(PostChain effect);
}

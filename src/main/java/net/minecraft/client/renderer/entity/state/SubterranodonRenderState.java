package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * Render state for Subterranodon entity.
 * This holds the state needed for rendering the entity on the client.
 */
@Environment(EnvType.CLIENT)
public class SubterranodonRenderState extends LivingEntityRenderState {
    public boolean isFlying;
    public boolean isHovering;
    public float flapAmount;
    public int altSkin; // 0 = normal, 1 = retro, 2 = tectonic
    
    public SubterranodonRenderState() {
    }
}

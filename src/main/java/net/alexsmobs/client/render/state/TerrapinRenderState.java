package net.alexsmobs.client.render.state;

import net.alexsmobs.entity.util.TerrapinTypes;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class TerrapinRenderState extends LivingEntityRenderState {
    public TerrapinTypes turtleType;
    public int shellType;
    public int skinType;
    public int turtleColor;
    public int shellColor;
    public int skinColor;
    public boolean isKoopa;
    public float clientSpin;
    public int spinCounter;
    public float prevSwimProgress;
    public float swimProgress;
    public float prevRetreatProgress;
    public float retreatProgress;
    public float prevSpinProgress;
    public float spinProgress;
    public boolean isSpinning;
    public boolean hasRetreated;
    public float partialTick;
}

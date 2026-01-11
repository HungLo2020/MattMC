package com.github.alexthe666.citadel.client.event;

import com.github.alexthe666.citadel.server.event.EventMergeStructureSpawns.TriState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FluidState;

// TODO: Integrate with Fabric rendering events
@Environment(EnvType.CLIENT)
public class EventGetFluidRenderType {
    private FluidState fluidState;
    private RenderType renderType;
    private TriState result = TriState.DEFAULT;

    public EventGetFluidRenderType(FluidState fluidState, RenderType renderType) {
        this.fluidState = fluidState;
        this.renderType = renderType;
    }

    public FluidState getFluidState() {
        return fluidState;
    }

    public RenderType getRenderType() {
        return renderType;
    }

    public void setRenderType(RenderType renderType) {
        this.renderType = renderType;
    }

    public void setResult(TriState result) {
        this.result = result;
    }

    public TriState getResult() {
        return result;
    }
}

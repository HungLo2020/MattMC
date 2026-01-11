package com.github.alexthe666.citadel.client.event;

import com.github.alexthe666.citadel.server.event.EventMergeStructureSpawns.TriState;
import net.minecraft.client.multiplayer.ClientLevel;

// TODO: Integrate with Fabric rendering events
public class EventGetStarBrightness {
    private ClientLevel clientLevel;
    private float brightness;
    private float partialTicks;
    private TriState result = TriState.DEFAULT;

    public EventGetStarBrightness(ClientLevel clientLevel, float brightness, float partialTicks) {
        this.clientLevel = clientLevel;
        this.brightness = brightness;
        this.partialTicks = partialTicks;
    }

    public ClientLevel getLevel() {
        return clientLevel;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public void setResult(TriState result) {
        this.result = result;
    }

    public TriState getResult() {
        return result;
    }
}

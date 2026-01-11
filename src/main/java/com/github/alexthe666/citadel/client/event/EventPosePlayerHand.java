package com.github.alexthe666.citadel.client.event;

import com.github.alexthe666.citadel.server.event.EventMergeStructureSpawns.TriState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// TODO: Integrate with Fabric rendering events
public class EventPosePlayerHand {
    private LivingEntity entityIn;
    private HumanoidModel model;
    private boolean left;
    private TriState result = TriState.DEFAULT;

    public EventPosePlayerHand(LivingEntity entityIn, HumanoidModel model, boolean left) {
        this.entityIn = entityIn;
        this.model = model;
        this.left = left;
    }

    public Entity getEntityIn() {
        return entityIn;
    }

    public HumanoidModel getModel() {
        return model;
    }

    public boolean isLeftHand() {
        return left;
    }

    public void setResult(TriState result) {
        this.result = result;
    }

    public TriState getResult() {
        return result;
    }
}

package com.github.alexmodguy.alexscaves.server.entity.util;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.message.MultipartEntityMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

public abstract class ACMultipartEntity<T extends Entity> extends PartEntity<T> {

    public ACMultipartEntity(T parent) {
        super(parent);
        this.blocksBuilding = true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        Entity parent = this.getParent();
        if (parent == null) {
            return InteractionResult.PASS;
        } else {
            if (player.level().isClientSide) {
                AlexsCaves.sendMSGToServer(new MultipartEntityMessage(parent.getId(), player.getId(), 0));
            }
            return parent.interact(player, hand);
        }
    }

    @Override
    public boolean save(CompoundTag tag) {
        return false;
    }


    @Override
    public boolean canBeCollidedWith() {
        Entity parent = this.getParent();
        return parent != null && parent.canBeCollidedWith();
    }


    @Override
    public boolean isPickable() {
        Entity parent = this.getParent();
        return parent != null && parent.isPickable();
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity parent = this.getParent();
        if (!this.isInvulnerableTo(source) && parent != null) {
            Entity attacker = source.getEntity();
            if (attacker != null && attacker.level().isClientSide) {
                // Client-side: send packet to server to handle the attack
                AlexsCaves.sendMSGToServer(new MultipartEntityMessage(parent.getId(), attacker.getId(), 1));
                return true;
            } else if (attacker == null || !attacker.level().isClientSide) {
                // Server-side: directly apply damage to parent
                return parent.hurt(source, amount);
            }
        }
        return false;
    }

    public boolean is(Entity entityIn) {
        return this == entityIn || this.getParent() == entityIn;
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {

    }

    public boolean shouldBeSaved() {
        return false;
    }
}

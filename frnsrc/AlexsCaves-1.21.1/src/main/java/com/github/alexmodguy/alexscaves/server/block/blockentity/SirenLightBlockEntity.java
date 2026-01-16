package com.github.alexmodguy.alexscaves.server.block.blockentity;

import com.github.alexmodguy.alexscaves.server.block.SirenLightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class SirenLightBlockEntity extends BlockEntity {
    private float onProgress;
    private float prevOnProgress;

    private float sirenRotation;
    private float prevSirenRotation;

    private int color = -1;

    public SirenLightBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntityRegistry.SIREN_LIGHT.get(), pos, state);
        if (state.getValue(SirenLightBlock.POWERED)) {
            prevOnProgress = onProgress = 10.0F;
        }
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, SirenLightBlockEntity entity) {
        entity.prevOnProgress = entity.onProgress;
        entity.prevSirenRotation = entity.sirenRotation;
        boolean powered = state.getValue(SirenLightBlock.POWERED);

        if (powered && entity.onProgress < 10.0F) {
            entity.onProgress += 1F;
        } else if (!powered && entity.onProgress > 0.0F) {
            entity.onProgress -= 1F;
        }
        if (powered) {
            entity.sirenRotation += entity.onProgress * 2F + 0.25F;
        }
    }

    public float getOnProgress(float partialTicks) {
        return (prevOnProgress + (onProgress - prevOnProgress) * partialTicks) * 0.1F;
    }

    public float getSirenRotation(float partialTicks) {
        return (prevSirenRotation + (sirenRotation - prevSirenRotation) * partialTicks);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.color = tag.getInt("Color");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Color", this.color);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        if (packet != null && packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }


    public boolean setColor(int setTo) {
        if (this.color == setTo) return false;
        this.color = setTo;
        this.setChanged();
        level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
        return true;
    }

    public int getColor() {
        return color < 0 ? 0X00FF00 : color;
    }

    @OnlyIn(Dist.CLIENT)
    public AABB getRenderBoundingBox() {
        BlockPos pos = this.getBlockPos();
        BlockPos min = pos.offset(-3, -3, -3);
        BlockPos max = pos.offset(4, 4, 4);
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

}

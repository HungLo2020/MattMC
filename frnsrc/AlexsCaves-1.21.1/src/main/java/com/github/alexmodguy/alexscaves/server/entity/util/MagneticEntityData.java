package com.github.alexmodguy.alexscaves.server.entity.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data class for storing magnetic entity data.
 * Used with NeoForge's Attachment API to store magnetic state on entities.
 */
public class MagneticEntityData {
    
    public static final MagneticEntityData DEFAULT = new MagneticEntityData(0f, 0f, 0f, Direction.DOWN);
    
    public static final Codec<MagneticEntityData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.FLOAT.fieldOf("deltaX").forGetter(MagneticEntityData::getDeltaX),
            Codec.FLOAT.fieldOf("deltaY").forGetter(MagneticEntityData::getDeltaY),
            Codec.FLOAT.fieldOf("deltaZ").forGetter(MagneticEntityData::getDeltaZ),
            Direction.CODEC.fieldOf("attachmentDirection").forGetter(MagneticEntityData::getAttachmentDirection)
        ).apply(instance, MagneticEntityData::new)
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticEntityData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, MagneticEntityData::getDeltaX,
        ByteBufCodecs.FLOAT, MagneticEntityData::getDeltaY,
        ByteBufCodecs.FLOAT, MagneticEntityData::getDeltaZ,
        ByteBufCodecs.fromCodec(Direction.CODEC), MagneticEntityData::getAttachmentDirection,
        MagneticEntityData::new
    );
    
    private float deltaX;
    private float deltaY;
    private float deltaZ;
    private Direction attachmentDirection;
    
    public MagneticEntityData(float deltaX, float deltaY, float deltaZ, Direction attachmentDirection) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.deltaZ = deltaZ;
        this.attachmentDirection = attachmentDirection;
    }
    
    public float getDeltaX() {
        return deltaX;
    }
    
    public void setDeltaX(float deltaX) {
        this.deltaX = deltaX;
    }
    
    public float getDeltaY() {
        return deltaY;
    }
    
    public void setDeltaY(float deltaY) {
        this.deltaY = deltaY;
    }
    
    public float getDeltaZ() {
        return deltaZ;
    }
    
    public void setDeltaZ(float deltaZ) {
        this.deltaZ = deltaZ;
    }
    
    public Direction getAttachmentDirection() {
        return attachmentDirection;
    }
    
    public void setAttachmentDirection(Direction attachmentDirection) {
        this.attachmentDirection = attachmentDirection;
    }
    
    public MagneticEntityData copy() {
        return new MagneticEntityData(deltaX, deltaY, deltaZ, attachmentDirection);
    }
    
    public boolean isDefault() {
        return deltaX == 0f && deltaY == 0f && deltaZ == 0f && attachmentDirection == Direction.DOWN;
    }
}

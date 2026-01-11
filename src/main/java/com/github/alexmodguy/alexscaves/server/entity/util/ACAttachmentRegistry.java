package com.github.alexmodguy.alexscaves.server.entity.util;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registry for NeoForge Attachment Types used by Alex's Caves.
 * Attachments are used to store custom data on entities without requiring
 * SynchedEntityData (which causes ID conflicts with vanilla entities in 1.21).
 */
public class ACAttachmentRegistry {
    
    public static final DeferredRegister<AttachmentType<?>> DEF_REG = 
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AlexsCaves.MODID);
    
    /**
     * Attachment for magnetic entity data (delta movement and attachment direction).
     * This replaces the old SynchedEntityData-based approach that caused ID conflicts.
     */
    public static final Supplier<AttachmentType<MagneticEntityData>> MAGNETIC_DATA = DEF_REG.register(
        "magnetic_data",
        () -> AttachmentType.builder(() -> new MagneticEntityData(0f, 0f, 0f, Direction.DOWN))
            .serialize(MagneticEntityData.CODEC)
            .copyOnDeath()
            .build()
    );
}

package com.github.alexmodguy.alexscaves.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    // In 1.21, tickingSounds is a List<TickableSoundInstance>, not a Map
    @Accessor("tickingSounds")
    List<TickableSoundInstance> getTickingSounds();

    // In 1.21, queuedTickableSounds is List<TickableSoundInstance>
    @Accessor("queuedTickableSounds")
    List<TickableSoundInstance> getQueuedTickableSounds();
}

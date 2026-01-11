package com.github.alexmodguy.alexscaves.server.misc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Custom advancement trigger for NeoForge 1.21.
 * Uses Codec-based serialization instead of the old JSON parsing.
 */
public class ACAdvancementTrigger extends SimpleCriterionTrigger<ACAdvancementTrigger.Instance> {
    
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, (instance) -> true);
    }

    public void triggerForEntity(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            trigger(serverPlayer);
        }
    }

    public static record Instance(Optional<ContextAwarePredicate> player) implements SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
            ).apply(instance, Instance::new)
        );

        public static Instance simple() {
            return new Instance(Optional.empty());
        }

        @Override
        public Optional<ContextAwarePredicate> player() {
            return this.player;
        }
    }
}

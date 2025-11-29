package net.matt.quantize.tags;

import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class QDamageTypes {

    public static final ResourceKey<DamageType> ACID = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("acid"));
    public static final ResourceKey<DamageType> NUKE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("nuke"));
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("radiation"));
    public static final ResourceKey<DamageType> RAYGUN = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("raygun"));
    public static final ResourceKey<DamageType> FORSAKEN_SONIC_BOOM = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("forsaken_sonic_boom"));
    public static final ResourceKey<DamageType> DESOLATE_DAGGER = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("desolate_dagger"));
    public static final ResourceKey<DamageType> DARK_ARROW = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("dark_arrow"));
    public static final ResourceKey<DamageType> SPIRIT_DINOSAUR = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("spirit_dinosaur"));
    public static final ResourceKey<DamageType> TREMORZILLA_BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("tremorzilla_beam"));
    public static final ResourceKey<DamageType> GUMBALL = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("gumball"));
    public static final ResourceKey<DamageType> INTENTIONAL_GAME_DESIGN = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("intentional_game_design"));
    public static final ResourceKey<DamageType> FREDDY = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceIdentifier("freddy"));
    public static DamageSource causeAcidDamage(RegistryAccess registryAccess) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(ACID), 1);
    }

    public static DamageSource causeFreddyBearDamage(LivingEntity attacker){
        return new DamageSource(attacker.level().registryAccess().registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(FREDDY), attacker);
    }

    public static DamageSource causeRadiationDamage(RegistryAccess registryAccess) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(RADIATION), 2);
    }

    public static DamageSource causeNukeDamage(RegistryAccess registryAccess) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(NUKE), 4);
    }

    public static DamageSource causeRaygunDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(RAYGUN), source, 1);
    }

    public static DamageSource causeForsakenSonicBoomDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(FORSAKEN_SONIC_BOOM), source, 2);
    }

    public static DamageSource causeDesolateDaggerDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(DESOLATE_DAGGER), source, 1);
    }

    public static DamageSource causeDarkArrowDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(DARK_ARROW), source, 1);
    }

    public static DamageSource causeSpiritDinosaurDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(SPIRIT_DINOSAUR), source, 1);
    }

    public static DamageSource causeTremorzillaBeamDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(TREMORZILLA_BEAM), source, 1);
    }

    public static DamageSource causeGumballDamage(RegistryAccess registryAccess, Entity source) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(GUMBALL), source, 1);
    }

    public static DamageSource causeIntentionalGameDesign(RegistryAccess registryAccess) {
        return new DamageSourceRandomMessages(registryAccess.registry(Registries.DAMAGE_TYPE).get().getHolderOrThrow(INTENTIONAL_GAME_DESIGN), 1);
    }


    private static class DamageSourceRandomMessages extends DamageSource {

        private int messageCount;

        public DamageSourceRandomMessages(Holder.Reference<DamageType> message, int messageCount) {
            super(message);
            this.messageCount = messageCount;
        }

        public DamageSourceRandomMessages(Holder.Reference<DamageType> message, Entity source, int messageCount) {
            super(message, source);
            this.messageCount = messageCount;
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity attacked) {
            int type = attacked.getRandom().nextInt(this.messageCount);
            String s = "death.attack." + this.getMsgId() + "_" + type;
            Entity entity = this.getDirectEntity() == null ? this.getEntity() : this.getDirectEntity();
            if (entity != null) {
                return Component.translatable(s + ".entity", attacked.getDisplayName(), entity.getDisplayName());
            }else{
                return Component.translatable(s, attacked.getDisplayName());
            }
        }
    }
}

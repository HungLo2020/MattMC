package com.github.alexmodguy.alexscaves.server.misc;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public class CabinMapLootModifier implements IGlobalLootModifier {
    public static final MapCodec<CabinMapLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst ->
                    inst.group(
                                    LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(lm -> lm.conditions)
                            )
                            .apply(inst, CabinMapLootModifier::new));

    private final LootItemCondition[] conditions;

    private final Predicate<LootContext> orConditions;

    protected CabinMapLootModifier(LootItemCondition[] conditionsIn) {
        this.conditions = conditionsIn;
        this.orConditions = (context) -> {
            for (LootItemCondition condition : conditionsIn) {
                if (condition.test(context)) return true;
            }
            return false;
        };
    }

    @NotNull
    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        return this.orConditions.test(context) ? this.doApply(generatedLoot, context) : generatedLoot;
    }

    @Nonnull
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.getRandom().nextFloat() < getChance() && context.hasParam(LootContextParams.ORIGIN)) {
            ServerLevel serverlevel = context.getLevel();
            BlockPos chestPos = BlockPos.containing(context.getParam(LootContextParams.ORIGIN));
            BlockPos blockpos = serverlevel.findNearestMapStructure(ACTagRegistry.ON_UNDERGROUND_CABIN_MAPS, chestPos, 100, true);
            if(blockpos != null){
                ItemStack itemstack = MapItem.create(serverlevel, blockpos.getX(), blockpos.getZ(), (byte)2, true, true);
                MapItem.renderBiomePreviewMap(serverlevel, itemstack);
                MapItemSavedData.addTargetDecoration(itemstack, blockpos, "+", ACVanillaMapUtil.getUndergroundCabinDecoration());
                itemstack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.alexscaves.underground_cabin_explorer_map"));
                generatedLoot.add(itemstack);
            }

        }
        return generatedLoot;
    }

    private float getChance() {
        return AlexsCaves.COMMON_CONFIG.cabinMapLootChance.get().floatValue();
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}

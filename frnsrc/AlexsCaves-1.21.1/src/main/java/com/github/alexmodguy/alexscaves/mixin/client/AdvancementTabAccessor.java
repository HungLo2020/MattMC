package com.github.alexmodguy.alexscaves.mixin.client;

import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * In 1.21, AdvancementTab no longer has a direct 'rootAdvancement' field.
 * Instead, it has a 'root' field of type AdvancementWidget, which contains the advancement.
 * Use getRootWidget() to get the root AdvancementWidget, then use AdvancementWidgetAccessor
 * to get the AdvancementHolder from it.
 */
@Mixin(AdvancementTab.class)
public interface AdvancementTabAccessor {
    @Accessor("root")
    AdvancementWidget getRootWidget();
}

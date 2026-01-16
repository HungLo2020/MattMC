package com.github.alexmodguy.alexscaves.mixin.client;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/**
 * In 1.21, AdvancementWidget uses 'advancementNode' of type AdvancementNode,
 * not 'advancement' of type AdvancementHolder as in 1.20.
 */
@Mixin(AdvancementWidget.class)
public interface AdvancementWidgetAccessor {
    @Accessor("progress")
    @Nullable
    AdvancementProgress getProgress();

    @Accessor("parent")
    @Nullable
    AdvancementWidget getParentWidget();

    // In 1.21, the field is named 'advancementNode' of type AdvancementNode
    @Accessor("advancementNode")
    AdvancementNode getAdvancementNode();
}

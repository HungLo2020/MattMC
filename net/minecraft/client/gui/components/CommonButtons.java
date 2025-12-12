package net.minecraft.client.gui.components;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class CommonButtons {
	public static SpriteIconButton accessibility(int i, Button.OnPress onPress, boolean bl) {
		Component component = bl ? Component.translatable("options.accessibility") : Component.translatable("accessibility.onboarding.accessibility.button");
		return SpriteIconButton.builder(component, onPress, bl).width(i).sprite(ResourceLocation.withDefaultNamespace("icon/accessibility"), 15, 15).build();
	}
}

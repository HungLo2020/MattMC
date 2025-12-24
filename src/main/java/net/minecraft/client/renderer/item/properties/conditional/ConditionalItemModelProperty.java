package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface ConditionalItemModelProperty extends ItemModelPropertyTest {
	MapCodec<? extends ConditionalItemModelProperty> type();
}

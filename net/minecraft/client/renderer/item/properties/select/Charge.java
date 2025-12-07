package net.minecraft.client.renderer.item.properties.select;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem.ChargeType;
import net.minecraft.world.item.component.ChargedProjectiles;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record Charge() implements SelectItemModelProperty<ChargeType> {
	public static final Codec<ChargeType> VALUE_CODEC = ChargeType.CODEC;
	public static final SelectItemModelProperty.Type<Charge, ChargeType> TYPE = SelectItemModelProperty.Type.create(MapCodec.unit(new Charge()), VALUE_CODEC);

	public ChargeType get(
		ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext
	) {
		ChargedProjectiles chargedProjectiles = (ChargedProjectiles)itemStack.get(DataComponents.CHARGED_PROJECTILES);
		if (chargedProjectiles == null || chargedProjectiles.isEmpty()) {
			return ChargeType.NONE;
		} else {
			return chargedProjectiles.contains(Items.FIREWORK_ROCKET) ? ChargeType.ROCKET : ChargeType.ARROW;
		}
	}

	@Override
	public SelectItemModelProperty.Type<Charge, ChargeType> type() {
		return TYPE;
	}

	@Override
	public Codec<ChargeType> valueCodec() {
		return VALUE_CODEC;
	}
}

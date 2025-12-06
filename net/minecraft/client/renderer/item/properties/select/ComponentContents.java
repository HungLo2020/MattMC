package net.minecraft.client.renderer.item.properties.select;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ComponentContents<T>(DataComponentType<T> componentType) implements SelectItemModelProperty<T> {
	private static final SelectItemModelProperty.Type<? extends ComponentContents<?>, ?> TYPE = createType();

	private static <T> SelectItemModelProperty.Type<ComponentContents<T>, T> createType() {
		Codec<? extends DataComponentType<?>> codec = BuiltInRegistries.DATA_COMPONENT_TYPE
			.byNameCodec()
			.validate(
				dataComponentType -> dataComponentType.isTransient() ? DataResult.error(() -> "Component can't be serialized") : DataResult.success(dataComponentType)
			);
		@SuppressWarnings("unchecked")
		MapCodec<SelectItemModel.UnbakedSwitch<ComponentContents<T>, T>> mapCodec = (MapCodec<SelectItemModel.UnbakedSwitch<ComponentContents<T>, T>>)(MapCodec<?>)codec.dispatchMap(
			"component",
			unbakedSwitch -> ((ComponentContents<?>)unbakedSwitch.property()).componentType,
			dataComponentType -> SelectItemModelProperty.Type.createCasesFieldCodec(dataComponentType.codecOrThrow())
				.xmap(list -> new SelectItemModel.UnbakedSwitch<>(new ComponentContents<>(dataComponentType), list), SelectItemModel.UnbakedSwitch::cases)
		);
		return new SelectItemModelProperty.Type<>(mapCodec);
	}

	public static <T> SelectItemModelProperty.Type<ComponentContents<T>, T> castType() {
		return (SelectItemModelProperty.Type<ComponentContents<T>, T>)TYPE;
	}

	@Nullable
	@Override
	public T get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		return (T)itemStack.get(this.componentType);
	}

	@Override
	public SelectItemModelProperty.Type<ComponentContents<T>, T> type() {
		return castType();
	}

	@Override
	public Codec<T> valueCodec() {
		return this.componentType.codecOrThrow();
	}
}

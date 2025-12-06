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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> SelectItemModelProperty.Type<ComponentContents<T>, T> createType() {
		Codec<DataComponentType<?>> codec = BuiltInRegistries.DATA_COMPONENT_TYPE
			.byNameCodec()
			.validate(
				dataComponentType -> dataComponentType.isTransient() ? DataResult.error(() -> "Component can't be serialized") : DataResult.success(dataComponentType)
			);
		MapCodec<SelectItemModel.UnbakedSwitch<ComponentContents<T>, T>> mapCodec = (MapCodec)codec.dispatchMap(
			"component",
			(SelectItemModel.UnbakedSwitch unbakedSwitch) -> ((ComponentContents<?>)unbakedSwitch.property()).componentType,
			dataComponentType -> SelectItemModelProperty.Type.createCasesFieldCodec(dataComponentType.codecOrThrow())
				.xmap(list -> {
					SelectItemModel.UnbakedSwitch result = new SelectItemModel.UnbakedSwitch(new ComponentContents(dataComponentType), list);
					return result;
				}, (SelectItemModel.UnbakedSwitch s) -> s.cases())
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

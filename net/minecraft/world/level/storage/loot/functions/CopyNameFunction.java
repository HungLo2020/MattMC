package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class CopyNameFunction extends LootItemConditionalFunction {
	private static final ExtraCodecs.LateBoundIdMapper<String, CopyNameFunction.Source> SOURCES = new ExtraCodecs.LateBoundIdMapper<>();
	public static final MapCodec<CopyNameFunction> CODEC;
	private final CopyNameFunction.Source source;

	private CopyNameFunction(List<LootItemCondition> list, CopyNameFunction.Source source) {
		super(list);
		this.source = source;
	}

	@Override
	public LootItemFunctionType<CopyNameFunction> getType() {
		return LootItemFunctions.COPY_NAME;
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(this.source.param);
	}

	@Override
	public ItemStack run(ItemStack itemStack, LootContext lootContext) {
		if (lootContext.getOptionalParameter(this.source.param) instanceof Nameable nameable) {
			itemStack.set(DataComponents.CUSTOM_NAME, nameable.getCustomName());
		}

		return itemStack;
	}

	public static LootItemConditionalFunction.Builder<?> copyName(CopyNameFunction.Source source) {
		return simpleBuilder(list -> new CopyNameFunction(list, source));
	}

	static {
		for (LootContext.EntityTarget entityTarget : LootContext.EntityTarget.values()) {
			SOURCES.put(entityTarget.getSerializedName(), new CopyNameFunction.Source(entityTarget.getParam()));
		}

		for (LootContext.BlockEntityTarget blockEntityTarget : LootContext.BlockEntityTarget.values()) {
			SOURCES.put(blockEntityTarget.getSerializedName(), new CopyNameFunction.Source(blockEntityTarget.getParam()));
		}

		CODEC = RecordCodecBuilder.mapCodec(
			instance -> commonFields(instance)
				.and(SOURCES.codec(Codec.STRING).fieldOf("source").forGetter(copyNameFunction -> copyNameFunction.source))
				.apply(instance, CopyNameFunction::new)
		);
	}

	public record Source(ContextKey<?> param) {
	}
}

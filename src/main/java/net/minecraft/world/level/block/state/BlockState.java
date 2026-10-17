package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState extends BlockBehaviour.BlockStateBase {
	public static final Codec<BlockState> CODEC = codec(BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState).stable();

	public BlockState(Block block, Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2ObjectArrayMap, MapCodec<BlockState> mapCodec) {
		super(block, reference2ObjectArrayMap, mapCodec);
	}

    BlockState(Block owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<BlockState> codec, int nativeTraits, net.minecraft.world.level.block.SoundType nativeSound, NativeBlockMaterials.Offset nativeOffset, int nativePolicy) {
        super(owner, values, codec, nativeTraits, nativeSound, nativeOffset, nativePolicy);
    }

	@Override
	protected BlockState asState() {
		return this;
	}
}

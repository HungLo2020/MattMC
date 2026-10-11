package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * A multipart blockstate's model for one state: the selectors whose
 * conditions the state meets (evaluated by Rust's model loader,
 * `assets/models/blockstate.rs`), drawing from the definition's shared
 * baked selectors.
 */
@Environment(EnvType.CLIENT)
public class MultiPartModel implements BlockStateModel {
	private final MultiPartModel.SharedBakedState shared;
	private final BitSet selected;
	@Nullable
	private List<BlockStateModel> models;

	public MultiPartModel(MultiPartModel.SharedBakedState sharedBakedState, BitSet selected) {
		this.shared = sharedBakedState;
		this.selected = selected;
	}

	/** A multipart definition's baked selectors, in file order. */
	public static MultiPartModel.SharedBakedState shared(List<BlockStateModel> selectors) {
		return new MultiPartModel.SharedBakedState(List.copyOf(selectors));
	}

	@Override
	public TextureAtlasSprite particleIcon() {
		return this.shared.particleIcon;
	}

	@Override
	public void collectParts(RandomSource randomSource, List<BlockModelPart> list) {
		if (this.models == null) {
			this.models = this.shared.selectModels(this.selected);
		}

		long l = randomSource.nextLong();

		for (BlockStateModel blockStateModel : this.models) {
			randomSource.setSeed(l);
			blockStateModel.collectParts(randomSource, list);
		}
	}

	public List<BlockStateModel> sodium$getSelectedModels() {
		if (this.models == null) {
			this.models = this.shared.selectModels(this.selected);
		}
		return this.models;
	}

	@Environment(EnvType.CLIENT)
	public static final class SharedBakedState {
		private final List<BlockStateModel> selectors;
		final TextureAtlasSprite particleIcon;
		private final Map<BitSet, List<BlockStateModel>> subsets = new ConcurrentHashMap();

		SharedBakedState(List<BlockStateModel> list) {
			if (list.isEmpty()) {
				throw new IllegalArgumentException("Model must have at least one selector");
			}
			this.selectors = list;
			this.particleIcon = list.getFirst().particleIcon();
		}

		List<BlockStateModel> selectModels(BitSet bitSet) {
			return (List<BlockStateModel>)this.subsets.computeIfAbsent(bitSet, bitSetx -> {
				Builder<BlockStateModel> builder = ImmutableList.builder();

				for (int i = 0; i < this.selectors.size(); i++) {
					if (bitSetx.get(i)) {
						builder.add(this.selectors.get(i));
					}
				}

				return builder.build();
			});
		}
	}
}

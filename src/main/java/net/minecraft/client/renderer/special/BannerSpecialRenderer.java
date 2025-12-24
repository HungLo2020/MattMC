package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class BannerSpecialRenderer implements SpecialModelRenderer<BannerPatternLayers> {
	private final BannerRenderer bannerRenderer;
	private final DyeColor baseColor;

	public BannerSpecialRenderer(DyeColor dyeColor, BannerRenderer bannerRenderer) {
		this.bannerRenderer = bannerRenderer;
		this.baseColor = dyeColor;
	}

	@Nullable
	public BannerPatternLayers extractArgument(ItemStack itemStack) {
		return (BannerPatternLayers)itemStack.get(DataComponents.BANNER_PATTERNS);
	}

	public void submit(
		@Nullable BannerPatternLayers bannerPatternLayers,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		int j,
		boolean bl,
		int k
	) {
		this.bannerRenderer
			.submitSpecial(
				poseStack, submitNodeCollector, i, j, this.baseColor, (BannerPatternLayers)Objects.requireNonNullElse(bannerPatternLayers, BannerPatternLayers.EMPTY), k
			);
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		this.bannerRenderer.getExtents(set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(DyeColor baseColor) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<BannerSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(DyeColor.CODEC.fieldOf("color").forGetter(BannerSpecialRenderer.Unbaked::baseColor))
				.apply(instance, BannerSpecialRenderer.Unbaked::new)
		);

		@Override
		public MapCodec<BannerSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new BannerSpecialRenderer(this.baseColor, new BannerRenderer(bakingContext));
		}
	}
}

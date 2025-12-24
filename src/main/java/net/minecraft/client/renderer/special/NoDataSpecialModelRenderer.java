package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface NoDataSpecialModelRenderer extends SpecialModelRenderer<Void> {
	@Nullable
	default Void extractArgument(ItemStack itemStack) {
		return null;
	}

	default void submit(
		@Nullable Void void_, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k
	) {
		this.submit(itemDisplayContext, poseStack, submitNodeCollector, i, j, bl, k);
	}

	void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k);
}

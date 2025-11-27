package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IronGolemFlowerLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
   private final BlockRenderDispatcher blockRenderer;

   public IronGolemFlowerLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> pRenderer, BlockRenderDispatcher pBlockRenderer) {
      super(pRenderer);
      this.blockRenderer = pBlockRenderer;
   }

   public void render(PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, IronGolem pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
      if (pLivingEntity.getOfferFlowerTick() != 0) {
         pMatrixStack.pushPose();
         ModelPart modelpart = this.getParentModel().getFlowerHoldingArm();
         modelpart.translateAndRotate(pMatrixStack);
         pMatrixStack.translate(-1.1875F, 1.0625F, -0.9375F);
         pMatrixStack.translate(0.5F, 0.5F, 0.5F);
         float f = 0.5F;
         pMatrixStack.scale(0.5F, 0.5F, 0.5F);
         pMatrixStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         pMatrixStack.translate(-0.5F, -0.5F, -0.5F);
         this.blockRenderer.renderSingleBlock(Blocks.POPPY.defaultBlockState(), pMatrixStack, pBuffer, pPackedLight, OverlayTexture.NO_OVERLAY);
         pMatrixStack.popPose();
      }
   }
}
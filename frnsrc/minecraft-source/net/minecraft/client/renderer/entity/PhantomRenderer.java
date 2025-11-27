package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.PhantomEyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantomRenderer extends MobRenderer<Phantom, PhantomModel<Phantom>> {
   private static final ResourceLocation PHANTOM_LOCATION = new ResourceLocation("textures/entity/phantom.png");

   public PhantomRenderer(EntityRendererProvider.Context p_174338_) {
      super(p_174338_, new PhantomModel<>(p_174338_.bakeLayer(ModelLayers.PHANTOM)), 0.75F);
      this.addLayer(new PhantomEyesLayer<>(this));
   }

   /**
    * Returns the location of an entity's texture.
    */
   public ResourceLocation getTextureLocation(Phantom pEntity) {
      return PHANTOM_LOCATION;
   }

   protected void scale(Phantom pLivingEntity, PoseStack pMatrixStack, float pPartialTickTime) {
      int i = pLivingEntity.getPhantomSize();
      float f = 1.0F + 0.15F * (float)i;
      pMatrixStack.scale(f, f, f);
      pMatrixStack.translate(0.0F, 1.3125F, 0.1875F);
   }

   protected void setupRotations(Phantom pEntityLiving, PoseStack pMatrixStack, float pAgeInTicks, float pRotationYaw, float pPartialTicks) {
      super.setupRotations(pEntityLiving, pMatrixStack, pAgeInTicks, pRotationYaw, pPartialTicks);
      pMatrixStack.mulPose(Axis.XP.rotationDegrees(pEntityLiving.getXRot()));
   }
}
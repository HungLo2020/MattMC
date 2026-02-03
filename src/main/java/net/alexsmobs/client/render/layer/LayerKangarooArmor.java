package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.KangarooModel;
import net.alexsmobs.client.render.KangarooRenderer;
import net.alexsmobs.client.render.KangarooRenderState;
import com.google.common.collect.Maps;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class LayerKangarooArmor extends RenderLayer<KangarooRenderState, KangarooModel> {

    private static final Map<String, ResourceLocation> ARMOR_TEXTURE_RES_MAP = Maps.newHashMap();
    private final HumanoidModel defaultBipedModel;
    private final KangarooRenderer renderer;

    public LayerKangarooArmor(KangarooRenderer render, EntityRendererProvider.Context context) {
        super(render);
        defaultBipedModel = new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_ARMOR.chest()));
        this.renderer = render;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, KangarooRenderState renderState, float bob, float yRot) {
        // This layer needs access to entity for armor items and isRoger() check
        // TODO: Add armor equipment to KangarooRenderState if needed
    }

    private void translateToHead(PoseStack matrixStackIn) {
        translateToChest(matrixStackIn);
        this.renderer.getModel().neck.translateAndRotate(matrixStackIn);
        this.renderer.getModel().head.translateAndRotate(matrixStackIn);
    }

    private void translateToChest(PoseStack matrixStackIn) {
        this.renderer.getModel().root.translateAndRotate(matrixStackIn);
        this.renderer.getModel().body.translateAndRotate(matrixStackIn);
        this.renderer.getModel().chest.translateAndRotate(matrixStackIn);
    }

    protected void setModelSlotVisible(HumanoidModel p_188359_1_, EquipmentSlot slotIn) {
        this.setModelVisible(p_188359_1_);
        switch (slotIn) {
            case HEAD -> {
                p_188359_1_.head.visible = true;
                p_188359_1_.hat.visible = true;
            }
            case CHEST -> {
                p_188359_1_.body.visible = true;
                p_188359_1_.rightArm.visible = true;
                p_188359_1_.leftArm.visible = true;
            }
            case LEGS -> {
                p_188359_1_.body.visible = true;
                p_188359_1_.rightLeg.visible = true;
                p_188359_1_.leftLeg.visible = true;
            }
            case FEET -> {
                p_188359_1_.rightLeg.visible = true;
                p_188359_1_.leftLeg.visible = true;
            }
        }
    }

    protected void setModelVisible(HumanoidModel model) {
        model.setAllVisible(false);
    }

    protected HumanoidModel<?> getArmorModelHook(LivingEntity entity, ItemStack itemStack, EquipmentSlot slot,
            HumanoidModel model) {
        return model;
    }
}

package net.minecraft.client.renderer.entity.layers;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LayerTintSemanticTest {
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test @SuppressWarnings("unchecked")
    void cracksCopyNeutralTintIndependentlyOfOutlineColor() throws Exception {
        var layer=mock(WolfArmorLayer.class,CALLS_REAL_METHODS);
        var collector=mock(SubmitNodeCollector.class);
        Model<WolfRenderState> model=mock(Model.class);
        var state=new WolfRenderState();state.outlineColor=0xff00aa88;
        var item=new ItemStack(Items.WOLF_ARMOR);item.setDamageValue(item.getMaxDamage()-1);
        var pose=new PoseStack();
        var submit=WolfArmorLayer.class.getDeclaredMethod("maybeRenderCracks",PoseStack.class,
            SubmitNodeCollector.class,int.class,ItemStack.class,Model.class,WolfRenderState.class);
        submit.setAccessible(true);submit.invoke(layer,pose,collector,15728880,item,model,state);
        verify(collector).submitModelSemanticTexture(same(model),same(state),same(pose),any(RenderType.class),
            eq(15728880),eq(OverlayTexture.NO_OVERLAY),eq(-1),
            eq(ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_high.png")),
            eq(state.outlineColor),isNull());
    }
    @Test @SuppressWarnings("unchecked")
    void spinEffectCopiesNeutralTintIndependentlyOfOutlineColor(){
        var models=mock(net.minecraft.client.model.geom.EntityModelSet.class);
        when(models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SPIN_ATTACK))
            .thenReturn(net.minecraft.client.model.SpinAttackEffectModel.createLayer().bakeRoot());
        var parent=mock(net.minecraft.client.renderer.entity.RenderLayerParent.class);
        var layer=new SpinAttackEffectLayer(parent,models);
        var state=new net.minecraft.client.renderer.entity.state.AvatarRenderState();
        state.isAutoSpinAttack=true;state.outlineColor=0xff00aa88;
        var collector=mock(SubmitNodeCollector.class);var pose=new PoseStack();
        layer.submit(pose,collector,15728880,state,0F,0F);
        verify(collector).submitModelSemanticTexture(any(Model.class),same(state),same(pose),any(RenderType.class),
            eq(15728880),eq(OverlayTexture.NO_OVERLAY),eq(-1),any(ResourceLocation.class),eq(state.outlineColor),isNull());
    }

}

package net.minecraft.client.dev;

import com.google.gson.JsonParser;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditWolfInputsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void completedDrawObservationCopiesViewAndIsBounded() {
        String key="mattmc.dev.graphicsAuditWolfArmor",old=System.getProperty(key);
        try {
            System.setProperty(key,"high");
            GraphicsAuditWolfInputs.begin(GraphicsAuditGroundFoilSources.currentFrameIndex());
            var texture=net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_high.png");
            var view=new org.joml.Matrix4f().translation(1F,2F,3F);
            var pipeline=net.minecraft.client.renderer.RenderPipelines.ARMOR_TRANSLUCENT;
            GraphicsAuditWolfInputs.observeCompletedDraw(texture,pipeline,264,396,view);
            view.identity();
            var captured=JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject();
            var rows=captured.getAsJsonArray("completedDraws");
            assertEquals(1,rows.size());
            assertEquals(1F,rows.get(0).getAsJsonObject().getAsJsonArray("view").get(12).getAsFloat());
            assertEquals(396,rows.get(0).getAsJsonObject().get("indices").getAsInt());
            for(int i=0;i<16;i++)GraphicsAuditWolfInputs.observeCompletedDraw(texture,pipeline,264,396,view);
            captured=JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject();
            assertEquals(16,captured.getAsJsonArray("completedDraws").size());
            assertFalse(captured.get("complete").getAsBoolean());
            assertEquals(1,rows.size());
            GraphicsAuditWolfInputs.begin(77);
            assertEquals(0,JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject().getAsJsonArray("completedDraws").size());
        } finally {if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
    }
    @Test void onlySettledAuthoredAgesPass() {
        for(int pose=0;pose<5;pose++) {
            assertTrue(GraphicsAuditWolfInputs.settledAge(pose,pose*10+1));
            assertFalse(GraphicsAuditWolfInputs.settledAge(pose,pose*10+.99F));
            assertFalse(GraphicsAuditWolfInputs.settledAge(pose,pose*10+1.01F));
        }
        assertFalse(GraphicsAuditWolfInputs.settledAge(-1,-9));
        assertFalse(GraphicsAuditWolfInputs.settledAge(5,51));
        assertFalse(GraphicsAuditWolfInputs.settledAge(0,Float.NaN));
    }
    @Test void copiesRealModelWithoutMutationAndRejectsStaleOrInvalidInput() {
        String key="mattmc.dev.graphicsAuditWolfArmor",old=System.getProperty(key);
        try {
            System.clearProperty(key);
            var model=new WolfModel(LayerDefinition.create(WolfModel.createMeshDefinition(CubeDeformation.NONE),64,32).bakeRoot());
            var state=new WolfRenderState();state.yRot=12F;state.xRot=4F;state.walkAnimationSpeed=.3F;
            model.setupAnim(state);
            float original=model.head.yRot;
            System.setProperty(key,"high");
            GraphicsAuditWolfInputs.begin(7);
            GraphicsAuditWolfInputs.observeModel(model.root(),state,7);
            GraphicsAuditWolfInputs.observeTransform(state,new PoseStack().last(),7);
            var captured=JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject();
            assertTrue(captured.get("complete").getAsBoolean());
            assertEquals(7,captured.get("renderedFrameIndex").getAsLong());
            assertEquals(original,model.head.yRot);
            var geometry=captured.getAsJsonArray("geometrySources");
            assertEquals(1,geometry.size());
            assertEquals(66,geometry.get(0).getAsJsonArray().size());
            GraphicsAuditWolfInputs.observeModel(model.root(),state,7);
            assertEquals(1,JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject().getAsJsonArray("geometrySources").size());
            var parts=captured.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonObject("parts");
            assertTrue(parts.has("root/head/real_head"));assertTrue(parts.has("root/tail/real_tail"));
            model.head.yRot=99F;
            GraphicsAuditWolfInputs.observeModel(model.root(),state,7);
            assertEquals(2,JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject().getAsJsonArray("geometrySources").size());
            assertEquals(1,geometry.size());
            assertEquals(original,parts.getAsJsonObject("root/head").getAsJsonArray("pose").get(4).getAsFloat());
            GraphicsAuditWolfInputs.observeModel(model.root(),state,8);
            assertFalse(JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject().get("complete").getAsBoolean());
            GraphicsAuditWolfInputs.observeTransform(state,new PoseStack().last(),8);
            model.head.yRot=Float.NaN;
            GraphicsAuditWolfInputs.observeModel(model.root(),state,8);
            assertTrue(Float.isNaN(model.head.yRot));
            assertFalse(JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject().get("complete").getAsBoolean());
            model.head.yRot=original;GraphicsAuditWolfInputs.begin(9);
            GraphicsAuditWolfInputs.observeTransform(state,new PoseStack().last(),9);
            for(int i=0;i<17;i++)GraphicsAuditWolfInputs.observeModel(model.root(),state,9);
            captured=JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject();
            assertFalse(captured.get("complete").getAsBoolean());assertEquals(16,captured.getAsJsonArray("models").size());
            GraphicsAuditWolfInputs.begin(10);
            GraphicsAuditWolfInputs.observeTransform(state,new PoseStack().last(),10);
            for(int i=0;i<5;i++) {model.head.yRot=i;GraphicsAuditWolfInputs.observeModel(model.root(),state,10);}
            captured=JsonParser.parseString(GraphicsAuditWolfInputs.snapshot()).getAsJsonObject();
            assertEquals(4,captured.getAsJsonArray("geometrySources").size());
            assertFalse(captured.get("complete").getAsBoolean());
        } finally {if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
    }
}

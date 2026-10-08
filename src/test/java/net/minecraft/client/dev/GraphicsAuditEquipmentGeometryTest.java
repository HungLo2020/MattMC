package net.minecraft.client.dev;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditEquipmentGeometryTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void copiedSourcePoseCannotAliasLaterModelSubmissions() {
        var stack=new net.blaze3d.vertex.PoseStack();
        stack.translate(1.0,2.0,3.0);
        GraphicsAuditEquipmentGeometry.beginFrame(12);
        var copy=GraphicsAuditEquipmentGeometry.copiedPose(stack.last());
        stack.translate(4.0,5.0,6.0);
        assertEquals(1F,copy.getAsJsonArray("modelView").get(12).getAsFloat());
        assertEquals(9,copy.getAsJsonArray("normal").size());
        assertEquals(5F,GraphicsAuditEquipmentGeometry.copiedPose(stack.last()).getAsJsonArray("modelView").get(12).getAsFloat());
        stack.last().pose().m00(Float.NaN);
        assertEquals(0,GraphicsAuditEquipmentGeometry.copiedPose(stack.last()).size());
    }
    @Test void copiesVisibleGeometryAndPreservesSkipDrawChildren() {
        var mesh=new MeshDefinition();
        var parent=mesh.getRoot().addOrReplaceChild("parent",CubeListBuilder.create().texOffs(0,0).addBox(0,0,0,1,1,1),PartPose.offset(16,0,0));
        parent.addOrReplaceChild("child",CubeListBuilder.create().texOffs(0,0).addBox(0,0,0,1,1,1),PartPose.offset(16,0,0));
        var root=LayerDefinition.create(mesh,64,32).bakeRoot();var part=root.getChild("parent");
        GraphicsAuditEquipmentGeometry.beginFrame(12);
        assertEquals(12,GraphicsAuditEquipmentGeometry.geometry(root).size());
        part.skipDraw=true;
        var geometry=GraphicsAuditEquipmentGeometry.geometry(root);
        assertEquals(6,geometry.size());
        assertEquals("/parent/child",geometry.get(0).getAsJsonObject().get("part").getAsString());
        for(var quad:geometry) for(var vertex:quad.getAsJsonObject().getAsJsonArray("vertices")) {
            float x=vertex.getAsJsonArray().get(0).getAsFloat();assertTrue(x>=2F && x<=2.0625F);
        }
        assertEquals(16F,part.x);assertTrue(part.skipDraw);
        part.visible=false;assertEquals(0,GraphicsAuditEquipmentGeometry.geometry(root).size());
        assertFalse(part.visible);assertEquals(16F,part.getChild("child").x);
    }
    @Test void hiddenSubtreesStillCountTowardTraversalBounds() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        for(int i=0;i<65;i++)root.addOrReplaceChild("box"+i,CubeListBuilder.create(),PartPose.ZERO);
        var model=LayerDefinition.create(mesh,64,32).bakeRoot();model.visible=false;
        GraphicsAuditEquipmentGeometry.beginFrame(12);
        assertTrue(GraphicsAuditEquipmentGeometry.geometry(model).isEmpty());
        var validMesh=new MeshDefinition();validMesh.getRoot().addOrReplaceChild("box",CubeListBuilder.create().texOffs(0,0).addBox(0,0,0,1,1,1),PartPose.ZERO);
        assertTrue(GraphicsAuditEquipmentGeometry.geometry(LayerDefinition.create(validMesh,64,32).bakeRoot()).isEmpty(),"overflow remains invalid until the next frame");
    }
    @Test void completedDrawsRetainOrderAndCopiedViewWithinBounds() {
        String key="mattmc.dev.graphicsAuditEquipmentMaterial",timing="mattmc.dev.equipmentFoilTiming";
        String old=System.getProperty(key),oldTiming=System.getProperty(timing);
        try {
            System.setProperty(key,"leather-dyed");System.setProperty(timing,"true");
            var texture=ResourceLocation.withDefaultNamespace("textures/entity/equipment/humanoid/leather.png");
            var pipeline=RenderType.armorCutoutNoCull(texture).pipeline();
            GraphicsAuditEquipmentGeometry.beginFrame(12);
            var view=new org.joml.Matrix4f().translation(1F,2F,3F);
            GraphicsAuditEquipmentGeometry.observeCompletedDraw(texture,pipeline,48,72,view);
            view.identity();
            GraphicsAuditEquipmentGeometry.observeCompletedDraw(texture,pipeline,72,108,view);
            var rows=GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("completedDraws");
            assertEquals(72,rows.get(0).getAsJsonObject().get("indices").getAsInt());
            assertEquals(108,rows.get(1).getAsJsonObject().get("indices").getAsInt());
            assertEquals(1F,rows.get(0).getAsJsonObject().getAsJsonArray("view").get(12).getAsFloat());
            for(int i=0;i<64;i++)GraphicsAuditEquipmentGeometry.observeCompletedDraw(texture,pipeline,48,72,view);
            assertEquals(64,GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("completedDraws").size());
            assertFalse(GraphicsAuditEquipmentGeometry.snapshot().get("complete").getAsBoolean());
            GraphicsAuditEquipmentGeometry.beginFrame(13);
            assertTrue(GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("completedDraws").isEmpty());
        } finally {
            if(old==null)System.clearProperty(key);else System.setProperty(key,old);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }
    @Test void capturesTintAndDeclarationWithBoundedIndependentSnapshots() {
        String key="mattmc.dev.graphicsAuditEquipmentMaterial",timing="mattmc.dev.equipmentFoilTiming";
        String old=System.getProperty(key),oldTiming=System.getProperty(timing);
        try {
            System.setProperty(key,"leather-dyed");System.setProperty(timing,"true");
            var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("box",CubeListBuilder.create().texOffs(0,0).addBox(0,0,0,1,1,1),PartPose.ZERO);
            var root=LayerDefinition.create(mesh,64,32).bakeRoot();
            var model=new Model.Simple(root,RenderType::armorCutoutNoCull);
            var texture=ResourceLocation.withDefaultNamespace("textures/entity/equipment/humanoid/leather.png");
            var type=RenderType.armorCutoutNoCull(texture);
            GraphicsAuditEquipmentGeometry.beginFrame(12);
            GraphicsAuditEquipmentGeometry.observe(model,new ZombieRenderState(),type,0xff3366cc,15728640,655360);
            var receipt=GraphicsAuditEquipmentGeometry.snapshot();assertTrue(receipt.get("complete").getAsBoolean());
            var row=receipt.getAsJsonArray("models").get(0).getAsJsonObject();
            assertEquals(texture.toString(),row.get("texture").getAsString());assertEquals(0xff3366cc,row.get("tint").getAsInt());
            root.getChild("box").visible=false;
            assertEquals(6,row.getAsJsonArray("quads").size());
            receipt.getAsJsonArray("models").remove(0);assertEquals(1,GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("models").size());
            GraphicsAuditEquipmentGeometry.beginFrame(12);
            GraphicsAuditEquipmentGeometry.observeEntityPip(model,new ZombieRenderState(),type,-1,15728880,655360,
                new net.blaze3d.vertex.PoseStack().last(),1);
            assertEquals(1,GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("models").get(0)
                .getAsJsonObject().get("entityPipGuardPixels").getAsInt());
            for(int i=0;i<64;i++)GraphicsAuditEquipmentGeometry.observe(model,new ZombieRenderState(),type,-1,0,0);
            assertFalse(GraphicsAuditEquipmentGeometry.snapshot().get("complete").getAsBoolean());
            assertEquals(64,GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("models").size());
            GraphicsAuditEquipmentGeometry.beginFrame(13);assertEquals(0,GraphicsAuditEquipmentGeometry.snapshot().getAsJsonArray("models").size());
        } finally {
            if(old==null)System.clearProperty(key);else System.setProperty(key,old);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }
}

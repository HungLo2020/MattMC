package net.vulkanic.world;

import java.util.List;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldDecalFoilProducerTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void normalProducerRequiresRustOwnershipAndIgnoresLegacyFlag() {
        String key="mattmc.dev.rustGalWorldDecalFoil", old=System.getProperty(key);
        try (var mode=mockStatic(RustGalVulkanWholeFrameMode.class)) {
            System.setProperty(key,"true");
            mode.when(RustGalVulkanWholeFrameMode::enabled).thenReturn(false);
            assertFalse(RustGalWorldPrimitiveRenderer.worldDecalFoilEnabled());
            mode.when(RustGalVulkanWholeFrameMode::enabled).thenReturn(true);
            assertTrue(RustGalWorldPrimitiveRenderer.worldDecalFoilEnabled());
            assertTrue(RustGalWorldPrimitiveRenderer.worldDecalFoilAdmissionReceipt().contains("\"privateFlagsPresent\":true"));
            System.setProperty(key,"false");
            assertTrue(RustGalWorldPrimitiveRenderer.worldDecalFoilEnabled());
            System.clearProperty(key);
            assertTrue(RustGalWorldPrimitiveRenderer.worldDecalFoilEnabled());
            assertEquals("{\"schema\":\"rust-owned-world-decal-foil-v1\",\"normalRoute\":true,\"privateFlagsPresent\":false}",
                RustGalWorldPrimitiveRenderer.worldDecalFoilAdmissionReceipt());
            assertEquals("empty-quads",RustGalWorldPrimitiveRenderer.itemEntityMeshIneligibility(
                net.minecraft.world.item.ItemDisplayContext.GROUND,15728640,0,0,new int[0],null,null,
                ItemStackRenderState.FoilType.SPECIAL));
            mode.when(RustGalVulkanWholeFrameMode::enabled).thenReturn(false);
            assertFalse(RustGalWorldPrimitiveRenderer.worldDecalFoilEnabled());
        } finally {if(old==null) System.clearProperty(key);else System.setProperty(key,old);}
    }
    @Test void firstPersonComposedSemanticsMatchActualVanillaPoseForBothHands() throws Exception {
        var copy=RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("copiedFirstPersonDecalFoil",
            PoseStack.Pose.class,ItemStackRenderState.SemanticLayer.class,Matrix4f.class);
        copy.setAccessible(true);
        for(boolean left:new boolean[]{false,true}) for(boolean nonuniform:new boolean[]{false,true}) {
            var outer=new PoseStack.Pose();
            outer.translate(.2F,-.3F,.4F);
            outer.rotate(new Quaternionf().rotationXYZ(.3F,-.2F,.5F));
            if(nonuniform) outer.scale(2,3,-4);
            var item=new ItemTransform(new Vector3f(23,-37,61),new Vector3f(.1F,.2F,-.4F),
                nonuniform ? new Vector3f(-2,3,4) : new Vector3f(2,2,2));
            var local=new PoseStack.Pose();item.apply(left,local);
            var layer=new ItemStackRenderState.SemanticLayer(List.of(),new int[0],null,
                ItemStackRenderState.FoilType.SPECIAL,false,false,false,
                local.pose().get(new float[16]),local.normal().get(new float[9]),local.trustedNormals);
            var expected=outer.copy();item.apply(left,expected);
            var model=new Matrix4f(outer.pose()).mul(new Matrix4f().set(layer.modelTransform()));
            var result=(VulkanicGalBridge.WorldDecalFoilRecord)copy.invoke(null,outer,layer,model);
            assertTrue(result.firstPerson());
            assertEquals(expected.trustedNormals,result.trustedNormals());
            assertArrayEquals(expected.pose().get(new float[16]),result.modelPose(),0.00001F);
            assertArrayEquals(expected.normal().get(new float[9]),result.normalPose(),0.00001F);
            outer.pose().zero();outer.normal().zero();
            assertArrayEquals(model.get(new float[16]),result.modelPose());
        }
    }
}

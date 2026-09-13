package net.minecraft.client.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;
import net.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class GraphicsAuditEquipmentTrimSourcesTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    private static final class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents){super(ResourceLocation.withDefaultNamespace("textures/atlas/armor_trims.png"),contents,256,128,32,16);}
    }
    @Test void observesActualPixelsWithoutMutationAndRejectsIncompleteOrExcessSources(){
        String timing="mattmc.dev.equipmentFoilTiming",trim="mattmc.dev.graphicsAuditEquipmentTrim";
        String oldTiming=System.getProperty(timing),oldTrim=System.getProperty(trim);
        System.setProperty(timing,"true");System.setProperty(trim,"gold-spire-decal");
        try(var image=new NativeImage(2,1,false)){
            image.setPixel(0,0,0xff123456);image.setPixel(1,0,0xffabcdef);
            var contents=new SpriteContents(ResourceLocation.withDefaultNamespace("trims/entity/humanoid/spire_gold"),new FrameSize(2,1),image);
            var sprite=new Sprite(contents);var type=Sheets.armorTrimsSheet(true);
            GraphicsAuditEquipmentTrimSources.beginFrame(7);
            assertFalse(GraphicsAuditEquipmentTrimSources.snapshot().get("complete").getAsBoolean());
            for(int i=0;i<4;i++)GraphicsAuditEquipmentTrimSources.observe(sprite,type,"HUMANOID","minecraft:diamond");
            var snapshot=GraphicsAuditEquipmentTrimSources.snapshot();
            assertTrue(snapshot.get("complete").getAsBoolean());
            var row=snapshot.getAsJsonArray("sources").get(0).getAsJsonObject();
            assertEquals(32,row.get("x").getAsInt());assertEquals(.125F,row.get("u0").getAsFloat());
            assertEquals("EQUAL_DEPTH_TEST",row.get("depthTest").getAsString());
            assertTrue(row.get("depthWrite").getAsBoolean());
            assertEquals(0xff123456,image.getPixel(0,0));
            image.setPixel(0,0,0xff000000);
            GraphicsAuditEquipmentTrimSources.beginFrame(8);
            GraphicsAuditEquipmentTrimSources.observe(sprite,type,"HUMANOID","minecraft:diamond");
            assertNotEquals(row.get("rgbaSha256"),GraphicsAuditEquipmentTrimSources.snapshot()
                .getAsJsonArray("sources").get(0).getAsJsonObject().get("rgbaSha256"));
            assertEquals(4,snapshot.getAsJsonArray("sources").size());
            for(int i=0;i<20;i++)GraphicsAuditEquipmentTrimSources.observe(sprite,type,"HUMANOID","minecraft:diamond");
            assertFalse(GraphicsAuditEquipmentTrimSources.snapshot().get("complete").getAsBoolean());
            assertEquals(16,GraphicsAuditEquipmentTrimSources.snapshot().getAsJsonArray("sources").size());
        }finally{
            GraphicsAuditEquipmentTrimSources.beginFrame(0);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
            if(oldTrim==null)System.clearProperty(trim);else System.setProperty(trim,oldTrim);
        }
    }

    @Test void inventoryTrimModeEnablesTheSameBoundedSourceObservation(){
        String timing="mattmc.dev.equipmentFoilTiming",inventory="mattmc.dev.graphicsAuditInventoryEquipment";
        String oldTiming=System.getProperty(timing),oldInventory=System.getProperty(inventory);
        System.setProperty(timing,"true");System.setProperty(inventory,"trim");
		try{
			GraphicsAuditEquipmentTrimSources.beginFrame(9);
			assertTrue(GraphicsAuditEquipmentTrimSources.snapshot().get("enabled").getAsBoolean());
			assertTrue(com.google.gson.JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot())
				.getAsJsonObject().has("trimSources"));
        }finally{
            GraphicsAuditEquipmentTrimSources.beginFrame(0);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
            if(oldInventory==null)System.clearProperty(inventory);else System.setProperty(inventory,oldInventory);
        }
    }
}

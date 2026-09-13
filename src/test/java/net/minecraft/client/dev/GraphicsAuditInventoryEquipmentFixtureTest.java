package net.minecraft.client.dev;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditInventoryEquipmentFixtureTest {
	private static net.minecraft.core.HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
		registries = new net.minecraft.core.RegistrySetBuilder()
			.add(net.minecraft.core.registries.Registries.TRIM_PATTERN, net.minecraft.world.item.equipment.trim.TrimPatterns::bootstrap)
			.add(net.minecraft.core.registries.Registries.TRIM_MATERIAL, net.minecraft.world.item.equipment.trim.TrimMaterials::bootstrap)
			.build(net.minecraft.core.RegistryAccess.EMPTY);
    }

    @Test
    void previewReadinessWaitsForStableViewsOnDistinctFrames() {
        String flag="mattmc.dev.graphicsAuditInventoryEquipment",timing="mattmc.dev.equipmentFoilTiming";
        String old=System.getProperty(flag),oldTiming=System.getProperty(timing);
        try {
            System.setProperty(flag,"base");System.setProperty(timing,"true");
            GraphicsAuditInventoryPreviewInputs.resetReadiness();
            var state=new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            var translation=new org.joml.Vector3f();var rotation=new org.joml.Quaternionf();
            for(int i=1;i<=3;i++) {
                GraphicsAuditInventoryPreviewInputs.beginFrame(i);
                state.bodyRot=i==1?207:165; state.ageInTicks=i;
                GraphicsAuditInventoryPreviewInputs.observeScreenMouse("background",i==1?0:213,i==1?0:120,213,120);
                GraphicsAuditInventoryPreviewInputs.observeScreenMouse("render-return",213,120,213,120);
                GraphicsAuditInventoryPreviewInputs.observe(state,30,translation,rotation,null,1,2,50,72);
                assertEquals(i==3,GraphicsAuditInventoryPreviewInputs.ready());
                if(i==1){
                    GraphicsAuditInventoryPreviewInputs.observe(state,30,translation,rotation,null,1,2,50,72);
                    assertFalse(GraphicsAuditInventoryPreviewInputs.ready(),"same-frame repetition cannot settle a view");
                }
            }
            GraphicsAuditInventoryPreviewInputs.beginFrame(4);
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("background",0,0,213,120);
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("render-return",213,120,213,120);
            GraphicsAuditInventoryPreviewInputs.observe(state,30,translation,rotation,null,1,2,50,72);
            assertFalse(GraphicsAuditInventoryPreviewInputs.ready(),"stable pose cannot hide stale screen mouse input after reload");
            GraphicsAuditInventoryPreviewInputs.resetReadiness();
            assertFalse(GraphicsAuditInventoryPreviewInputs.ready(),"a new screen must settle independently");
        } finally {
            GraphicsAuditInventoryPreviewInputs.resetReadiness();GraphicsAuditInventoryPreviewInputs.beginFrame(0);
            if(old==null)System.clearProperty(flag);else System.setProperty(flag,old);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void previewObservationsCopyInputsAndRejectStaleOrInvalidEvidence() {
        String flag="mattmc.dev.graphicsAuditInventoryEquipment",timing="mattmc.dev.equipmentFoilTiming";
        String old=System.getProperty(flag),oldTiming=System.getProperty(timing);
        try {
            System.setProperty(flag,"base");System.setProperty(timing,"true");
            GraphicsAuditInventoryPreviewInputs.beginFrame(12);
            var state=new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            state.bodyRot=207F;
            var translation=new org.joml.Vector3f(1,2,3);
            var rotation=new org.joml.Quaternionf().rotateX(.4F);
            GraphicsAuditInventoryPreviewInputs.observe(state,30,translation,rotation,null,1,2,50,72);
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("background",0,0,213,120);
            GraphicsAuditInventoryPreviewInputs.observeScreenMouse("render-return",213,120,213,120);
            var saved=GraphicsAuditInventoryPreviewInputs.snapshot();
            assertTrue(saved.get("complete").getAsBoolean());
            assertEquals(2,saved.getAsJsonArray("screenMouse").size());
            assertEquals(213F,saved.getAsJsonArray("screenMouse").get(0).getAsJsonObject()
                .getAsJsonArray("supplied").get(0).getAsFloat());
            assertEquals(12,saved.get("renderedFrameIndex").getAsLong());
            state.bodyRot=99;translation.x=99;rotation.identity();
            var row=saved.getAsJsonArray("observations").get(0).getAsJsonObject();
            assertEquals(207F,row.getAsJsonArray("angles").get(0).getAsFloat());
            assertEquals(1F,row.getAsJsonArray("translation").get(0).getAsFloat());
            saved.getAsJsonArray("observations").asList().clear();
            assertEquals(1,GraphicsAuditInventoryPreviewInputs.snapshot().getAsJsonArray("observations").size());
            GraphicsAuditInventoryPreviewInputs.beginFrame(13);
            assertEquals(0,GraphicsAuditInventoryPreviewInputs.snapshot().getAsJsonArray("screenMouse").size());
            assertFalse(GraphicsAuditInventoryPreviewInputs.snapshot().get("complete").getAsBoolean());
            translation.x=Float.NaN;
            GraphicsAuditInventoryPreviewInputs.observe(state,30,translation,rotation,null,1,2,50,72);
            assertFalse(GraphicsAuditInventoryPreviewInputs.snapshot().get("complete").getAsBoolean());
            GraphicsAuditInventoryPreviewInputs.beginFrame(14);translation.x=1;
            for(int i=0;i<5;i++)GraphicsAuditInventoryPreviewInputs.observe(state,30,translation,rotation,null,1,2,50,72);
            assertEquals(4,GraphicsAuditInventoryPreviewInputs.snapshot().getAsJsonArray("observations").size());
            assertFalse(GraphicsAuditInventoryPreviewInputs.snapshot().get("complete").getAsBoolean());
        } finally {
            GraphicsAuditInventoryPreviewInputs.beginFrame(0);
            if(old==null)System.clearProperty(flag);else System.setProperty(flag,old);
            if(oldTiming==null)System.clearProperty(timing);else System.setProperty(timing,oldTiming);
        }
    }

    @Test
    void ordinaryLeatherSlotsRequireExactDyeAndNoGlintOverride() {
        var items = new net.minecraft.world.item.Item[] {
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
        for (int i=0; i<4; i++) {
            ItemStack stack = GraphicsAuditInventoryEquipmentFixture.stack(i);
            assertTrue(stack.is(items[i]));
            assertEquals(0x3366CC, stack.get(DataComponents.DYED_COLOR).rgb());
            assertTrue(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,i,false));
            assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,(i+1)%4,false));
            assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,i,true));
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,false);
            assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,i,false));
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            stack.set(DataComponents.DYED_COLOR,new DyedItemColor(0x3366CD));
            assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,i,false));
            stack.remove(DataComponents.DYED_COLOR);
            assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,i,false));
            stack = GraphicsAuditInventoryEquipmentFixture.stack(i);
            stack.setDamageValue(1);
            assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,i,false));
        }
    }

    @Test
    void unknownModesCannotSilentlySelectAControl() {
        String key="mattmc.dev.graphicsAuditInventoryEquipment", previous=System.getProperty(key);
        try {
            System.clearProperty(key);
			assertEquals("",GraphicsAuditInventoryEquipmentFixture.mode());
				for (String mode : new String[] {"empty","base","foil","foil-moving","trim","trim-decal"}) {
				System.setProperty(key,mode);assertEquals(mode,GraphicsAuditInventoryEquipmentFixture.mode());
			}
            for (String mode : new String[] {"true","leather","BASE"}) {
                System.setProperty(key,mode);
                assertThrows(IllegalArgumentException.class,GraphicsAuditInventoryEquipmentFixture::mode);
            }
        } finally {
            if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);
        }
    }

	@Test
	void registeredAndInlineDecalTrimModesRemainDistinct() {
		var gold=registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL)
			.getOrThrow(net.minecraft.world.item.equipment.trim.TrimMaterials.GOLD);
		var spire=registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN)
			.getOrThrow(net.minecraft.world.item.equipment.trim.TrimPatterns.SPIRE);
		var stack=GraphicsAuditInventoryEquipmentFixture.stack(0);
		stack.set(DataComponents.TRIM,new net.minecraft.world.item.equipment.trim.ArmorTrim(gold,spire));
		assertTrue(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,0,"trim"));
		assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,0,"trim-decal"));
		stack.set(DataComponents.TRIM,new net.minecraft.world.item.equipment.trim.ArmorTrim(gold,
			GraphicsAuditEquipmentFixture.fixturePattern(spire,true)));
		assertTrue(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,0,"trim-decal"));
		assertFalse(GraphicsAuditInventoryEquipmentFixture.matchesStack(stack,0,"trim"));
	}

	@Test
	void movingFoilRemainsAnEnchantedFixtureMode() {
		String key="mattmc.dev.graphicsAuditInventoryEquipment", previous=System.getProperty(key);
		try {
			System.setProperty(key,"foil-moving");
			assertTrue(GraphicsAuditInventoryEquipmentFixture.foilRequested());
			assertTrue(GraphicsAuditInventoryEquipmentFixture.movingFoilRequested());
			assertFalse(GraphicsAuditInventoryEquipmentFixture.trimRequested());
		} finally {
			if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);
		}
	}
}

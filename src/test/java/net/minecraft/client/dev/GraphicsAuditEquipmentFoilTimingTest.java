package net.minecraft.client.dev;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditEquipmentFoilTimingTest {
    @Test void steppedReadinessRejectsObservedInterpolationAndMixedLayerAges(){run(()->{
        var part=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        assertFalse(GraphicsAuditEquipmentFoilTiming.observedSteppedAgeReady(3));
        GraphicsAuditEquipmentFoilTiming.observeZombiePose(30.86F,0F,false,part,part);
        assertFalse(GraphicsAuditEquipmentFoilTiming.observedSteppedAgeReady(3));
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        GraphicsAuditEquipmentFoilTiming.observeZombiePose(31F,0F,false,part,part);
        assertTrue(GraphicsAuditEquipmentFoilTiming.observedSteppedAgeReady(3));
        assertFalse(GraphicsAuditEquipmentFoilTiming.observedSteppedAgeReady(2));
        GraphicsAuditEquipmentFoilTiming.observeZombiePose(30.86F,0F,false,part,part);
        assertFalse(GraphicsAuditEquipmentFoilTiming.observedSteppedAgeReady(3));
        assertEquals(30.86F,snapshot().getAsJsonArray("zombiePoses").get(1)
            .getAsJsonObject().get("ageInTicks").getAsFloat());
    });}
    @Test void inventoryGeometryIsSavedWithoutWorldEquipmentMaterial(){run(()->{
        String inventory="mattmc.dev.graphicsAuditInventoryEquipment",material="mattmc.dev.graphicsAuditEquipmentMaterial";
        String oldInventory=System.getProperty(inventory),oldMaterial=System.getProperty(material);
        try {
            System.clearProperty(material);
            System.setProperty(inventory,"base");
            var saved=snapshot().getAsJsonObject("modelGeometry");
            assertNotNull(saved);
            assertEquals("equipment-model-geometry-v1",saved.get("schema").getAsString());
            assertTrue(saved.get("enabled").getAsBoolean());
            assertEquals(7,saved.get("renderedFrameIndex").getAsInt());
            assertFalse(saved.get("complete").getAsBoolean(),"empty source cannot prove geometry");
            System.clearProperty(inventory);
            assertFalse(snapshot().has("modelGeometry"));
        } finally {
            if(oldInventory==null)System.clearProperty(inventory);else System.setProperty(inventory,oldInventory);
            if(oldMaterial==null)System.clearProperty(material);else System.setProperty(material,oldMaterial);
        }
    });}
    private static JsonObject snapshot() {return JsonParser.parseString(GraphicsAuditEquipmentFoilTiming.snapshot()).getAsJsonObject();}
    private static void run(Runnable action) {
        String key="mattmc.dev.equipmentFoilTiming",old=System.getProperty(key);
        System.setProperty(key,"true");GraphicsAuditEquipmentFoilTiming.beginFrame(7);
        try {action.run();} finally {GraphicsAuditEquipmentFoilTiming.beginFrame(0);
            if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
    }
    @Test void nativeRequiresAllFourActualInputsAndResetsEachFrame() {run(()->{
        for(int i=1;i<=3;i++)GraphicsAuditEquipmentFoilTiming.observeSemanticClock(i,12345,.5,.5F);
        assertFalse(snapshot().get("complete").getAsBoolean());
        GraphicsAuditEquipmentFoilTiming.observeSemanticClock(4,12345,.5,.5F);
        assertTrue(snapshot().get("complete").getAsBoolean());
        assertEquals(49380,snapshot().getAsJsonArray("samples").get(0).getAsJsonObject().get("scaledTicks").getAsLong());
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        assertFalse(snapshot().get("complete").getAsBoolean());
        assertEquals(0,snapshot().getAsJsonArray("samples").size());
    });}
	@Test void guiPreviewRequiresFourAcceptedOrthographicClocks() {run(()->{
		for(int i=0;i<3;i++)GraphicsAuditEquipmentFoilTiming.observeGuiSemanticClock(i,12345,.5,.5F);
		assertFalse(snapshot().get("complete").getAsBoolean());
		GraphicsAuditEquipmentFoilTiming.observeGuiSemanticClock(3,12345,.5,.5F);
		var saved=snapshot();
		assertTrue(saved.get("complete").getAsBoolean());
		assertEquals("semantic-armor-orthographic",saved.getAsJsonArray("samples").get(0)
			.getAsJsonObject().get("provider").getAsString());
		assertEquals(0,saved.getAsJsonArray("samples").get(0).getAsJsonObject().get("batchSequence").getAsInt());
	});}
    @Test void frozenIgnoresOtherGlintFamiliesAndMixedProvidersReject() {run(()->{
        GraphicsAuditEquipmentFoilTiming.observeScaledTicks(42,8F);
        assertEquals(0,snapshot().getAsJsonArray("samples").size());
        GraphicsAuditEquipmentFoilTiming.observeScaledTicks(43,.16F);
        assertTrue(snapshot().get("complete").getAsBoolean());
        GraphicsAuditEquipmentFoilTiming.observeSemanticClock(1,1,.5,.5F);
        assertFalse(snapshot().get("complete").getAsBoolean());
    });}
    @Test void invalidAndUnboundedEvidenceCannotBecomeComplete() {run(()->{
        for(int i=0;i<17;i++)GraphicsAuditEquipmentFoilTiming.observeScaledTicks(i,.16F);
        assertEquals(16,snapshot().getAsJsonArray("samples").size());
        assertFalse(snapshot().get("complete").getAsBoolean());
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        GraphicsAuditEquipmentFoilTiming.observeSemanticClock(1,10,Double.NaN,.5F);
        for(int i=1;i<=4;i++)GraphicsAuditEquipmentFoilTiming.observeSemanticClock(i,10,.5,.5F);
        assertFalse(snapshot().get("complete").getAsBoolean());
    });}
    private static void moving(Runnable action){
        String key="mattmc.dev.equipmentFoilPhase", centers="mattmc.dev.equipmentFoilPhaseCenters";
        String old=System.getProperty(key),oldCenters=System.getProperty(centers);
        System.setProperty(key,"10000");System.clearProperty(centers);
        try{run(action);}finally{System.clearProperty(key);System.clearProperty(centers);
            GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0);
            if(old!=null)System.setProperty(key,old);if(oldCenters!=null)System.setProperty(centers,oldCenters);}
    }
    @Test void naturalSchedulingChecksObservedPhaseAndFiniteDeadline(){moving(()->{
        GraphicsAuditEquipmentFoilTiming.observeScaledTicks(9999,.16F);
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0));
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        GraphicsAuditEquipmentFoilTiming.observeScaledTicks(340010,.16F);
        assertTrue(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,1));
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(1,2));
        assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.readyForCapture(1,200_000_000_002L));
        assertEquals(26000,GraphicsAuditEquipmentFoilTiming.target(10000,4));
        assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.target(10000,5));
    });}
    @Test void pairedCentersCannotAuthorizeAnotherPhase(){moving(()->{
        System.setProperty("mattmc.dev.equipmentFoilPhaseCenters","10020,14020,18020,22020,26020");
        GraphicsAuditEquipmentFoilTiming.observeScaledTicks(10050,.16F);
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0));
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);GraphicsAuditEquipmentFoilTiming.observeScaledTicks(10024,.16F);
        assertTrue(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,1));
        System.setProperty("mattmc.dev.equipmentFoilPhaseCenters","9999,14020,18020,22020,26020");
        assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.readyForCapture(0,2));
    });}
	@Test void movingInventoryAcceptsOneObservedCenterInsteadOfFiveWorldPoses(){
		String inventory="mattmc.dev.graphicsAuditInventoryEquipment";
		String old=System.getProperty(inventory);
		try {
			System.setProperty(inventory,"foil-moving");
			moving(()->{
				System.setProperty("mattmc.dev.equipmentFoilPhaseCenters","10020");
				GraphicsAuditEquipmentFoilTiming.observeScaledTicks(10024,.16F);
				assertTrue(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0));
				System.setProperty("mattmc.dev.equipmentFoilPhaseCenters","10020,14020,18020,22020,26020");
				assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.readyForCapture(0,1));
			});
		} finally {
			if(old==null)System.clearProperty(inventory);else System.setProperty(inventory,old);
		}
	}
    @Test void phaseReadinessRequiresAllEquipmentClocksToAgree(){moving(()->{
        for(int i=1;i<=4;i++)GraphicsAuditEquipmentFoilTiming.observeSemanticClock(i,2500+i*10,.5,.5F);
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0));
        assertTrue(GraphicsAuditEquipmentFoilTiming.phaseMatches(340512,10000,512));
        assertFalse(GraphicsAuditEquipmentFoilTiming.phaseMatches(340513,10000,512));
    });}
    @Test void waitEvidenceRetainsBoundedCandidatesAndResetsPerPose(){moving(()->{
        System.setProperty("mattmc.dev.equipmentFoilPhaseCenters","10020,14020,18020,22020,26020");
        for(int i=0;i<40;i++){
            GraphicsAuditEquipmentFoilTiming.beginFrame(7+i);
            GraphicsAuditEquipmentFoilTiming.observeScaledTicks(10050+i,.16F);
            assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,i));
        }
        var wait=snapshot().getAsJsonObject("phaseWait");
        assertEquals(32,wait.getAsJsonArray("candidates").size());
        assertEquals(40,wait.get("centerMisses").getAsLong());
        assertEquals(10050,wait.getAsJsonArray("candidates").get(0).getAsJsonObject()
            .getAsJsonArray("samples").get(0).getAsJsonObject().get("scaledTicks").getAsLong());
        GraphicsAuditEquipmentFoilTiming.beginFrame(50);
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,50));
        assertEquals(1,snapshot().getAsJsonObject("phaseWait").get("incompleteObservations").getAsLong());
        var original=System.out;
        var bytes=new java.io.ByteArrayOutputStream();
        try{
            System.setOut(new java.io.PrintStream(bytes));
            assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.readyForCapture(0,200_000_000_000L));
        }finally{System.setOut(original);}
        assertTrue(bytes.toString().contains("equipment-phase.timeout"));
        assertTrue(bytes.toString().contains("\"centerMisses\":40"));
        assertTrue(bytes.toString().contains("\"scaledTicks\":10050"));
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(1,200_000_000_001L));
        assertEquals(0,snapshot().getAsJsonObject("phaseWait").getAsJsonArray("candidates").size());
    });}
    @Test void explicitPoseSpacingChangesTargetsWithoutChangingPhaseTolerance(){moving(()->{
        String key="mattmc.dev.equipmentFoilPoseStep",previous=System.getProperty(key);
        try{
            System.setProperty(key,"8000");
            assertEquals(18000,GraphicsAuditEquipmentFoilTiming.target(10000,1));
            assertEquals(42000,GraphicsAuditEquipmentFoilTiming.target(10000,4));
            GraphicsAuditEquipmentFoilTiming.observeScaledTicks(14000,.16F);
            assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(1,0));
            GraphicsAuditEquipmentFoilTiming.beginFrame(8);
            GraphicsAuditEquipmentFoilTiming.observeScaledTicks(18000,.16F);
            assertTrue(GraphicsAuditEquipmentFoilTiming.readyForCapture(1,1));
            assertEquals(8000,snapshot().get("requestedPoseStep").getAsInt());
            System.setProperty(key,"7999");
            assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.target(10000,1));
        }finally{if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
    });}
    @Test void rangeSelectionRequiresProximityToEveryObservedCurrentClock(){moving(()->{
        String key="mattmc.dev.equipmentFoilPhaseRanges",previous=System.getProperty(key);
        try{
            System.setProperty(key,"10000:10016,14000:14016,18000:18016,22000:22016,26000:26016");
            GraphicsAuditEquipmentFoilTiming.observeScaledTicks(10020,.16F);
            assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0));
            GraphicsAuditEquipmentFoilTiming.beginFrame(8);
            GraphicsAuditEquipmentFoilTiming.observeScaledTicks(10016,.16F);
            assertTrue(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,1));
            System.setProperty(key,"10000:10017,14000:14016,18000:18016,22000:22016,26000:26016");
            assertThrows(IllegalStateException.class,()->GraphicsAuditEquipmentFoilTiming.readyForCapture(0,2));
        }finally{if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
    });}
    @Test void nativeSelectionKeepsMatchingIntervalInsideAuthoredWindow(){moving(()->{
        for(int i=1;i<=4;i++)GraphicsAuditEquipmentFoilTiming.observeSemanticClock(i,2500,.5,.5F);
        assertFalse(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,0));
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        for(int i=1;i<=4;i++)GraphicsAuditEquipmentFoilTiming.observeSemanticClock(i,2505,.5,.5F);
        assertTrue(GraphicsAuditEquipmentFoilTiming.readyForCapture(0,1));
    });}
    @Test void completeModelPoseCopiesEveryPart(){run(()->{
        var root=net.minecraft.client.model.geom.builders.LayerDefinition.create(
            net.minecraft.client.model.HumanoidModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,0F),64,64).bakeRoot();
        var model=new net.minecraft.client.model.HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState>(root);
        model.head.xRot=.25F;model.leftLeg.zRot=.125F;
        GraphicsAuditEquipmentFoilTiming.observeZombieModelPose(1,0,false,model);
        model.head.xRot=.75F;
        var parts=snapshot().getAsJsonArray("zombiePoses").get(0).getAsJsonObject().getAsJsonObject("remainingParts");
        assertEquals(5,parts.size());assertEquals(.25F,parts.getAsJsonArray("head").get(3).getAsFloat());
        assertEquals(.125F,parts.getAsJsonArray("leftLeg").get(5).getAsFloat());
    });}
    @Test void entityTransformObservationsCopyAndBoundInputs(){run(()->{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var state=new net.minecraft.client.renderer.entity.state.ZombieRenderState();state.ageInTicks=1;state.bodyRot=12;
        var pose=new net.blaze3d.vertex.PoseStack();pose.translate(1,2,3);
        GraphicsAuditEquipmentFoilTiming.observeEntityTransform(state,pose.last());
        state.bodyRot=99;pose.translate(10,20,30);
        var captured=snapshot();assertTrue(captured.get("entityTransformsComplete").getAsBoolean());
        var first=captured.getAsJsonArray("entityTransforms").get(0).getAsJsonObject();
        assertEquals(12F,first.getAsJsonArray("state").get(1).getAsFloat());
        assertEquals(1F,first.getAsJsonArray("modelView").get(12).getAsFloat());
        for(int i=0;i<16;i++)GraphicsAuditEquipmentFoilTiming.observeEntityTransform(state,pose.last());
        assertEquals(16,snapshot().getAsJsonArray("entityTransforms").size());
        assertFalse(snapshot().get("entityTransformsComplete").getAsBoolean());
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        assertEquals(0,snapshot().getAsJsonArray("entityTransforms").size());
    });}
    @Test void poseObservationCopiesWithoutMutatingAndResetsEachFrame(){run(()->{
        var left=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var right=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        left.xRot=.25F;right.zRot=-.125F;
        GraphicsAuditEquipmentFoilTiming.observeZombiePose(42.5F,0F,false,left,right);
        var snap=snapshot();assertTrue(snap.get("zombiePosesComplete").getAsBoolean());
        var pose=snap.getAsJsonArray("zombiePoses").get(0).getAsJsonObject();
        assertEquals(.25F,pose.getAsJsonArray("leftArm").get(3).getAsFloat());
        assertEquals(-.125F,pose.getAsJsonArray("rightArm").get(5).getAsFloat());
        left.xRot=.75F;assertEquals(.25F,pose.getAsJsonArray("leftArm").get(3).getAsFloat());
        assertEquals(-.125F,right.zRot);
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);
        assertEquals(0,snapshot().getAsJsonArray("zombiePoses").size());
        assertFalse(snapshot().get("zombiePosesComplete").getAsBoolean());
    });}
    @Test void poseObservationBoundsRecordsAndRejectsInvalidValues(){run(()->{
        var part=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        for(int i=0;i<17;i++)GraphicsAuditEquipmentFoilTiming.observeZombiePose(i,0F,false,part,part);
        assertEquals(16,snapshot().getAsJsonArray("zombiePoses").size());
        assertFalse(snapshot().get("zombiePosesComplete").getAsBoolean());
        GraphicsAuditEquipmentFoilTiming.beginFrame(8);part.xRot=Float.NaN;
        GraphicsAuditEquipmentFoilTiming.observeZombiePose(1,0F,false,part,part);
        assertFalse(snapshot().get("zombiePosesComplete").getAsBoolean());
    });}
}

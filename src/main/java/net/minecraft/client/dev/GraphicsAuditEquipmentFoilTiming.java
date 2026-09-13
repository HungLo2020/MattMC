package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Bounded observations of actual equipment glint inputs; never supplies a clock. */
public final class GraphicsAuditEquipmentFoilTiming {
    private static final JsonArray samples = new JsonArray();
    private static long frame;
    private static final JsonArray zombiePoses=new JsonArray();
    private static boolean poseValid;
    private static final JsonArray entityTransforms=new JsonArray();
    private static boolean entityTransformValid;
    private static long waitObservations, phaseCandidates, clockDisagreements, centerMisses;
    private static final JsonArray waitCandidates=new JsonArray();
    private static long incompleteObservations;
    private static boolean valid;
    private static int nativeCount, frozenCount;
    private static int waitingPose=-1;
    private static long waitStarted;
    private static long pacedFrame = -1, pacingLogFrame = -1, pacingBeforeTicks, pacingRequestedNanos, pacingElapsedNanos;
    private static long pacingTarget = -1;
    public static boolean movingRequested() {return System.getProperty("mattmc.dev.equipmentFoilPhase")!=null;}
    static int requestedPhase() {
        int value=Integer.parseInt(System.getProperty("mattmc.dev.equipmentFoilPhase","-1"));
        if(value!=10000 && value!=40000)throw new IllegalStateException("unsupported equipment phase");
        return value;
    }
    static int poseStep() {
        int value=Integer.parseInt(System.getProperty("mattmc.dev.equipmentFoilPoseStep","4000"));
        if(value!=4000 && value!=8000)throw new IllegalStateException("unsupported equipment pose step");
        return value;
    }
    static long target(int phase,int pose) {
        if((phase!=10000 && phase!=40000) || pose<0 || pose>=5)
            throw new IllegalStateException("equipment phase requires five authored poses");
        return phase+pose*(long)poseStep();
    }
    static boolean phaseMatches(long ticks,long target,long window) {
        return ticks>=0 && target>=0 && target<330000 && window>=0 && window<=512
            && Math.floorMod(ticks-target,330000L)<=window;
    }
    public static synchronized boolean readyForCapture(int pose) {
        boolean ready = readyForCapture(pose,System.nanoTime());
        if (pacedFrame == frame && pacingLogFrame != frame && pacingRequestedNanos > 0) {
            pacingLogFrame = frame;
            System.out.println("[MattMC graphics audit] equipment-phase.pace-before-clock pose="+pose
                +" frame="+frame+" beforeTicks="+pacingBeforeTicks
                +" requestedNanos="+pacingRequestedNanos+" elapsedNanos="+pacingElapsedNanos);
        }
        return ready;
    }
    /** Delay only the first armor clock read of an explicitly requested capture.
     * Samples still come from the ordinary Util clock after this method returns. */
    public static synchronized void beforeFirstSemanticClock(double speed) {
        if (!Boolean.getBoolean("mattmc.dev.equipmentFoilCapturePacing") || !enabled()
                || !movingRequested() || pacingTarget < 0 || waitingPose < 0
                || frame <= 0 || nativeCount != 0 || pacedFrame == frame || speed != 0.5) return;
        pacedFrame = frame;
        pacingBeforeTicks = (long)(net.minecraft.Util.getMillis() * speed * 8.0);
        // Leave room for the remaining actual reads without preceding the authored phase window.
        pacingRequestedNanos = GraphicsAuditEquipmentCapturePacing.delayNanos(pacingBeforeTicks, Math.max(pacingTarget - 4, target(requestedPhase(), waitingPose)));
        pacingElapsedNanos = 0;
        if (pacingRequestedNanos == 0) return;
        long started = System.nanoTime();
        while (!Thread.currentThread().isInterrupted()) {
            long remaining = pacingRequestedNanos - (System.nanoTime() - started);
            if (remaining <= 0) break;
            // Keep the render thread runnable during this bounded diagnostic
            // window. The following four ordinary reads still decide admission.
            Thread.onSpinWait();
        }
        pacingElapsedNanos = System.nanoTime() - started;
        // Logging waits until all four clock samples have been observed.
    }
    static synchronized boolean readyForCapture(int pose,long now) {
        pacingTarget = -1;
        if(!movingRequested()){waitingPose=-1;return true;}
        if(!enabled())throw new IllegalStateException("equipment phase requires clock observations");
        long target=target(requestedPhase(),pose);
        if(waitingPose!=pose){
            waitingPose=pose;waitStarted=now;waitObservations=phaseCandidates=clockDisagreements=centerMisses=incompleteObservations=0;
            waitCandidates.asList().clear();
            System.out.println("[MattMC graphics audit] equipment-phase.wait pose="+pose+" target="+target+" frame="+frame+" snapshot="+snapshot());
        }
        waitObservations++;
        if(now-waitStarted>=200_000_000_000L){
            System.out.println("[MattMC graphics audit] equipment-phase.timeout "+snapshot());
            throw new IllegalStateException("equipment natural phase wait timed out");
        }
        if(!complete()){incompleteObservations++;return false;}
        if(!GraphicsAuditEquipmentTickFixture.readyForPose(net.minecraft.client.Minecraft.getInstance(),pose)){
            incompleteObservations++;return false;
        }
        if(GraphicsAuditEquipmentTickFixture.enabled() && !observedSteppedAgeReady(pose)){
            incompleteObservations++;return false;
        }
        if(waitCandidates.size()<32){
            boolean candidate=false;
            for(var sample:samples)candidate|=phaseMatches(sample.getAsJsonObject().get("scaledTicks").getAsLong(),target,512);
            if(candidate){
                JsonObject observation=new JsonObject();observation.addProperty("frame",frame);
                observation.addProperty("elapsedNanos",now-waitStarted);
                observation.add("samples",samples.deepCopy());
                observation.addProperty("zombiePosesComplete",poseValid && !zombiePoses.isEmpty());
                if(!zombiePoses.isEmpty())observation.add("firstZombiePose",zombiePoses.get(0).deepCopy());
                waitCandidates.add(observation);
                System.out.println("[MattMC graphics audit] equipment-phase.candidate pose="+pose+" observation="+observation);
            }
        }
        String centers=System.getProperty("mattmc.dev.equipmentFoilPhaseCenters");
        Long center=null;
        if(centers!=null){
            String[] values=centers.split(",",-1);
			int count=GraphicsAuditInventoryEquipmentFixture.movingFoilRequested()?1:5;
			if(values.length!=count)throw new IllegalStateException("equipment phase centers require "+count+" observations");
			for(int i=0;i<count;i++){
                long value=Long.parseLong(values[i]);
                if(value<0 || value>=330000 || !phaseMatches(value,target(requestedPhase(),i),512))
                    throw new IllegalStateException("equipment phase center outside requested window");
                if(i==pose)center=value;
            }
        }
        String ranges=System.getProperty("mattmc.dev.equipmentFoilPhaseRanges");
        Long low=null,high=null;
        if(ranges!=null){
            String[] values=ranges.split(",",-1);
			int count=GraphicsAuditInventoryEquipmentFixture.movingFoilRequested()?1:5;
			if(values.length!=count)throw new IllegalStateException("equipment phase ranges require "+count+" observations");
			for(int i=0;i<count;i++){
                String[] ends=values[i].split(":",-1);
                if(ends.length!=2)throw new IllegalStateException("invalid equipment phase range");
                long a=Long.parseLong(ends[0]),b=Long.parseLong(ends[1]);
                if(a<0 || b<a || b>=330000 || b-a>16
                        || !phaseMatches(a,target(requestedPhase(),i),512)
                        || !phaseMatches(b,target(requestedPhase(),i),512))
                    throw new IllegalStateException("equipment phase range outside requested window");
                if(i==pose){low=a;high=b;}
            }
        }
        if (center != null) pacingTarget = center;
        // Leave the full +/-16 matching interval inside the authored window.
        if(nativeCount==4 && centers==null && ranges==null){
            for(var sample:samples)if(!phaseMatches(sample.getAsJsonObject().get("scaledTicks").getAsLong(),target+16,480))return false;
        }
        for(var sample:samples){
            long ticks=sample.getAsJsonObject().get("scaledTicks").getAsLong();
            if(!phaseMatches(ticks,target,512))return false;
            phaseCandidates++;
            if(center!=null && !near(ticks,center)){centerMisses++;return false;}
            if(low!=null && (!near(ticks,low) || !near(ticks,high))){centerMisses++;return false;}
            for(var other:samples)if(!near(ticks,other.getAsJsonObject().get("scaledTicks").getAsLong())){
                clockDisagreements++;return false;
            }
        }
        System.out.println("[MattMC graphics audit] equipment-phase.ready pose="+pose+" frame="+frame
            +" waitedNanos="+(now-waitStarted)+" observations="+waitObservations+" phaseCandidates="+phaseCandidates
            +" clockDisagreements="+clockDisagreements+" centerMisses="+centerMisses);
        return true;
    }
    // Tick replication can finish while the observed render frame still contains
    // partial-tick interpolation. Select a settled frame; never change its age.
    static synchronized boolean observedSteppedAgeReady(int pose){
        if(pose<0 || pose>=5 || !poseValid || zombiePoses.isEmpty())return false;
        for(var observed:zombiePoses)
            if(observed.getAsJsonObject().get("ageInTicks").getAsFloat()!=pose*10+1)return false;
        return true;
    }
    private static boolean near(long a,long b){long d=Math.floorMod(a-b,330000L);return d<=16 || d>=330000L-16;}
    private static boolean complete(){return enabled() && valid && ((nativeCount==4 && frozenCount==0)
            || (nativeCount==0 && frozenCount>=1 && frozenCount<=4));}
    private GraphicsAuditEquipmentFoilTiming() {}
    public static boolean enabled() { return Boolean.getBoolean("mattmc.dev.equipmentFoilTiming"); }
    public static synchronized void beginFrame() { if (enabled()) beginFrame(GraphicsAuditGroundFoilSources.currentFrameIndex()); }
    static synchronized void beginFrame(long renderedFrame) {
        if (!enabled()) return;
        samples.asList().clear(); zombiePoses.asList().clear(); entityTransforms.asList().clear();
        GraphicsAuditEquipmentTrimSources.beginFrame(renderedFrame);
        GraphicsAuditEquipmentGeometry.beginFrame(renderedFrame);
        GraphicsAuditInventoryPreviewInputs.beginFrame(renderedFrame);
        poseValid=entityTransformValid=true; frame=renderedFrame; valid=frame>0; nativeCount=frozenCount=0;
    }
    public static synchronized void observeScaledTicks(long ticks, float scale) {
        if (!enabled() || scale!=0.16F) return;
        JsonObject s=new JsonObject();s.addProperty("provider","frozen-armor-state");
        s.addProperty("scaledTicks",ticks);append(s,ticks,false);
    }
    public static synchronized void observeSemanticClock(long mesh, long clock, double speed, float strength) {
        if (!enabled()) return;
        if (mesh==0 || clock<0 || !Double.isFinite(speed) || speed<0 || speed>1
            || !Float.isFinite(strength) || strength<0 || strength>1) {valid=false;return;}
        long ticks=(long)(clock*speed*8.0);
        JsonObject s=new JsonObject();s.addProperty("provider","semantic-armor");
        s.addProperty("meshKey",Long.toUnsignedString(mesh));s.addProperty("clockMillis",clock);
        s.addProperty("speed",speed);s.addProperty("strength",strength);s.addProperty("scaledTicks",ticks);
        append(s,ticks,true);
    }
	/** Observes an accepted GUI entity-preview armor batch after semantic collection. */
	public static synchronized void observeGuiSemanticClock(long sequence, long clock, double speed, float strength) {
		if (!enabled()) return;
		if (sequence < 0 || clock < 0 || !Double.isFinite(speed) || speed < 0 || speed > 1
			|| !Float.isFinite(strength) || strength < 0 || strength > 1) { valid=false; return; }
		long ticks=(long)(clock*speed*8.0);
		JsonObject s=new JsonObject();s.addProperty("provider","semantic-armor-orthographic");
		s.addProperty("batchSequence",sequence);s.addProperty("clockMillis",clock);
		s.addProperty("speed",speed);s.addProperty("strength",strength);s.addProperty("scaledTicks",ticks);
		append(s,ticks,true);
	}
    private static void append(JsonObject sample,long ticks,boolean semantic) {
        if (frame<=0 || ticks<0 || samples.size()>=16) {valid=false;return;}
        samples.add(sample);if(semantic)nativeCount++;else frozenCount++;
    }
    /** Copies the completed vanilla model pose without changing any model field. */
    public static synchronized void observeZombiePose(float age,float attack,boolean aggressive,
            net.minecraft.client.model.geom.ModelPart left,net.minecraft.client.model.geom.ModelPart right){
        if(!enabled())return;
        if(!Float.isFinite(age) || !Float.isFinite(attack) || left==null || right==null || zombiePoses.size()>=16){
            poseValid=false;return;
        }
        JsonObject pose=new JsonObject();pose.addProperty("ageInTicks",age);pose.addProperty("attackTime",attack);
        pose.addProperty("aggressive",aggressive);
        pose.add("leftArm",arm(left));pose.add("rightArm",arm(right));zombiePoses.add(pose);
    }
    public static synchronized void observeZombieModelPose(float age,float attack,boolean aggressive,
            net.minecraft.client.model.HumanoidModel<?> model){
        if(!enabled())return;
        int before=zombiePoses.size();
        observeZombiePose(age,attack,aggressive,model.leftArm,model.rightArm);
        if(zombiePoses.size()!=before+1)return;
        JsonObject parts=new JsonObject();
        parts.add("head",arm(model.head));parts.add("body",arm(model.body));
        parts.add("leftLeg",arm(model.leftLeg));parts.add("rightLeg",arm(model.rightLeg));
        parts.add("root",arm(model.root()));
        zombiePoses.get(before).getAsJsonObject().add("remainingParts",parts);
    }
    public static synchronized void observeEntityTransform(
            net.minecraft.client.renderer.entity.state.LivingEntityRenderState state,net.blaze3d.vertex.PoseStack.Pose pose){
        if(!enabled() || !(state instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState))return;
        if(entityTransforms.size()>=16 || !pose.pose().isFinite() || !pose.normal().isFinite()){
            entityTransformValid=false;return;
        }
        JsonObject entry=new JsonObject();entry.add("modelView",values(pose.pose().get(new float[16])));
        entry.add("normal",values(pose.normal().get(new float[9])));
        entry.add("state",values(new float[]{state.ageInTicks,state.bodyRot,state.yRot,state.xRot,
            state.scale,state.ageScale,state.walkAnimationPos,state.walkAnimationSpeed}));
        entry.addProperty("lightCoords",state.lightCoords);entityTransforms.add(entry);
    }
    private static JsonArray values(float[] values){
        JsonArray result=new JsonArray();
        for(float value:values){if(!Float.isFinite(value)){entityTransformValid=false;result.add(0F);}else result.add(value);}
        return result;
    }
    private static JsonArray arm(net.minecraft.client.model.geom.ModelPart part){
        JsonArray values=new JsonArray();
        for(float v:new float[]{part.x,part.y,part.z,part.xRot,part.yRot,part.zRot,part.xScale,part.yScale,part.zScale}){
            if(!Float.isFinite(v)){poseValid=false;values.add(0.0F);}else values.add(v);
        }
        return values;
    }
    public static synchronized String snapshot() {
        JsonObject result=new JsonObject();result.addProperty("schema","equipment-foil-clock-observation-v1");
        result.addProperty("enabled",enabled());result.addProperty("renderedFrameIndex",frame);
        result.addProperty("complete",complete());
        if(movingRequested()){result.addProperty("requestedPhase",requestedPhase());
            result.addProperty("requestedPoseStep",poseStep());}
        result.add("samples",samples.deepCopy());
        if(GraphicsAuditEquipmentTickFixture.enabled())result.add("simulation",GraphicsAuditEquipmentTickFixture.snapshot());
        result.addProperty("zombiePosesComplete",enabled() && poseValid && !zombiePoses.isEmpty());
        result.add("zombiePoses",zombiePoses.deepCopy());
        result.addProperty("entityTransformsComplete",enabled() && entityTransformValid && !entityTransforms.isEmpty());
        result.add("entityTransforms",entityTransforms.deepCopy());
        if(!System.getProperty("mattmc.dev.graphicsAuditEquipmentMaterial", "").isEmpty()
                || !GraphicsAuditInventoryEquipmentFixture.mode().isEmpty()
                || GraphicsAuditEntityPreviewFixture.mode().equals("smithing-netherite-chestplate")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-iron-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-gold-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-copper-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-dyed-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-iron-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-gold-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-copper-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-dyed-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-black")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-brown")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-creamy")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-chestnut")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-gray")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-dark-brown")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-white")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-white-field")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-black-dots")
                || GraphicsAuditEntityPreviewFixture.mode().equals("skeleton-horse-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("skeleton-horse-baby-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("zombie-horse-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("zombie-horse-baby-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("donkey-chested-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("donkey-baby-chested-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("mule-chested-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("mule-baby-chested-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("llama-chested-blue-carpet")
                || GraphicsAuditEntityPreviewFixture.mode().equals("llama-baby-chested-blue-carpet"))
            result.add("modelGeometry",GraphicsAuditEquipmentGeometry.snapshot());
        if(!GraphicsAuditInventoryEquipmentFixture.mode().isEmpty() || GraphicsAuditEntityPreviewFixture.requested())
            result.add("inventoryPreview",GraphicsAuditInventoryPreviewInputs.snapshot());
		if(!System.getProperty("mattmc.dev.graphicsAuditEquipmentTrim", "").isEmpty()
				|| GraphicsAuditInventoryEquipmentFixture.trimRequested())
			result.add("trimSources",GraphicsAuditEquipmentTrimSources.snapshot());
        if(movingRequested()){
            JsonObject wait=new JsonObject();wait.addProperty("pose",waitingPose);wait.addProperty("observations",waitObservations);
            wait.addProperty("phaseCandidates",phaseCandidates);wait.addProperty("clockDisagreements",clockDisagreements);
            wait.addProperty("centerMisses",centerMisses);
            wait.addProperty("incompleteObservations",incompleteObservations);
            wait.add("candidates",waitCandidates.deepCopy());result.add("phaseWait",wait);
        }
        return result.toString();
    }
}

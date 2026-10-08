package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bounded copied GUI submission inputs; never changes a model, camera or renderer. */
public final class GraphicsAuditInventoryPreviewInputs {
    private static long frame;
    private static boolean valid;
    private static JsonObject previousView;
    private static long previousViewFrame = -1;
    private static int stableViews;
    static void resetReadiness() { previousView=null; previousViewFrame=-1; stableViews=0; }
    static boolean ready() {
        if (!valid || stableViews<2 || previousViewFrame!=frame || observations.isEmpty()) return false;
        boolean background=false, returned=false;
        for(var entry:screenMouse) {
            var row=entry.getAsJsonObject();
            if(!row.get("stored").equals(row.get("supplied"))) return false;
            if(row.get("stage").getAsString().equals("background")) background=true;
            if(row.get("stage").getAsString().equals("render-return") && background) returned=true;
        }
        return background && returned;
    }
    private static final JsonArray observations = new JsonArray();
    private static final JsonArray screenMouse = new JsonArray();
    private static boolean enabled() {
        return GraphicsAuditEquipmentFoilTiming.enabled()
            && (!GraphicsAuditInventoryEquipmentFixture.mode().isEmpty() || GraphicsAuditEntityPreviewFixture.requested());
    }
    static void beginFrame(long index) {
        frame=index; valid=index>0; observations.asList().clear(); screenMouse.asList().clear();
    }
    public static void observeScreenMouse(String stage, float storedX, float storedY, int suppliedX, int suppliedY) {
        if (!enabled()) return;
        if (screenMouse.size()>=8 || !(stage.equals("background") || stage.equals("render-return"))) {valid=false;return;}
        JsonObject row=new JsonObject();row.addProperty("stage",stage);
        row.add("stored",numbers(storedX,storedY));row.add("supplied",numbers(suppliedX,suppliedY));
        screenMouse.add(row);
    }
    public static void observe(EntityRenderState state, float scale, Vector3f translation,
            Quaternionf rotation, Quaternionf camera, int left, int top, int right, int bottom) {
        if (!enabled()) return;
        if (observations.size()>=4 || !(state instanceof LivingEntityRenderState living)) {valid=false;return;}
        JsonObject row=new JsonObject();
        row.add("bounds",numbers(left,top,right,bottom));
        row.add("translation",numbers(translation.x,translation.y,translation.z));
        row.add("rotation",numbers(rotation.x,rotation.y,rotation.z,rotation.w));
        if(camera!=null)row.add("cameraRotation",numbers(camera.x,camera.y,camera.z,camera.w));
        row.add("scales",numbers(scale,living.scale,living.ageScale));
        row.add("angles",numbers(living.bodyRot,living.yRot,living.xRot));
        row.add("animation",numbers(state.ageInTicks,living.walkAnimationPos,living.walkAnimationSpeed,living.deathTime));
        row.addProperty("invisible",state.isInvisible);
        row.addProperty("invisibleToPlayer",living.isInvisibleToPlayer);
        row.addProperty("pose",living.pose.name());
        row.addProperty("light",state.lightCoords);
        if(state instanceof AvatarRenderState avatar) {
            row.addProperty("skin",avatar.skin.body().texturePath().toString());
            row.addProperty("skinModel",avatar.skin.model().toString());
            row.addProperty("spectator",avatar.isSpectator);
            JsonArray visible=new JsonArray();
            for(boolean value:new boolean[]{avatar.showHat,avatar.showJacket,avatar.showLeftPants,
                    avatar.showRightPants,avatar.showLeftSleeve,avatar.showRightSleeve})visible.add(value);
            row.add("outerParts",visible);
        }
        if(state instanceof ArmorStandRenderState stand) {
            JsonObject armorStand=new JsonObject();
            armorStand.addProperty("marker",stand.isMarker);armorStand.addProperty("small",stand.isSmall);
            armorStand.addProperty("showArms",stand.showArms);armorStand.addProperty("showBasePlate",stand.showBasePlate);
            armorStand.addProperty("wiggle",stand.wiggle);
            armorStand.add("head",numbers(stand.headPose.x(),stand.headPose.y(),stand.headPose.z()));
            armorStand.add("body",numbers(stand.bodyPose.x(),stand.bodyPose.y(),stand.bodyPose.z()));
            armorStand.add("leftArm",numbers(stand.leftArmPose.x(),stand.leftArmPose.y(),stand.leftArmPose.z()));
            armorStand.add("rightArm",numbers(stand.rightArmPose.x(),stand.rightArmPose.y(),stand.rightArmPose.z()));
            armorStand.add("leftLeg",numbers(stand.leftLegPose.x(),stand.leftLegPose.y(),stand.leftLegPose.z()));
            armorStand.add("rightLeg",numbers(stand.rightLegPose.x(),stand.rightLegPose.y(),stand.rightLegPose.z()));
            row.add("armorStand",armorStand);
        }
        observations.add(row);
        if (valid && previousViewFrame != frame) {
            JsonObject view=row.deepCopy();
            // Wait for the screen's prior-frame mouse view, without freezing animation.
            view.remove("animation");
            stableViews=view.equals(previousView) ? Math.min(2,stableViews+1) : 1;
            previousView=view; previousViewFrame=frame;
        }
    }
    private static JsonArray numbers(float... values) {
        JsonArray result=new JsonArray();
        for(float value:values) {
            if(!Float.isFinite(value)){valid=false;result.add(0F);}else result.add(value);
        }
        return result;
    }
    static JsonObject snapshot() {
        JsonObject result=new JsonObject();
        result.addProperty("schema","inventory-preview-inputs-v1");
        result.addProperty("enabled",enabled());
        result.addProperty("renderedFrameIndex",frame);
        result.addProperty("complete",enabled() && valid && !observations.isEmpty());
        result.addProperty("capabilityAdmitted",false);
        result.addProperty("viewStable",ready());
        result.add("observations",observations.deepCopy());
        result.add("screenMouse",screenMouse.deepCopy());
        return result;
    }
    private GraphicsAuditInventoryPreviewInputs() {}
}

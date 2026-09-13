package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.blaze3d.vertex.PoseStack;

/** Bounded read-only CPU observations. Never selects a model, pose, material or clock. */
public final class GraphicsAuditWolfInputs {
    private static long frame;
    private static boolean valid;
    private static final JsonArray models = new JsonArray();
    private static final JsonArray transforms = new JsonArray();
    private static final JsonArray completedDraws = new JsonArray();
    private static final JsonArray geometrySources = new JsonArray();
    private static boolean enabled() { return GraphicsAuditWolfArmorFixture.requested(); }
    static synchronized void begin(long selectedFrame) {
        frame=selectedFrame;valid=frame>0;models.asList().clear();transforms.asList().clear();completedDraws.asList().clear();
        geometrySources.asList().clear();
    }
    private static void selectFrame(long selectedFrame) { if (frame!=selectedFrame) begin(selectedFrame); }
    public static void observeModel(ModelPart root, WolfRenderState state) {
        if (enabled()) observeModel(root,state,GraphicsAuditGroundFoilSources.currentFrameIndex());
    }
    static synchronized void observeModel(ModelPart root, WolfRenderState state,long selectedFrame) {
        selectFrame(selectedFrame);
        if (root==null || state==null || models.size()>=16) { valid=false;return; }
        JsonObject row=new JsonObject();row.add("state",state(state));
        JsonObject parts=new JsonObject();visit(root,"root",parts,0);row.add("parts",parts);models.add(row);
        if (valid) observeGeometry(root);
    }
    /** Model-local CPU source only; a fresh stack leaves all renderer state untouched. */
    private static void observeGeometry(ModelPart root) {
        JsonArray quads=new JsonArray();
        root.visit(new PoseStack(),(pose,path,cubeIndex,cube)-> {
            for (var polygon:cube.polygons) {
                if (!valid) return;
                if (quads.size()>=128 || polygon==null || polygon.vertices().length!=4) {
                    valid=false;return;
                }
                JsonObject quad=new JsonObject();quad.addProperty("part",path);quad.addProperty("cube",cubeIndex);
                var normal=pose.transformNormal(polygon.normal(),new org.joml.Vector3f());
                quad.add("normal",values(normal.x,normal.y,normal.z));
                JsonArray vertices=new JsonArray();
                for (var vertex:polygon.vertices()) {
                    var position=pose.pose().transformPosition(vertex.worldX(),vertex.worldY(),vertex.worldZ(),new org.joml.Vector3f());
                    vertices.add(values(position.x,position.y,position.z,vertex.u(),vertex.v()));
                }
                quad.add("vertices",vertices);quads.add(quad);
            }
        });
        if (!valid || quads.isEmpty()) {valid=false;return;}
        for (var previous:geometrySources) if (previous.equals(quads)) return;
        if (geometrySources.size()>=4) {valid=false;return;}
        geometrySources.add(quads);
    }
    private static void visit(ModelPart part,String path,JsonObject parts,int depth) {
        if (depth>16 || parts.size()>=64) {valid=false;return;}
        JsonObject value=new JsonObject();
        value.add("pose",values(part.x,part.y,part.z,part.xRot,part.yRot,part.zRot,part.xScale,part.yScale,part.zScale));
        value.addProperty("visible",part.visible);value.addProperty("skipDraw",part.skipDraw);
        parts.add(path,value);
        part.children.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> visit(entry.getValue(),path+"/"+entry.getKey(),parts,depth+1));
    }
    public static synchronized void observeTransform(LivingEntityRenderState state,PoseStack.Pose pose) {
        if (!enabled() || !(state instanceof WolfRenderState wolf)) return;
        observeTransform(wolf,pose,GraphicsAuditGroundFoilSources.currentFrameIndex());
    }
    static synchronized void observeTransform(WolfRenderState wolf,PoseStack.Pose pose,long selectedFrame) {
        selectFrame(selectedFrame);
        if (pose==null || transforms.size()>=16) {valid=false;return;}
        JsonObject row=new JsonObject();row.add("state",state(wolf));
        row.add("modelView",values(pose.pose().get(new float[16])));
        row.add("normal",values(pose.normal().get(new float[9])));transforms.add(row);
    }
    private static JsonObject state(WolfRenderState state) {
        JsonObject row=new JsonObject();
        row.add("values",values(state.ageInTicks,state.bodyRot,state.yRot,state.xRot,state.scale,state.ageScale,
            state.walkAnimationPos,state.walkAnimationSpeed,state.tailAngle,state.headRollAngle,state.shakeAnim,state.wetShade));
        row.addProperty("angry",state.isAngry);row.addProperty("sitting",state.isSitting);
        row.addProperty("baby",state.isBaby);row.addProperty("light",state.lightCoords);
        row.addProperty("texture",state.texture==null?"missing":state.texture.toString());
        return row;
    }
    private static JsonArray values(float... values) {
        JsonArray result=new JsonArray();
        for(float value:values) {if (!Float.isFinite(value)) {valid=false;result.add(0F);} else result.add(value);}
        return result;
    }
    static boolean settledAge(int pose,float observed) {
        return pose>=0 && pose<5 && Float.isFinite(observed) && observed==pose*10+1;
    }
    /** Observes a completed ordinary draw; never queues, sorts or changes it. */
    public static synchronized void observeCompletedDraw(net.minecraft.resources.ResourceLocation texture,
            net.blaze3d.pipeline.RenderPipeline pipeline,int vertices,int indices,org.joml.Matrix4f view) {
        if (!enabled() || texture==null) return;
        String path=texture.toString();
        if (!path.startsWith("minecraft:textures/entity/wolf/")
                && !path.startsWith("minecraft:textures/entity/equipment/wolf_body/")) return;
        selectFrame(GraphicsAuditGroundFoilSources.currentFrameIndex());
        if (completedDraws.size()>=16 || vertices<1 || vertices>512 || indices<1 || indices>768) {
            valid=false;return;
        }
        JsonObject row=new JsonObject();row.addProperty("textureDeclaration",path);
        row.addProperty("pipeline",pipeline.getLocation().toString());
        row.addProperty("depthCompare",pipeline.getDepthTestFunction().toString());
        row.addProperty("depthWrite",pipeline.isWriteDepth());row.addProperty("cull",pipeline.isCull());
        row.addProperty("blend",pipeline.getBlendFunction().map(Object::toString).orElse("none"));
        row.addProperty("vertices",vertices);row.addProperty("indices",indices);
        row.add("view",values(view.get(new float[16])));completedDraws.add(row);
    }
    public static synchronized boolean readyForCapture(net.minecraft.client.Minecraft minecraft,int pose) {
        if (!GraphicsAuditWolfTickFixture.enabled()) return true;
        if (!GraphicsAuditWolfTickFixture.readyForPose(minecraft,pose) || !valid || models.isEmpty()
                || transforms.isEmpty()) return false;
        for (var model:models) if (!settledAge(pose,model.getAsJsonObject().getAsJsonObject("state")
                .getAsJsonArray("values").get(0).getAsFloat())) return false;
        for (var transform:transforms) if (!settledAge(pose,transform.getAsJsonObject().getAsJsonObject("state")
                .getAsJsonArray("values").get(0).getAsFloat())) return false;
        return true;
    }
    public static synchronized String snapshot() {
        JsonObject result=new JsonObject();result.addProperty("schema","wolf-model-inputs-v1");
        result.addProperty("enabled",enabled());result.addProperty("renderedFrameIndex",frame);
        result.addProperty("complete",enabled() && valid && !models.isEmpty() && !transforms.isEmpty());
        result.addProperty("gpuReadback",false);result.addProperty("capabilityAdmitted",false);
        if (GraphicsAuditWolfTickFixture.enabled()) result.add("simulation",GraphicsAuditWolfTickFixture.snapshot());
        result.add("models",models.deepCopy());result.add("transforms",transforms.deepCopy());
        result.add("geometrySources",geometrySources.deepCopy());
        result.add("completedDraws",completedDraws.deepCopy());return result.toString();
    }
}

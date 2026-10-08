package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

/** Bounded observations of ordinary CPU model sources, never GPU state or rendering policy. */
public final class GraphicsAuditEquipmentGeometry {
    private static final JsonArray models = new JsonArray();
    private static final JsonArray draws = new JsonArray();
    private static long frame;
    private static boolean valid;
    private static boolean enabled() {
        return GraphicsAuditEquipmentFoilTiming.enabled()
            && (!System.getProperty("mattmc.dev.graphicsAuditEquipmentMaterial", "").isEmpty()
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
                || GraphicsAuditEntityPreviewFixture.mode().equals("llama-baby-chested-blue-carpet"));
    }
    static synchronized void beginFrame(long selectedFrame) {
        frame=selectedFrame;valid=frame>0;models.asList().clear();draws.asList().clear();
    }
    public static synchronized void observe(net.minecraft.client.model.Model<?> model, Object state,
            RenderType renderType, int tint, int light, int overlay) {
        observe(model,state,renderType,tint,light,overlay,null,null);
    }
    public static synchronized void observe(net.minecraft.client.model.Model<?> model, Object state,
            RenderType renderType, int tint, int light, int overlay, PoseStack.Pose sourcePose) {
        observe(model,state,renderType,tint,light,overlay,sourcePose,null);
    }
    public static synchronized void observeEntityPip(net.minecraft.client.model.Model<?> model, Object state,
            RenderType renderType, int tint, int light, int overlay, PoseStack.Pose sourcePose, int guardPixels) {
        observe(model,state,renderType,tint,light,overlay,sourcePose,guardPixels);
    }
    private static void observe(net.minecraft.client.model.Model<?> model, Object state,
            RenderType renderType, int tint, int light, int overlay, PoseStack.Pose sourcePose, Integer guardPixels) {
        if (!enabled()) return;
        boolean worldEquipment = !System.getProperty("mattmc.dev.graphicsAuditEquipmentMaterial", "").isEmpty()
            && state instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState;
        boolean inventoryEquipment = !GraphicsAuditInventoryEquipmentFixture.mode().isEmpty()
            && state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        boolean smithingEquipment = GraphicsAuditEntityPreviewFixture.mode().equals("smithing-netherite-chestplate")
            && state instanceof net.minecraft.client.renderer.entity.state.ArmorStandRenderState
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.SmithingScreen;
        boolean horseEquipment = (GraphicsAuditEntityPreviewFixture.mode().equals("horse-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-iron-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-gold-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-copper-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-dyed-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped"))
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean babyHorse = (GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-iron-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-gold-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-copper-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-dyed-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped"))
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse && horse.isBaby
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean markedHorse = (GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped"))
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && horse.isBaby == (GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped"))
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.WHITE_DOTS
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean blackHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-black")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.BLACK
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.NONE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean brownHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-brown")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.BROWN
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.NONE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean creamyHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-creamy")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.CREAMY
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.NONE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean chestnutHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-chestnut")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.CHESTNUT
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.NONE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean grayHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-gray")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.GRAY
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.NONE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean darkBrownHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-dark-brown")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.DARK_BROWN
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.NONE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean whiteMarkingHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-white")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.WHITE
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.WHITE
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean whiteFieldHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-white-field")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.WHITE
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.WHITE_FIELD
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean blackDotsHorse = GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-black-dots")
            && state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horse
            && !horse.isBaby && horse.variant == net.minecraft.world.entity.animal.horse.Variant.WHITE
            && horse.markings == net.minecraft.world.entity.animal.horse.Markings.BLACK_DOTS
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean skeletonHorsePreview = (GraphicsAuditEntityPreviewFixture.mode().equals("skeleton-horse-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("skeleton-horse-baby-saddled"))
            && state instanceof net.minecraft.client.renderer.entity.state.EquineRenderState horse
            && horse.isBaby == GraphicsAuditEntityPreviewFixture.mode().contains("baby")
            && horse.saddle.is(net.minecraft.world.item.Items.SADDLE)
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean zombieHorsePreview = (GraphicsAuditEntityPreviewFixture.mode().equals("zombie-horse-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("zombie-horse-baby-saddled"))
            && state instanceof net.minecraft.client.renderer.entity.state.EquineRenderState horse
            && horse.isBaby == GraphicsAuditEntityPreviewFixture.mode().contains("baby")
            && horse.saddle.is(net.minecraft.world.item.Items.SADDLE)
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean donkeyPreview = GraphicsAuditEntityPreviewFixture.mode().startsWith("donkey-")
            && state instanceof net.minecraft.client.renderer.entity.state.DonkeyRenderState donkey
            && donkey.hasChest && donkey.isBaby == GraphicsAuditEntityPreviewFixture.mode().contains("baby")
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean mulePreview = GraphicsAuditEntityPreviewFixture.mode().startsWith("mule-")
            && state instanceof net.minecraft.client.renderer.entity.state.DonkeyRenderState mule
            && mule.hasChest && mule.isBaby == GraphicsAuditEntityPreviewFixture.mode().contains("baby")
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        boolean llamaPreview = GraphicsAuditEntityPreviewFixture.mode().startsWith("llama-")
            && state instanceof net.minecraft.client.renderer.entity.state.LlamaRenderState llama
            && llama.isBaby == GraphicsAuditEntityPreviewFixture.mode().contains("baby")
            && llama.hasChest == !llama.isBaby
            && llama.bodyItem.is(net.minecraft.world.item.Items.BLUE_CARPET)
            && net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
        if (!(worldEquipment || inventoryEquipment || smithingEquipment || horseEquipment || babyHorse || markedHorse || blackHorse || brownHorse || creamyHorse || chestnutHorse || grayHorse || darkBrownHorse || whiteMarkingHorse || whiteFieldHorse || blackDotsHorse || skeletonHorsePreview || zombieHorsePreview || donkeyPreview || mulePreview || llamaPreview)) return;
        var texture=renderType.auditTextureDeclaration().orElse(null);
        if (texture==null || !(texture.getPath().startsWith("textures/entity/equipment/")
                || texture.getPath().contains("glint")
                || (babyHorse || markedHorse) && texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                || blackHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_black.png")
                || brownHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_brown.png")
                || creamyHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_creamy.png")
                || chestnutHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_chestnut.png")
                || grayHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_gray.png")
                || darkBrownHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_darkbrown.png")
                || whiteMarkingHorse && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                    || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_white.png"))
                || whiteFieldHorse && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                    || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_whitefield.png"))
                || blackDotsHorse && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                    || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_blackdots.png"))
                || skeletonHorsePreview && (texture.toString().equals("minecraft:textures/entity/horse/horse_skeleton.png")
                    || texture.toString().equals("minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png"))
                || zombieHorsePreview && (texture.toString().equals("minecraft:textures/entity/horse/horse_zombie.png")
                    || texture.toString().equals("minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png"))
                || markedHorse && texture.toString().equals("minecraft:textures/entity/horse/horse_markings_whitedots.png")
                || donkeyPreview && texture.toString().equals("minecraft:textures/entity/horse/donkey.png")
                || mulePreview && texture.toString().equals("minecraft:textures/entity/horse/mule.png")
                || llamaPreview && texture.toString().equals("minecraft:textures/entity/llama/creamy.png"))) return;
        if (models.size()>=64 || frame<=0) {valid=false;return;}
        JsonObject row=new JsonObject();row.addProperty("texture",texture.toString());
        row.addProperty("pipeline",renderType.pipeline().getLocation().toString());
        row.addProperty("tint",tint);row.addProperty("light",light);row.addProperty("overlay",overlay);
        if(guardPixels!=null){if(guardPixels<0 || guardPixels>8){valid=false;return;}row.addProperty("entityPipGuardPixels",guardPixels);}
        if (inventoryEquipment || smithingEquipment || babyHorse || markedHorse || blackHorse || brownHorse || creamyHorse || chestnutHorse || grayHorse || darkBrownHorse || whiteMarkingHorse || whiteFieldHorse || blackDotsHorse || skeletonHorsePreview || zombieHorsePreview || donkeyPreview || mulePreview || llamaPreview) {
            row.add("sourcePose",copiedPose(sourcePose));
            var living=(net.minecraft.client.renderer.entity.state.LivingEntityRenderState)state;
            row.add("state",values(new float[]{living.ageInTicks,living.ageScale,living.bodyRot,living.yRot,living.xRot}));
        }
        row.add("quads",geometry(model.root()));models.add(row);
    }
    static JsonObject copiedPose(PoseStack.Pose pose) {
        JsonObject result=new JsonObject();
        if (pose==null || !pose.pose().isFinite() || !pose.normal().isFinite()) {valid=false;return result;}
        result.add("modelView",values(pose.pose().get(new float[16])));
        result.add("normal",values(pose.normal().get(new float[9])));
        return result;
    }
    /** Called after ordinary drawIndexed returns; no GPU completion claim. */
    public static synchronized void observeCompletedDraw(net.minecraft.resources.ResourceLocation texture,
            net.blaze3d.pipeline.RenderPipeline pipeline,int vertices,int indices,org.joml.Matrix4f view) {
        boolean babyHorse=(GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-iron-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-gold-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-copper-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-dyed-leather-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped"))
            && texture!=null && texture.toString().equals("minecraft:textures/entity/horse/horse_white.png");
        boolean markedHorse=(GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-marked-equipped")
                || GraphicsAuditEntityPreviewFixture.mode().equals("horse-baby-marked-equipped")) && texture!=null
            && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_whitedots.png"));
        boolean blackHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-black") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/horse_black.png");
        boolean brownHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-brown") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/horse_brown.png");
        boolean creamyHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-creamy") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/horse_creamy.png");
        boolean chestnutHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-chestnut") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/horse_chestnut.png");
        boolean grayHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-gray") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/horse_gray.png");
        boolean darkBrownHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-dark-brown") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/horse_darkbrown.png");
        boolean whiteMarkingHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-white") && texture!=null
            && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_white.png"));
        boolean whiteFieldHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-white-field") && texture!=null
            && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_whitefield.png"));
        boolean blackDotsHorse=GraphicsAuditEntityPreviewFixture.mode().equals("horse-marking-black-dots") && texture!=null
            && (texture.toString().equals("minecraft:textures/entity/horse/horse_white.png")
                || texture.toString().equals("minecraft:textures/entity/horse/horse_markings_blackdots.png"));
        boolean skeletonHorsePreview=(GraphicsAuditEntityPreviewFixture.mode().equals("skeleton-horse-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("skeleton-horse-baby-saddled")) && texture!=null
            && (texture.toString().equals("minecraft:textures/entity/horse/horse_skeleton.png")
                || texture.toString().equals("minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png"));
        boolean zombieHorsePreview=(GraphicsAuditEntityPreviewFixture.mode().equals("zombie-horse-saddled")
                || GraphicsAuditEntityPreviewFixture.mode().equals("zombie-horse-baby-saddled")) && texture!=null
            && (texture.toString().equals("minecraft:textures/entity/horse/horse_zombie.png")
                || texture.toString().equals("minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png"));
        boolean donkeyPreview=GraphicsAuditEntityPreviewFixture.mode().startsWith("donkey-") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/donkey.png");
        boolean mulePreview=GraphicsAuditEntityPreviewFixture.mode().startsWith("mule-") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/horse/mule.png");
        boolean llamaPreview=GraphicsAuditEntityPreviewFixture.mode().startsWith("llama-") && texture!=null
            && texture.toString().equals("minecraft:textures/entity/llama/creamy.png");
        if (!enabled() || texture==null || !(texture.getPath().startsWith("textures/entity/equipment/")
                || texture.getPath().contains("glint") || babyHorse || markedHorse || blackHorse || brownHorse || creamyHorse || chestnutHorse || grayHorse || darkBrownHorse || whiteMarkingHorse || whiteFieldHorse || blackDotsHorse || skeletonHorsePreview || zombieHorsePreview || donkeyPreview || mulePreview || llamaPreview)) return;
        if (frame<=0 || draws.size()>=64 || vertices<1 || vertices>2048 || indices<1 || indices>3072) {
            valid=false;return;
        }
        JsonObject row=new JsonObject();row.addProperty("texture",texture.toString());
        row.addProperty("pipeline",pipeline.getLocation().toString());
        row.addProperty("depthCompare",pipeline.getDepthTestFunction().toString());
        row.addProperty("depthWrite",pipeline.isWriteDepth());row.addProperty("cull",pipeline.isCull());
        row.addProperty("blend",pipeline.getBlendFunction().map(Object::toString).orElse("none"));
        row.addProperty("vertices",vertices);row.addProperty("indices",indices);
        row.add("view",values(view.get(new float[16])));draws.add(row);
    }
    static JsonArray geometry(ModelPart root) {
        JsonArray quads=new JsonArray();
        var visible=new java.util.HashSet<String>();
        visiblePaths(root,"",visible,0,new int[]{0},true);
        if (!valid) return quads;
        root.visit(new PoseStack(),(pose,path,cubeIndex,cube)-> {
            if (!visible.contains(path)) return;
            for(var polygon:cube.polygons) {
                if (!valid) return;
                if (quads.size()>=128 || polygon==null || polygon.vertices().length!=4) {valid=false;return;}
                JsonObject q=new JsonObject();q.addProperty("part",path);q.addProperty("cube",cubeIndex);
                var normal=pose.transformNormal(polygon.normal(),new org.joml.Vector3f());
                q.add("normal",values(normal.x,normal.y,normal.z));
                JsonArray vertices=new JsonArray();
                for(var vertex:polygon.vertices()) {
                    var p=pose.pose().transformPosition(vertex.worldX(),vertex.worldY(),vertex.worldZ(),new org.joml.Vector3f());
                    vertices.add(values(p.x,p.y,p.z,vertex.u(),vertex.v()));
                }
                q.add("vertices",vertices);quads.add(q);
            }
        });
        return quads;
    }
    private static void visiblePaths(ModelPart part,String path,java.util.Set<String> visible,int depth,int[] nodes,boolean parentVisible) {
        if (part==null || depth>16 || ++nodes[0]>64) {valid=false;return;}
        boolean effectiveVisible=parentVisible && part.visible;
        if (effectiveVisible && !part.skipDraw) visible.add(path);
        for(var child:part.children.entrySet()) visiblePaths(child.getValue(),path+"/"+child.getKey(),visible,depth+1,nodes,effectiveVisible);
    }
    private static JsonArray values(float... values) {
        JsonArray result=new JsonArray();
        for(float v:values) {if(!Float.isFinite(v)){valid=false;result.add(0F);}else result.add(v);}
        return result;
    }
    static synchronized JsonObject snapshot() {
        JsonObject result=new JsonObject();result.addProperty("schema","equipment-model-geometry-v1");
        result.addProperty("enabled",enabled());result.addProperty("renderedFrameIndex",frame);
        result.addProperty("complete",enabled() && valid && !models.isEmpty());
        result.addProperty("gpuReadback",false);result.addProperty("capabilityAdmitted",false);
        result.add("models",models.deepCopy());result.add("completedDraws",draws.deepCopy());return result;
    }
    private GraphicsAuditEquipmentGeometry() {}
}

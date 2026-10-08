package net.minecraft.client.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Bounded CPU-only observation of actual trim producer inputs. */
public final class GraphicsAuditEquipmentTrimSources {
    private static final JsonArray sources=new JsonArray();
    private static long frame;
    private static boolean valid;
    private static boolean enabled(){
        return GraphicsAuditEquipmentFoilTiming.enabled()
            && (!System.getProperty("mattmc.dev.graphicsAuditEquipmentTrim", "").isEmpty()
                || GraphicsAuditInventoryEquipmentFixture.trimRequested());
    }
    static synchronized void beginFrame(long selectedFrame){
        sources.asList().clear();frame=selectedFrame;valid=frame>0;
    }
    public static synchronized void observe(TextureAtlasSprite sprite,RenderType renderType,String layer,String asset){
        if(!enabled())return;
        if(frame<=0 || sources.size()>=16 || sprite==null){valid=false;return;}
        var contents=sprite.contents();var image=contents.originalImage;
        int width=contents.width(),height=contents.height();
        // Animated/oversized sheets are outside this diagnostic's bounded scope.
        if(width<=0 || height<=0 || width>128 || height>128
                || image.getWidth()!=width || image.getHeight()!=height){valid=false;return;}
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int argb=image.getPixel(x,y);
            digest.update((byte)(argb>>>16));digest.update((byte)(argb>>>8));
            digest.update((byte)argb);digest.update((byte)(argb>>>24));
        }
        var row=new JsonObject();
        row.addProperty("sprite",contents.name().toString());
        row.addProperty("atlas",sprite.atlasLocation().toString());
        row.addProperty("layer",layer);row.addProperty("asset",asset);
        row.addProperty("width",width);row.addProperty("height",height);
        row.addProperty("x",sprite.getX());row.addProperty("y",sprite.getY());
        row.addProperty("u0",sprite.getU0());row.addProperty("u1",sprite.getU1());
        row.addProperty("v0",sprite.getV0());row.addProperty("v1",sprite.getV1());
        row.addProperty("rgbaSha256",HexFormat.of().formatHex(digest.digest()));
        row.addProperty("depthTest",renderType.pipeline().getDepthTestFunction().toString());
        row.addProperty("depthWrite",renderType.pipeline().isWriteDepth());
        sources.add(row);
    }
    static synchronized JsonObject snapshot(){
        var result=new JsonObject();result.addProperty("schema","equipment-trim-cpu-sources-v1");
        result.addProperty("enabled",enabled());result.addProperty("renderedFrameIndex",frame);
        result.addProperty("complete",enabled() && valid && sources.size()==4);
        result.addProperty("gpuReadback",false);result.add("sources",sources.deepCopy());return result;
    }
    private GraphicsAuditEquipmentTrimSources(){}
}

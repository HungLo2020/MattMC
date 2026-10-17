package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.util.NativeLibraryLoader;
import org.joml.Vector3f;

/** Immutable authored item poses, consumed directly by Rust. The bounded cache
 * compares identity and current scalar bits; mutable built-in vectors cannot
 * invalidate a previously captured pose. Custom vector callbacks stay in Java. */
public final class NativeItemLayerTransform {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_item_transform_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_item_transform_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RESOLVE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_item_transform_resolve", FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
    private static final LinkedHashMap<Key, NativeItemLayerTransform> CACHE = new LinkedHashMap<>(256,0.75F,true);
    private record Key(ItemTransform transform) {
        @Override public int hashCode() { return System.identityHashCode(transform); }
        @Override public boolean equals(Object o) { return o instanceof Key k && k.transform == transform; }
    }
    private final float[] authored;
    private final MemorySegment owner;
    private final Capture right;
    private final Capture left;
    private NativeItemLayerTransform(float[] authored, boolean noTransform) {
        this.authored = authored;
        try (Arena input = Arena.ofConfined()) {
            MemorySegment pointer = (MemorySegment) CREATE.invokeExact(input.allocateFrom(ValueLayout.JAVA_FLOAT, authored),noTransform ? 1 : 0);
            if (pointer.address()==0) throw new IllegalArgumentException("Nonfinite authored item transform");
            try { owner=pointer.asReadOnly().reinterpret(340,Arena.ofAuto(),NativeItemLayerTransform::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
        } catch (RuntimeException | Error failure) {throw failure;}
        catch (Throwable failure) {throw new IllegalStateException("Cannot create native item transform",failure);}
        right=new Capture(this,false);left=new Capture(this,true);
    }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); }
        catch (Throwable failure) {throw new IllegalStateException("Cannot release native item transform",failure);}
    }
    public static Capture capture(ItemTransform transform, boolean leftHand) {
        if (org.joml.Options.FASTMATH || org.joml.Options.USE_MATH_FMA) return null;
        boolean none=transform==ItemTransform.NO_TRANSFORM;
        if (!none && (transform.rotation()==null || transform.translation()==null || transform.scale()==null
            || transform.rotation().getClass()!=Vector3f.class || transform.translation().getClass()!=Vector3f.class
            || transform.scale().getClass()!=Vector3f.class)) return null;
        var r=none ? null : (Vector3f)transform.rotation();var t=none ? null : (Vector3f)transform.translation();var s=none ? null : (Vector3f)transform.scale();
        // No temporary scalar arrays on cache hits. NO_TRANSFORM ignores its vectors.
        float rx=none?0:r.x, ry=none?0:r.y, rz=none?0:r.z;
        float tx=none?0:t.x, ty=none?0:t.y, tz=none?0:t.z;
        float sx=none?1:s.x, sy=none?1:s.y, sz=none?1:s.z;
        synchronized (CACHE) {
            Key key=new Key(transform);
            NativeItemLayerTransform cached=CACHE.get(key);
            if (cached==null || !cached.matches(rx,ry,rz,tx,ty,tz,sx,sy,sz)) {
                // Nonfinite poses retain the original Java exception path.
                if (!Float.isFinite(rx)||!Float.isFinite(ry)||!Float.isFinite(rz)
                    ||!Float.isFinite(tx)||!Float.isFinite(ty)||!Float.isFinite(tz)
                    ||!Float.isFinite(sx)||!Float.isFinite(sy)||!Float.isFinite(sz)
                    || (Math.abs(sx)!=Math.abs(sy)||Math.abs(sx)!=Math.abs(sz)) && (sx==0||sy==0||sz==0)) return null;
                try {cached=new NativeItemLayerTransform(new float[]{rx,ry,rz,tx,ty,tz,sx,sy,sz},none);}
                catch (IllegalArgumentException nonfinite) {return null;}
                CACHE.put(key,cached);
                if (CACHE.size()>256) CACHE.remove(CACHE.keySet().iterator().next());
            }
            return leftHand?cached.left:cached.right;
        }
    }
    private boolean matches(float rx,float ry,float rz,float tx,float ty,float tz,float sx,float sy,float sz) {
        return bits(authored[0],rx)&&bits(authored[1],ry)&&bits(authored[2],rz)
            &&bits(authored[3],tx)&&bits(authored[4],ty)&&bits(authored[5],tz)
            &&bits(authored[6],sx)&&bits(authored[7],sy)&&bits(authored[8],sz);
    }
    private static boolean bits(float a,float b) {return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);}
    public record Capture(NativeItemLayerTransform transform, boolean leftHand) {
        public Capture {java.util.Objects.requireNonNull(transform);}
        public long ownerAddress() {return transform.owner.address();}
        public int wireMode() {return leftHand?2:1;}
        public boolean safeForWorld() { return transform.owner.get(ValueLayout.JAVA_INT,336) != 0; }
        public boolean trustedNormals() {return transform.owner.get(ValueLayout.JAVA_INT,(leftHand?104:0)+100)!=0;}
        /** Compatibility projection only; native GUI batches use ownerAddress instead. */
        public float[] modelTransform() {return transform.owner.asSlice(leftHand?104:0,64).toArray(ValueLayout.JAVA_FLOAT);}
        public float[] normalTransform() {return transform.owner.asSlice((leftHand?104:0)+64,36).toArray(ValueLayout.JAVA_FLOAT);}
    }
    /** One copied parent; its local authored transform remains Rust-owned. */
    public static final class WorldPose {
        private final Capture capture;
        private final float[] model, normal;
        private final int properties;
        private final boolean trusted, applyAuthored, decal;
        private WorldPose(Capture capture,float[] model,float[] normal,int properties,boolean trusted,boolean applyAuthored,boolean decal) {
            this.capture=capture;this.model=model;this.normal=normal;this.properties=properties;
            this.trusted=trusted;this.applyAuthored=applyAuthored;this.decal=decal;
        }
        public static WorldPose capture(Capture capture,net.blaze3d.vertex.PoseStack.Pose parent,boolean applyAuthored) {
            if(capture==null || !capture.safeForWorld() || parent==null) return null;
            int properties=parent.pose().properties();
            if((properties&2)==0 || (properties&~31)!=0) return null;
            float[] model=parent.pose().get(new float[16]);float[] normal=parent.normal().get(new float[9]);
            for(float v:model)if(!Float.isFinite(v)||Math.abs(v)>1.0e10F)return null;
            for(float v:normal)if(!Float.isFinite(v)||Math.abs(v)>1.0e10F)return null;
            return new WorldPose(capture,model,normal,properties,parent.trustedNormals,applyAuthored,false);
        }
        public WorldPose withoutDecalFoil() {return decal?new WorldPose(capture,model,normal,properties,trusted,applyAuthored,false):this;}
        public WorldPose withDecalFoil() {return new WorldPose(capture,model,normal,properties,trusted,applyAuthored,true);}
        public long ownerAddress(){return capture.ownerAddress();}
        public int wireMode(){return capture.wireMode()+(applyAuthored?0:2);}
        public int parentProperties(){return properties;}
        public boolean parentTrusted(){return trusted;}
        public boolean decalFoil(){return decal;}
        public boolean firstPerson(){return !applyAuthored;}
        public void encodeParentModel(MemorySegment output,long offset){MemorySegment.copy(model,0,output,ValueLayout.JAVA_FLOAT,offset,16);}
        public void encodeParentNormal(MemorySegment output,long offset){MemorySegment.copy(normal,0,output,ValueLayout.JAVA_FLOAT,offset,9);}
        private MemorySegment resolve(Arena arena) {
            MemorySegment output=arena.allocate(104,4);
            try {
                int status=(int)RESOLVE.invokeExact(capture.transform.owner,wireMode(),properties,
                    arena.allocateFrom(ValueLayout.JAVA_FLOAT,model),arena.allocateFrom(ValueLayout.JAVA_FLOAT,normal),trusted?1:0,output);
                if(status!=0)throw new IllegalStateException("Native item CPU projection rejected");
                return output;
            }catch(RuntimeException|Error failure){throw failure;}
            catch(Throwable failure){throw new IllegalStateException("Cannot project native world item pose",failure);}
            finally{java.lang.ref.Reference.reachabilityFence(this);}
        }
        /** Diagnostics/compatibility only; native encoding uses the copied parent. */
        public float[] modelTransform(){try(Arena arena=Arena.ofConfined()){return resolve(arena).asSlice(0,64).toArray(ValueLayout.JAVA_FLOAT);}}
        public net.vulkanic.bridge.VulkanicGalBridge.WorldDecalFoilRecord decalProjection(){
            try(Arena arena=Arena.ofConfined()){
                MemorySegment pose=resolve(arena);
                return new net.vulkanic.bridge.VulkanicGalBridge.WorldDecalFoilRecord(firstPerson(),pose.get(ValueLayout.JAVA_INT,100)!=0,
                    pose.asSlice(0,64).toArray(ValueLayout.JAVA_FLOAT),pose.asSlice(64,36).toArray(ValueLayout.JAVA_FLOAT));
            }
        }
    }

}

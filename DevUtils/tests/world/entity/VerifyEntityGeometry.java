import java.io.*;
import java.lang.foreign.Arena;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;

/** Runs each repository's actual movement and ordinary renderer policy separately. */
public class VerifyEntityGeometry {
    static class Fixture extends Entity {
        int moves, lifeMode;
        boolean overrideReason;
        List<String> life;
        void revive(){unsetRemoved();}
        @Override public RemovalReason getRemovalReason(){return overrideReason ? RemovalReason.UNLOADED_TO_CHUNK : super.getRemovalReason();}
        @Override public void stopRiding(){
            life.add("stop:"+isRemoved()+":"+getRemovalReason());
            if(lifeMode==1){lifeMode=0;revive();}
            if(lifeMode==2)throw new IllegalStateException("stop");
        }
        @Override public void onRemoval(RemovalReason requested){
            life.add("onRemoval:"+requested+":"+isRemoved()+":"+getRemovalReason());
            if(lifeMode==3)throw new IllegalStateException("onRemoval");
        }
        Fixture(){super(EntityType.PIG,null);}
        @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
        @Override protected void readAdditionalSaveData(ValueInput input){}
        @Override protected void addAdditionalSaveData(ValueOutput output){}
        @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){return false;}
        @Override public boolean shouldRender(double x,double y,double z){return true;}
    }
    static class Renderer extends EntityRenderer<Fixture,EntityRenderState> {
        Renderer(){super(null);}
        @Override public EntityRenderState createRenderState(){return new EntityRenderState();}
    }
    static class CallbackRenderer extends Renderer {
        int mode;
        @Override public AABB getBoundingBoxForCulling(Fixture entity) {
            entity.moves++;AABB captured=entity.getBoundingBox();
            if(mode==1)entity.setBoundingBox(new AABB(100,100,100,101,101,101));
            if(mode==2)return captured.inflate(0.5);
            if(mode==3)entity.setPosRaw(10,20,30);
            return captured;
        }
    }
    static Object allocate(Class<?> c)throws Exception {
        var type=Class.forName("sun.misc.Unsafe");var f=type.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.getMethod("allocateInstance",Class.class).invoke(f.get(null),c);
    }
    static void field(Fixture entity,String name,Object value)throws Exception {
        var f=Entity.class.getDeclaredField(name);f.setAccessible(true);f.set(entity,value);
    }
    static Fixture fixture(boolean current,Object owner,Arena scope)throws Exception {
        var e=(Fixture)allocate(Fixture.class);field(e,"position",Vec3.ZERO);field(e,"blockPosition",BlockPos.ZERO);field(e,"chunkPosition",ChunkPos.ZERO);field(e,"firstTick",true);
        if(current) {
            var body=owner.getClass().getMethod("create",Arena.class,double.class,double.class,double.class,AABB.class)
                .invoke(owner,scope,0.0,0.0,0.0,new AABB(-.2,-.2,-.2,.2,.2,.2));
            field(e,"nativeGeometry",body);
        }
        field(e,"levelCallback",new EntityInLevelCallback(){
            public void onMove(){e.moves++;}
            public void onRemove(Entity.RemovalReason reason){}
        });
        e.life=new ArrayList<>();field(e,"passengers",com.google.common.collect.ImmutableList.of());
        e.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));return e;
    }
    static void state(DataOutputStream out,Fixture e)throws IOException {
        out.writeLong(Double.doubleToLongBits(e.getX()));out.writeLong(Double.doubleToLongBits(e.getY()));out.writeLong(Double.doubleToLongBits(e.getZ()));
        out.writeInt(e.blockPosition().getX());out.writeInt(e.blockPosition().getY());out.writeInt(e.blockPosition().getZ());
        out.writeInt(e.chunkPosition().x);out.writeInt(e.chunkPosition().z);out.writeInt(e.moves);
    }
    static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static void main(String[] args)throws Exception {
        boolean current=args[0].equals("current");net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Object owner=current?Class.forName("net.minecraft.world.entity.NativeEntityGeometry").getConstructor().newInstance():null;
        var bytes=new ByteArrayOutputStream();int cases=0;
        try(var scope=Arena.ofConfined();var out=new DataOutputStream(bytes)) {
            var entity=fixture(current,owner,scope);var random=new Random(0x4547454f);
            double[] edges={0.0,-0.0,-.1,-16.001,16,32,3e7,-3e7,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(double x:edges)for(double y:edges)for(double z:edges) {
                entity.setPosRaw(x,y,z);state(out,entity);entity.setPosRaw(x,y,z);state(out,entity);cases++;
            }
            field(entity,"levelCallback",new EntityInLevelCallback(){
                public void onMove(){entity.moves++;if(entity.getX()==17)entity.setPosRaw(-17,65,32);}
                public void onRemove(Entity.RemovalReason reason){}
            });entity.setPosRaw(17,64,32);state(out,entity);cases++;
            field(entity,"levelCallback",new EntityInLevelCallback(){
                public void onMove(){entity.moves++;throw new IllegalStateException("callback");}
                public void onRemove(Entity.RemovalReason reason){}
            });
            try{entity.setPosRaw(33,64,32);throw new AssertionError("Callback did not throw");}catch(IllegalStateException expected){state(out,entity);cases++;}
            field(entity,"levelCallback",EntityInLevelCallback.NULL);
            var renderer=(Renderer)allocate(Renderer.class);
            for(int camera=0;camera<120;camera++) {
                var view=new Matrix4f().rotateXYZ(random.nextFloat()*6,random.nextFloat()*6,random.nextFloat()*6);
                var projection=new Matrix4f().perspective((float)Math.toRadians(35+random.nextInt(90)),1.7F,.05F,256);
                var frustum=new Frustum(view,projection);double x=random.nextDouble()*6e7-3e7,y=random.nextDouble()*200,z=random.nextDouble()*6e7-3e7;
                frustum.prepare(x,y,z);
                for(int i=0;i<80;i++) {
                    double a=x+random.nextGaussian()*150,b=y+random.nextGaussian()*150,c=z+random.nextGaussian()*150;
                    entity.setPosRaw(a,b,c);entity.setBoundingBox(i%17==0 ? new AABB(Double.NaN,b,c,a+2,b+2,c+2) : new AABB(a,b,c,a+random.nextDouble()*4,b+random.nextDouble()*4,c+random.nextDouble()*4));
                    out.writeBoolean(renderer.shouldRender(entity,frustum,x,y,z));state(out,entity);cases++;
                }
                frustum.offset(4);out.writeBoolean(renderer.shouldRender(entity,frustum,x,y,z));cases++;
            }
            for(int mode=0;mode<4;mode++) {
                var callback=(CallbackRenderer)allocate(CallbackRenderer.class);callback.mode=mode;
                entity.setPosRaw(0,0,0);
                entity.setBoundingBox(mode==3 ? new AABB(Double.NaN,0,0,1,1,1) : new AABB(-.2,-.2,-.2,.2,.2,.2));
                out.writeBoolean(callback.shouldRender(entity,new Frustum(new Matrix4f(),new Matrix4f()),0,0,0));state(out,entity);cases++;
            }
        }
        // Actual production lifecycle calls, including null/repeated reasons,
        // private-state semantics under getter overrides and callback prefixes.
        try(var scope=Arena.ofConfined();var out=new DataOutputStream(bytes)) {
            var reasons=new Entity.RemovalReason[6];
            System.arraycopy(Entity.RemovalReason.values(),0,reasons,1,5);
            for(int first=0;first<6;first++)for(int second=0;second<6;second++)
                for(int mode=0;mode<6;mode++)for(int override=0;override<2;override++) {
                    var entity=fixture(current,owner,scope);entity.lifeMode=mode;entity.overrideReason=override!=0;
                    field(entity,"levelCallback",new EntityInLevelCallback(){
                        public void onMove(){}
                        public void onRemove(Entity.RemovalReason requested){
                            entity.life.add("callback:"+requested+":"+entity.isRemoved()+":"+entity.getRemovalReason());
                            if(entity.lifeMode==4)throw new IllegalStateException("callback");
                            if(entity.lifeMode==5){entity.lifeMode=0;entity.revive();entity.setRemoved(Entity.RemovalReason.DISCARDED);}
                        }
                    });
                    for(int code:new int[]{first,second}) {
                        String failure="none";
                        try{entity.setRemoved(reasons[code]);}catch(RuntimeException caught){failure=caught.getClass().getName()+":"+(caught instanceof NullPointerException ? "null" : caught.getMessage());}
                        out.writeUTF(failure);out.writeBoolean(entity.isRemoved());out.writeUTF(String.valueOf(entity.getRemovalReason()));out.writeBoolean(entity.shouldBeSaved());
                        out.writeInt(entity.life.size());for(String event:entity.life)out.writeUTF(event);
                    }
                    var beforeRevival=(Renderer)allocate(Renderer.class);
                    var detachedFrustum=new Frustum(new Matrix4f(),new Matrix4f());
                    entity.setPosRaw(0,0,0);entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));
                    out.writeBoolean(beforeRevival.shouldRender(entity,detachedFrustum,0,0,0));
                    entity.setPosRaw(100,0,0);entity.setBoundingBox(new AABB(99,0,0,101,1,1));
                    out.writeBoolean(beforeRevival.shouldRender(entity,detachedFrustum,0,0,0));
                    entity.revive();out.writeBoolean(entity.isRemoved());out.writeUTF(String.valueOf(entity.getRemovalReason()));out.writeBoolean(entity.shouldBeSaved());
                    entity.setPosRaw(0,0,0);entity.setBoundingBox(new AABB(-.2,-.2,-.2,.2,.2,.2));
                    var renderer=(Renderer)allocate(Renderer.class);var frustum=new Frustum(new Matrix4f(),new Matrix4f());
                    out.writeBoolean(renderer.shouldRender(entity,frustum,0,0,0));
                    cases++;
                }
        }
        var fixture=Path.of(args[1]);
        if(current){try(var in=new GZIPInputStream(Files.newInputStream(fixture))){if(!Arrays.equals(bytes.toByteArray(),in.readAllBytes()))throw new AssertionError("Actual Frozen movement/culling outputs differ");}}
        else try(var out=new GZIPOutputStream(Files.newOutputStream(fixture))){bytes.writeTo(out);}
        System.out.println("ENTITY_CASES "+cases+" "+sha(bytes.toByteArray())+" PASS");
        var guardedClasses=new ArrayList<>(List.of("net.minecraft.world.entity.Entity","net.minecraft.client.renderer.entity.EntityRenderer","net.minecraft.client.renderer.culling.Frustum"));
        if(current)guardedClasses.addAll(List.of("net.minecraft.world.entity.NativeEntityGeometry","net.minecraft.world.entity.NativeEntityGeometry$Body"));
        for(String name:guardedClasses) {
            try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        }
        if(current) {
            Path image=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+image)))throw new AssertionError("Native image not mapped");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(image))+" "+image);
        }
    }
}

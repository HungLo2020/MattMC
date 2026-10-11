package net.minecraft.world.entity;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.AABB;

/** Per-world primary position/bounds components; no Java object or GPU address is retained. */
public final class NativeEntityGeometry {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_entity_geometry_"+name, descriptor);
    }
    private static final MethodHandle WORLD_CREATE=handle("world_create",FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle WORLD_RELEASE=handle("world_release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle WORLD_VIEW=handle("world_view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CREATE=handle("create",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,
        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle RELEASE=handle("release",FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle VIEW=handle("view",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle CHANGE=handle("change",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle TRANSFER=handle("transfer",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
    private static final MethodHandle QUERY=NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_entity_geometry_query",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,
            ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_DOUBLE),Linker.Option.critical(true));
    private static final VarHandle LONG=ValueLayout.JAVA_LONG.varHandle(), INT=ValueLayout.JAVA_INT.varHandle();
    private final MemorySegment owner,view;

    public NativeEntityGeometry() {
        try {
            var pointer=(MemorySegment)WORLD_CREATE.invokeExact();
            if(pointer.address()==0)throw new IllegalStateException("Cannot create entity geometry world");
            boolean attached=false;
            try {
                var header=(MemorySegment)WORLD_VIEW.invokeExact(pointer);var scope=Arena.ofAuto();
                owner=pointer.reinterpret(1,scope,NativeEntityGeometry::releaseWorld);attached=true;
                view=header.reinterpret(32,scope,null).asReadOnly();
            } finally {if(!attached)releaseWorld(pointer);}
        } catch(Throwable failure) {throw failure(failure);}
    }
    private static void releaseWorld(MemorySegment pointer) {
        try {WORLD_RELEASE.invokeExact(pointer);}catch(Throwable failure){throw failure(failure);}
    }
    private static void releaseBody(MemorySegment pointer) {
        try {RELEASE.invokeExact(pointer);}catch(Throwable failure){throw failure(failure);}
    }
    public long revision() {try{return (long)LONG.getAcquire(view,0L);}finally{Reference.reachabilityFence(this);}}
    public long queryStamp() {try{return (long)LONG.getAcquire(view,8L);}finally{Reference.reachabilityFence(this);}}
    public long queryRevision() {try{return (long)LONG.getAcquire(view,24L);}finally{Reference.reachabilityFence(this);}}
    public int count() {try{return (int)INT.getAcquire(view,16L);}finally{Reference.reachabilityFence(this);}}

    public Body create(double x,double y,double z,AABB bounds) {return create(Arena.ofAuto(),x,y,z,bounds);}
    /** Explicit scopes are useful for bounded temporary entities and lifecycle verification. */
    public Body create(Arena scope,double x,double y,double z,AABB bounds) {
        try {
            var pointer=(MemorySegment)CREATE.invokeExact(owner,x,y,z,bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ);
            if(pointer.address()==0)throw new IllegalStateException("Cannot create entity geometry component");
            boolean attached=false;
            try {
                var bodyView=(MemorySegment)VIEW.invokeExact(pointer);
                var bodyOwner=pointer.reinterpret(1,scope,NativeEntityGeometry::releaseBody);attached=true;
                return new Body(this,bodyOwner,bodyView.reinterpret(152,scope,null).asReadOnly());
            } finally {if(!attached)releaseBody(pointer);}
        } catch(Throwable failure) {throw failure(failure);}
        finally {Reference.reachabilityFence(this);}
    }
    /** Queries retained components once; Java reads the resulting flags from their CPU views. */
    public long query(float[] matrix,double x,double y,double z) {
        if(matrix.length!=16)throw new IllegalArgumentException("Culling matrix must have16 components");
        try {
            long stamp=(long)QUERY.invokeExact(owner,MemorySegment.ofArray(matrix),matrix.length,x,y,z);
            if(stamp==0)throw new IllegalStateException("Invalid entity geometry query");return stamp;
        } catch(Throwable failure){throw failure(failure);}
        finally{Reference.reachabilityFence(this);}
    }
    public static final class Body {
        private NativeEntityGeometry world;
        private final MemorySegment owner,view;
        private AABB cullingProjection;
        private long cullingRevision;
        private Body(NativeEntityGeometry world,MemorySegment owner,MemorySegment view) {this.world=world;this.owner=owner;this.view=view;}
        public NativeEntityGeometry world() {return world;}
        public int move(double x,double y,double z) {return change(0,x,y,z,0.0,0.0,0.0);}
        public void bounds(AABB bounds) {change(1,bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ);}
        private int change(int mode,double a,double b,double c,double d,double e,double f) {
            try {
                int flags=(int)CHANGE.invokeExact(owner,mode,a,b,c,d,e,f);
                if(flags<0)throw new IllegalStateException("Invalid entity geometry mutation");return flags;
            }catch(Throwable failure){throw failure(failure);}finally{Reference.reachabilityFence(this);}
        }
        public void transfer(NativeEntityGeometry next) {
            if(next==world)return;
            try {
                int status=(int)TRANSFER.invokeExact(owner,next.owner);
                if(status<0)throw new IllegalStateException("Invalid entity geometry world transfer");world=next;
            }catch(Throwable failure){throw failure(failure);}finally{Reference.reachabilityFence(this);Reference.reachabilityFence(next);}
        }
        public int blockX(){return integer(120);}
        public int blockY(){return integer(124);}
        public int blockZ(){return integer(128);}
        public int chunkX(){return integer(132);}
        public int chunkZ(){return integer(136);}
        public boolean visible(){return integer(140)!=0;}
        /** Immutable compatibility projection; capture before hooks may mutate the owner. */
        public AABB cullingBounds() {
            long revision;
            try {revision=(long)LONG.getAcquire(view,144L);}finally{Reference.reachabilityFence(this);}
            if(cullingProjection==null || revision!=cullingRevision) {
                cullingProjection=new AABB(culling(0),culling(1),culling(2),culling(3),culling(4),culling(5));
                cullingRevision=revision;
            }
            return cullingProjection;
        }
        private int integer(long offset){try{return (int)INT.getAcquire(view,offset);}finally{Reference.reachabilityFence(this);}}
        public double position(int axis) {if(axis<0 || axis>2)throw new IndexOutOfBoundsException(axis);return number(axis*8L);}
        public double culling(int component) {if(component<0 || component>5)throw new IndexOutOfBoundsException(component);return number(72+component*8L);}
        private double number(long offset){try{return Double.longBitsToDouble((long)LONG.getAcquire(view,offset));}finally{Reference.reachabilityFence(this);}}
    }
    private static RuntimeException failure(Throwable failure) {
        if(failure instanceof RuntimeException runtime)return runtime;
        if(failure instanceof Error error)throw error;
        return new IllegalStateException("Entity geometry native call failed",failure);
    }
}

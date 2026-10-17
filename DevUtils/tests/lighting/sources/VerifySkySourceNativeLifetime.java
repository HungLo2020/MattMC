package net.minecraft.world.level.lighting;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.LevelHeightAccessor;
/** Prepared observer: actual source AutoArena/base handle, native cursor Arc and weak store. */
public final class VerifySkySourceNativeLifetime {
 static MethodHandle h(String name,FunctionDescriptor descriptor){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_sky_source_"+name,descriptor);}
 static final MethodHandle PROBE=h("probe_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 static final MethodHandle OWNERS=h("probe_owners",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 static final MethodHandle PROBE_DROP=h("probe_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 static final MethodHandle CURSOR=h("cursor_fill_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
 static final MethodHandle STEP=h("cursor_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
 static final MethodHandle DROP=h("cursor_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 record Setup(long probe,long cursor,List<WeakReference<Object>> weak,long[] snapshot,long[] expected){}
 static Setup create(MemorySegment frame,Object[] retained)throws Throwable{
  var source=new ChunkSkyLightSources(LevelHeightAccessor.create(0,32));
  long[] seed=source.nativeSnapshot();seed[0]=6;source.nativeRestore(seed);
  var ownerField=ChunkSkyLightSources.class.getDeclaredField("nativeOwner");ownerField.setAccessible(true);Object owner=ownerField.get(source);
  var idField=owner.getClass().getDeclaredField("id");idField.setAccessible(true);long id=idField.getLong(owner);
  var segmentField=owner.getClass().getDeclaredField("owner");segmentField.setAccessible(true);Object segment=segmentField.get(owner);
  long probe=(long)PROBE.invokeExact(id);long cursor=0;
  try{
   cursor=(long)CURSOR.invokeExact(id,frame);if(cursor==0||frame.getAtIndex(ValueLayout.JAVA_LONG,0)!=3)throw new AssertionError("actual highest-section cursor request");
   if((long)OWNERS.invokeExact(probe)!=2)throw new AssertionError("base and pending must each own actual source Arc");
   long[] snapshot=source.nativeSnapshot();if(snapshot[0]!=6)throw new AssertionError("real snapshot input");
   if(retained!=null)retained[0]=source;
   return new Setup(probe,cursor,List.of(new WeakReference<>(source),new WeakReference<>(owner),new WeakReference<>(segment)),snapshot,snapshot.clone());
  }catch(Throwable failure){if(cursor!=0&&(long)OWNERS.invokeExact(probe)>0)DROP.invokeExact(cursor);PROBE_DROP.invokeExact(probe);throw failure;}
 }
 static boolean collected(Setup setup){for(var weak:setup.weak())if(weak.get()!=null)return false;return true;}
 static void run(boolean retain)throws Throwable{
  Object[] holder=retain?new Object[1]:null;
  try(Arena arena=Arena.ofConfined()){
   MemorySegment frame=arena.allocate(128,8);Setup setup=create(frame,holder);boolean cursorLive=true;
   try{
    if(retain){
     for(int i=0;i<5;i++){System.gc();Thread.sleep(20);}
     if(collected(setup)||(long)OWNERS.invokeExact(setup.probe())!=2)throw new AssertionError("retained facade control lost real source ownership");
     holder[0]=null;
    }
    long deadline=System.nanoTime()+5_000_000_000L;
    while((!collected(setup)||(long)OWNERS.invokeExact(setup.probe())!=1)&&System.nanoTime()<deadline){if((long)OWNERS.invokeExact(setup.probe())==0)throw new AssertionError("pending cursor lost actual source store before last use");System.gc();Thread.sleep(10);}
    if(!collected(setup))throw new AssertionError("VM source/owner/arena still reachable");
    if((long)OWNERS.invokeExact(setup.probe())!=1)throw new AssertionError("actual base handle did not retire while pending cursor remains");
    frame.setAtIndex(ValueLayout.JAVA_LONG,11,-1);
    if((int)STEP.invokeExact(setup.cursor(),frame)!=0||frame.getAtIndex(ValueLayout.JAVA_LONG,0)!=0)throw new AssertionError("real pending fill failed after VM facade/base retirement");
    if((long)OWNERS.invokeExact(setup.probe())!=1)throw new AssertionError("pending source ownership lost before cursor release");
    DROP.invokeExact(setup.cursor());cursorLive=false;
    if((long)OWNERS.invokeExact(setup.probe())!=0)throw new AssertionError("physical source store remains after final real owner release");
    if(!Arrays.equals(setup.snapshot(),setup.expected())||setup.snapshot()[0]!=6)throw new AssertionError("independent VM snapshot changed/retired with native source");
    System.out.println("SKY_SOURCE_LIFETIME retained-control="+retain+" VMcollected=true baseRetired=true pendingAfterGC=true finalNativeOwners=0 independentSnapshot=true");
   }finally{if(cursorLive&&(long)OWNERS.invokeExact(setup.probe())>0)DROP.invokeExact(setup.cursor());PROBE_DROP.invokeExact(setup.probe());}
  }
 }
 public static void main(String[]args)throws Throwable{run(false);run(true);}
}

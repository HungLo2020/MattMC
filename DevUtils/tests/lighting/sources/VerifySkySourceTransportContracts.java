package net.minecraft.world.level.lighting;
import java.lang.foreign.*;
import java.lang.reflect.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/** Same precompiled observer: original pre transport operations vs additive API. */
public final class VerifySkySourceTransportContracts {
 static final IOException MARKER=new IOException("original raw override failure");
 @SuppressWarnings("unchecked")static<T extends Throwable>void fail()throws T{throw(T)MARKER;}
 static final Map<String,Method> API=new HashMap<>();
 static boolean additive;
 static class Parent extends ChunkSkyLightSources {
  long[] raw={11,22,33,44};final List<String> events=new ArrayList<>();Runnable onRaw;boolean reject,nullRaw;
  Parent(){super(LevelHeightAccessor.create(0,32));}
  @Override public long[] nativeRaw(){events.add("raw");Runnable effect=onRaw;onRaw=null;if(effect!=null)effect.run();if(reject)VerifySkySourceTransportContracts.<RuntimeException>fail();return nullRaw?null:raw;}
  @Override public int nativeBits(){events.add("bits");return 6;}
 }
 // Use an inherited default API with an override declared in its parent.
 static final class Child extends Parent {}
 static Object call(ChunkSkyLightSources source,String op,Object argument,long offset)throws Throwable{
  if(additive){try{return switch(op){
   case "count","snapshot"->API.get(op).invoke(source);
   case "to","from"->API.get(op).invoke(source,(MemorySegment)argument,offset);
   case "restore","matches"->API.get(op).invoke(source,(Object)(long[])argument);
   default->throw new AssertionError(op);
  };}catch(InvocationTargetException envelope){throw envelope.getCause();}}
  // Exact original shared consumer operations, no substituted expected result.
  long[] raw=source.nativeRaw();
  switch(op){
   case "count":return raw.length;
   case "snapshot":return raw.clone();
   case "to":MemorySegment.copy(raw,0,(MemorySegment)argument,ValueLayout.JAVA_LONG,offset,raw.length);return null;
   case "from":MemorySegment.copy((MemorySegment)argument,ValueLayout.JAVA_LONG,offset,raw,0,raw.length);return null;
   case "restore":{long[] snapshot=(long[])argument;System.arraycopy(snapshot,0,raw,0,snapshot.length);return null;}
   case "matches":return Arrays.equals((long[])argument,raw);
   default:throw new AssertionError(op);
  }
 }
 static String describe(Object value){return value instanceof long[] array?Arrays.toString(array):String.valueOf(value);}
 static void custom(String mode)throws Throwable{
  Child source=new Child();String op=mode;Object argument=null;long offset=8;
  try(Arena arena=Arena.ofConfined()){
   MemorySegment memory=arena.allocate(64,8);memory.fill((byte)0);for(int i=0;i<4;i++)memory.setAtIndex(ValueLayout.JAVA_LONG,i+1,100+i);
   switch(mode){
    case "to","from"->argument=memory;
    case "restore-short"->{op="restore";argument=new long[]{9,8};}
    case "restore-empty"->{op="restore";argument=new long[0];}
    case "restore-long"->{op="restore";argument=new long[5];}
    case "restore-null"->op="restore";
    case "matches-equal"->{op="matches";argument=source.raw.clone();}
    case "matches-short"->{op="matches";argument=new long[0];}
    case "matches-null"->op="matches";
    case "to-readonly"->{op="to";argument=memory.asReadOnly();}
    case "to-bounds"->{op="to";argument=memory;offset=40;}
    case "from-bounds"->{op="from";argument=memory;offset=40;}
    case "to-unaligned"->{op="to";argument=memory;offset=1;}
    case "from-unaligned"->{op="from";argument=memory;offset=1;}
    case "to-null"->op="to";
    case "from-null"->op="from";
    case "raw-null"->{op="snapshot";source.nullRaw=true;}
    case "raw-throw"->{op="restore";source.reject=true;}
    case "raw-reenter"->{op="snapshot";source.onRaw=()->{source.events.add("nested:begin");try{call(source,"restore",new long[]{7,6},0);}catch(Throwable error){throw new AssertionError(error);}source.events.add("nested:end");};}
    case "eligibility-skip"->{source.nativeBits();source.events.add("skip");System.out.println("SKY_TRANSPORT "+mode+" events="+source.events);return;}
    case "eligibility-count"->{source.nativeBits();op="count";}
   }
   Object result=null;Throwable failure=null;try{result=call(source,op,argument,offset);}catch(Throwable error){failure=error;}
   if(mode.equals("raw-throw")&&failure!=MARKER)throw new AssertionError("original raw Throwable replaced");
   int expected=mode.equals("raw-reenter")?2:1;long calls=source.events.stream().filter("raw"::equals).count();if(calls!=expected)throw new AssertionError("raw calls "+source.events);
   if(source.events.stream().filter("bits"::equals).count()!=(mode.equals("eligibility-count")?1:0))throw new AssertionError("extra bits effect");
   if(result instanceof long[] snapshot&&snapshot==source.raw)throw new AssertionError("snapshot aliases actual raw");
   System.out.println("SKY_TRANSPORT "+mode+" result="+describe(result)+" error="+(failure==null?"none":failure.getClass().getName())+" marker="+(failure==MARKER)+" raw="+Arrays.toString(source.raw)+" memory="+Arrays.toString(memory.toArray(ValueLayout.JAVA_LONG))+" events="+source.events);
  }
 }
 static ChunkSkyLightSources canonical(){
  ChunkSkyLightSources source=new ChunkSkyLightSources(LevelHeightAccessor.create(0,32));
  for(int z=0;z<16;z++)for(int x=0;x<16;x++){var reader=new VerifySkySourcePublicUpdateContracts.Reader(source,"occlude");if(!source.update(reader,x,4,z))throw new AssertionError("setup");}
  return source;
 }
 static void canonicalTransport()throws Throwable{
  ChunkSkyLightSources source=canonical();long[] snapshot=(long[])call(source,"snapshot",null,0);int n=(int)call(source,"count",null,0);
  if(snapshot.length!=n)throw new AssertionError("wordcount");
  try(Arena arena=Arena.ofConfined()){
   MemorySegment words=arena.allocate((n+2L)*8,8);call(source,"to",words,8);Arrays.fill(snapshot,0);snapshot[0]=9;call(source,"restore",snapshot,0);
   if(!(boolean)call(source,"matches",snapshot,0))throw new AssertionError("restore/match");call(source,"from",words,8);if(source.getLowestSourceY(0,0)!=5)throw new AssertionError("copyFrom not visible");
   long[] escaped=source.nativeRaw();long[] original=escaped.clone();call(source,"restore",new long[]{0},0);if(escaped!=source.nativeRaw()||escaped[0]!=0)throw new AssertionError("escaped projection not updated");
   for(int i=1;i<n;i++)if(escaped[i]!=original[i])throw new AssertionError("short restore changed suffix");
   escaped[0]=9;if(source.getLowestSourceY(0,0)!=8)throw new AssertionError("outside raw write not consumed");
   System.out.println("SKY_TRANSPORT canonical count="+n+" lowest="+source.getLowestSourceY(0,0)+" max="+source.getHighestLowestSourceY()+" raw="+Arrays.toString(escaped)+" matchesNull="+call(source,"matches",null,0));
  }
 }
 static void canonicalErrors()throws Throwable{
  ChunkSkyLightSources source=canonical();int n=(int)call(source,"count",null,0);long[] before=(long[])call(source,"snapshot",null,0);
  try(Arena arena=Arena.ofConfined()){
   MemorySegment memory=arena.allocate((n+2L)*8,8);
   System.out.println("TRANSPORT_API additive="+additive);
  for(String mode:List.of("to-readonly","to-bounds","from-bounds","to-unaligned","from-unaligned","to-null","from-null","restore-long","restore-null","restore-empty")){
    String op=mode.startsWith("to-")?"to":mode.startsWith("from-")?"from":"restore";Object argument=memory;long offset=8;
    switch(mode){case "to-readonly"->argument=memory.asReadOnly();case "to-bounds","from-bounds"->offset=24;case "to-unaligned","from-unaligned"->offset=1;case "to-null","from-null","restore-null"->argument=null;case "restore-long"->argument=new long[n+1];case "restore-empty"->argument=new long[0];}
    Throwable failure=null;try{call(source,op,argument,offset);}catch(Throwable error){failure=error;}
    long[] after=(long[])call(source,"snapshot",null,0);if(!Arrays.equals(before,after))throw new AssertionError("failed operation changed canonical state "+mode);
    if(mode.equals("restore-empty")?(failure!=null):(failure==null))throw new AssertionError("expected error contract "+mode);
    System.out.println("SKY_TRANSPORT canonical-"+mode+" error="+(failure==null?"none":failure.getClass().getName())+" unchanged="+Arrays.equals(before,after));
   }
  }
 }
 public static void main(String[]args)throws Throwable{
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  String[] names={"count","to","from","snapshot","restore","matches"};Class<?>[][] types={{},{MemorySegment.class,long.class},{MemorySegment.class,long.class},{},{long[].class},{long[].class}};
  String[] methods={"nativeWordCount","nativeCopyTo","nativeCopyFrom","nativeSnapshot","nativeRestore","nativeMatches"};
  try{for(int i=0;i<names.length;i++)API.put(names[i],ChunkSkyLightSources.class.getMethod(methods[i],types[i]));additive=true;}catch(NoSuchMethodException old){API.clear();additive=false;}
  for(String mode:List.of("count","to","from","snapshot","restore-short","restore-empty","restore-long","restore-null","matches-equal","matches-short","matches-null","to-readonly","to-bounds","from-bounds","to-unaligned","from-unaligned","to-null","from-null","raw-null","raw-throw","raw-reenter","eligibility-skip","eligibility-count"))custom(mode);
  canonicalTransport();canonicalErrors();
 }
}

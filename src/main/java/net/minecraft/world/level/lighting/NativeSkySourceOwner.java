package net.minecraft.world.level.lighting;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.lang.ref.Reference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.apache.commons.lang3.Validate;
/** VM resources and original virtual effects; Rust owns source state and decisions. */
final class NativeSkySourceOwner {
 static MethodHandle h(String name,FunctionDescriptor type){return NativeLibraryLoader.downcallHandle("mattmc_rust","mattmc_sky_source_"+name,type);}
 static final MethodHandle NEW=h("owner_from_bounds",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
 static final MethodHandle RELEASE=h("owner_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 static final MethodHandle META=h("owner_metadata",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 static final MethodHandle GET=h("owner_get",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
 static final MethodHandle HIGH=h("owner_highest",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG));
 static final MethodHandle SET=h("owner_set",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
 static final MethodHandle FILL=h("owner_fill",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT));
 static final MethodHandle WORDS=h("owner_words",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
 static final MethodHandle CURSOR=h("cursor_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
 static final MethodHandle FILL_CURSOR=h("cursor_fill_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
 static final MethodHandle STEP=h("cursor_step",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS));
 static final MethodHandle BULK_NEW=h("bulk_new",FunctionDescriptor.of(ValueLayout.JAVA_LONG,ValueLayout.JAVA_LONG,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
 static final MethodHandle BULK_SECTION=h("bulk_section",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_LONG,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
 static final MethodHandle BULK_DROP=h("bulk_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 static final MethodHandle DROP=h("cursor_release",FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
 static final class Scratch {final MemorySegment words=Arena.ofAuto().allocate(128L*Long.BYTES,Long.BYTES);}
 static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
 static final class Release implements Consumer<MemorySegment>{
  static final VarHandle ID;
  static{try{ID=MethodHandles.lookup().findVarHandle(Release.class,"id",long.class);}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
  volatile long id;Release(long id){this.id=id;}
  public void accept(MemorySegment unused){long actual=(long)ID.getAndSet(this,0L);if(actual!=0)try{RELEASE.invokeExact(actual);}catch(Throwable e){throw fail(e);}}
 }
 final long id;final MemorySegment owner;private long[] escaped;
 NativeSkySourceOwner(int low,int high){
  long created=0;
  try(Arena errors=Arena.ofConfined()){MemorySegment failure=errors.allocate(16,8);created=(long)NEW.invokeExact(low,high,failure);if(created==0){if(failure.getAtIndex(ValueLayout.JAVA_LONG,0)!=0)error((int)failure.getAtIndex(ValueLayout.JAVA_LONG,0),(int)failure.getAtIndex(ValueLayout.JAVA_LONG,1));throw new OutOfMemoryError("Native sky source allocation failed");}id=created;
   Release cleanup;try{cleanup=new Release(created);}catch(Throwable e){RELEASE.invokeExact(created);throw e;}
   try{owner=MemorySegment.ofAddress(created).reinterpret(1,Arena.ofAuto(),cleanup).asReadOnly();}catch(Throwable e){cleanup.accept(MemorySegment.NULL);throw e;}
  }catch(Throwable e){throw fail(e);}
 }
 @SuppressWarnings("unchecked")static<T extends Throwable>RuntimeException fail(Throwable e)throws T{throw(T)e;}
 int decode(long result){int kind=(int)(result>>>32),value=(int)result;if(kind!=0)error(kind,value);return value;}
 void error(int kind,int value){
  switch(kind){case 1->Validate.inclusiveBetween(0L,255L,value);case 2->Validate.inclusiveBetween(0L,(1L<<bits())-1,value);case 3->Validate.inclusiveBetween(1L,32L,value);case 5->throw new OutOfMemoryError("Native sky source allocation failed");}
  throw new IllegalStateException("Invalid native sky source operation "+kind+":"+value);
 }
 long metadata(){try{return(long)META.invokeExact(id);}catch(Throwable e){throw fail(e);}finally{Reference.reachabilityFence(this);}}
 int bits(){return(int)metadata();}int wordCount(){return(int)(metadata()>>>32);}
 void transfer(MemorySegment words,int len,int mode){try{int result=(int)WORDS.invokeExact(id,words,len,mode);if(result!=0)error(-result,0);}catch(Throwable e){throw fail(e);}finally{Reference.reachabilityFence(this);}}
 void importEscaped(){if(escaped!=null){MemorySegment scratch=SCRATCH.get().words;MemorySegment.copy(escaped,0,scratch,ValueLayout.JAVA_LONG,0,escaped.length);transfer(scratch,escaped.length,1);}}
 void exportEscaped(){if(escaped!=null){MemorySegment scratch=SCRATCH.get().words;transfer(scratch,escaped.length,0);MemorySegment.copy(scratch,ValueLayout.JAVA_LONG,0,escaped,0,escaped.length);}}
 long[] raw(){if(escaped==null)escaped=snapshot();return escaped;}
 int get(int x,int z){try{importEscaped();return decode((long)GET.invokeExact(id,x,z));}catch(Throwable e){throw fail(e);}finally{Reference.reachabilityFence(this);}}
 int highest(){try{importEscaped();return decode((long)HIGH.invokeExact(id));}catch(Throwable e){throw fail(e);}finally{Reference.reachabilityFence(this);}}
 void set(int index,int y){try{importEscaped();decode((long)SET.invokeExact(id,index,y));exportEscaped();}catch(Throwable e){throw fail(e);}finally{Reference.reachabilityFence(this);}}
 void fill(int y){try{importEscaped();decode((long)FILL.invokeExact(id,y));exportEscaped();}catch(Throwable e){throw fail(e);}finally{Reference.reachabilityFence(this);}}
 long[] snapshot(){try{importEscaped();int n=wordCount();long[] copy=new long[n];MemorySegment scratch=SCRATCH.get().words;transfer(scratch,n,0);MemorySegment.copy(scratch,ValueLayout.JAVA_LONG,0,copy,0,n);return copy;}finally{Reference.reachabilityFence(this);}}
 void copyTo(MemorySegment target,long offset){try{importEscaped();int n=wordCount();MemorySegment scratch=SCRATCH.get().words;transfer(scratch,n,0);MemorySegment.copy(scratch,ValueLayout.JAVA_LONG,0,target,ValueLayout.JAVA_LONG,offset,n);}finally{Reference.reachabilityFence(this);}}
 void copyFrom(MemorySegment input,long offset){try{importEscaped();int n=wordCount();MemorySegment scratch=SCRATCH.get().words;MemorySegment.copy(input,ValueLayout.JAVA_LONG,offset,scratch,ValueLayout.JAVA_LONG,0,n);transfer(scratch,n,1);exportEscaped();}finally{Reference.reachabilityFence(this);}}
 void restore(long[] snapshot){try{importEscaped();int n=wordCount();if(snapshot.length>n){System.arraycopy(snapshot,0,new long[n],0,snapshot.length);throw new AssertionError("oversize copy accepted");}MemorySegment scratch=SCRATCH.get().words;MemorySegment.copy(snapshot,0,scratch,ValueLayout.JAVA_LONG,0,snapshot.length);transfer(scratch,snapshot.length,2);exportEscaped();}finally{Reference.reachabilityFence(this);}}
 boolean matches(long[] snapshot){try{importEscaped();if(snapshot==null||snapshot.length!=wordCount())return false;return java.util.Arrays.equals(snapshot,snapshot());}finally{Reference.reachabilityFence(this);}}
 static long read(MemorySegment frame,int word){return frame.getAtIndex(ValueLayout.JAVA_LONG,word);}
 static void write(MemorySegment frame,int word,long value){frame.setAtIndex(ValueLayout.JAVA_LONG,word,value);}
 boolean update(BlockGetter getter,int x,int y,int z,BlockPos.MutableBlockPos first,BlockPos.MutableBlockPos second){return run(getter,null,x,y,z,first,second,false);}
 void fillFrom(ChunkAccess chunk,BlockPos.MutableBlockPos first,BlockPos.MutableBlockPos second){
  if(!bulkFill(chunk,first,second))run(null,chunk,0,0,0,first,second,true);
 }
 private boolean bulkFill(ChunkAccess chunk,BlockPos.MutableBlockPos first,BlockPos.MutableBlockPos second){
  long bulk=0;Throwable primary=null;
  try(Arena arena=Arena.ofConfined()){
   var resources=net.minecraft.world.level.chunk.NativeSkySourceSections.prepare(chunk);
   if(resources==null)return false;
   importEscaped();var frame=arena.allocate(128,8);
   bulk=(long)BULK_NEW.invokeExact(id,resources.minY(),resources.height(),resources.sections().length,resources.highest(),frame);
   if(bulk==0){if(read(frame,0)!=0)return false;exportEscaped();return true;}
   var words=arena.allocate(2048L*8,8);var ids=arena.allocate(257L*4,4);var header=arena.allocate(16,4);
   while(read(frame,0)!=0){
    int index=(int)read(frame,1);
    var section=net.minecraft.world.level.chunk.NativeSkySourceSections.export(resources.sections()[index],words,ids,header);
    if(section==null)return false;
    MemorySegment palette=section.global()?MemorySegment.NULL:ids;
    int status=(int)BULK_SECTION.invokeExact(bulk,words,section.words(),palette,section.count(),section.bits(),frame);
    if(status!=0)return false;
   }
   if(read(frame,1)!=0){int lastY=(int)read(frame,2);first.set(15,lastY,15);second.set(15,lastY-1,15);}
   exportEscaped();return true;
  }catch(Throwable e){primary=e;throw fail(e);}
  finally{try{if(bulk!=0)BULK_DROP.invokeExact(bulk);}catch(Throwable cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw fail(cleanup);}finally{Reference.reachabilityFence(this);Reference.reachabilityFence(chunk);Reference.reachabilityFence(first);Reference.reachabilityFence(second);}}
 }
 private boolean run(BlockGetter getter,ChunkAccess chunk,int x,int y,int z,BlockPos.MutableBlockPos first,BlockPos.MutableBlockPos second,boolean filling){
  long cursor=0;Throwable primary=null;
  try(Arena arena=Arena.ofConfined()){
   importEscaped();MemorySegment frame=arena.allocate(16L*Long.BYTES,Long.BYTES);cursor=filling?(long)FILL_CURSOR.invokeExact(id,frame):(long)CURSOR.invokeExact(id,x,y,z,frame);
   if(cursor==0){error(-(int)read(frame,0),(int)read(frame,1));throw new OutOfMemoryError("Native sky cursor allocation failed");}
   BlockState[] states=new BlockState[2];VoxelShape[] shapes=new VoxelShape[3];LevelChunkSection section=null;
   for(;;){int op=(int)read(frame,0);if(op<0)error(-op,(int)read(frame,1));if(op==0){exportEscaped();return read(frame,1)!=0;}
    exportEscaped();int value=0;
    try{
     switch(op){
      case 1->{if(read(frame,3)!=0)states[0]=states[1];BlockPos.MutableBlockPos position=read(frame,1)==0?first:second;if(read(frame,4)!=0)position.set((int)read(frame,5),(int)read(frame,6),(int)read(frame,7));states[(int)read(frame,2)]=getter.getBlockState(position);}
      case 2->{if(read(frame,1)!=0)states[0]=states[1];first.set((int)read(frame,5),(int)read(frame,6),(int)read(frame,7));second.set((int)read(frame,8),(int)read(frame,9),(int)read(frame,10));if(read(frame,2)!=0)states[0]=Blocks.AIR.defaultBlockState();}
      case 3->value=chunk.getHighestFilledSectionIndex();
      case 4->{if(read(frame,2)!=0)states[0]=Blocks.AIR.defaultBlockState();value=chunk.getSectionYFromSectionIndex((int)read(frame,1));}
      case 5->section=chunk.getSection((int)read(frame,1));
      case 6->value=section.hasOnlyAir()?1:0;
      case 7->states[1]=section.getBlockState((int)read(frame,1),(int)read(frame,2),(int)read(frame,3));
      case 16->value=states[1].getLightBlock();case 17->value=states[0].canOcclude()?1:0;case 18->value=states[0].useShapeForLightOcclusion()?1:0;
      case 19->shapes[0]=states[0].getFaceOcclusionShape(Direction.DOWN);case 20->shapes[0]=Shapes.empty();
      case 21->value=states[1].canOcclude()?1:0;case 22->value=states[1].useShapeForLightOcclusion()?1:0;
      case 23->shapes[1]=states[1].getFaceOcclusionShape(Direction.UP);case 24->shapes[1]=Shapes.empty();
      case 25->value=shapes[0]==Shapes.block()?1:0;case 26->value=shapes[1]==Shapes.block()?1:0;
      case 27->value=shapes[0].isEmpty()?1:0;case 28->value=shapes[1].isEmpty()?1:0;
      case 29->shapes[2]=Shapes.joinUnoptimized(shapes[0],shapes[1],BooleanOp.OR);
      case 30->value=Shapes.joinIsNotEmpty(Shapes.block(),shapes[2],BooleanOp.ONLY_FIRST)?1:0;
      default->throw new IllegalStateException("Unknown native sky source effect "+op);
     }
    }catch(Throwable effect){try{importEscaped();}catch(Throwable cleanup){effect.addSuppressed(cleanup);}throw effect;}
    importEscaped();write(frame,11,value);write(frame,5,first.getX());write(frame,6,first.getY());write(frame,7,first.getZ());write(frame,8,second.getX());write(frame,9,second.getY());write(frame,10,second.getZ());
    int status=(int)STEP.invokeExact(cursor,frame);if(status!=0)error(-status,(int)read(frame,1));
   }
  }catch(Throwable e){primary=e;throw fail(e);}
  finally{try{if(cursor!=0)DROP.invokeExact(cursor);}catch(Throwable cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw fail(cleanup);}finally{Reference.reachabilityFence(this);Reference.reachabilityFence(first);Reference.reachabilityFence(second);}}
 }
}

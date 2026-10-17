import com.google.gson.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;

/** Invokes the actual untouched Frozen queue. No copied expected algorithm. */
public final class GenerateFrozenChunkTasksOracle {
    static final Class<?>[] PINNED = {ChunkTaskPriorityQueue.class, ChunkTaskPriorityQueue.TasksForChunk.class,
        ChunkLevel.class, ChunkPos.class, Long2ObjectLinkedOpenHashMap.class};
    static String hash(Class<?> type) throws Exception {
        try(var in=type.getResourceAsStream(type.getName().substring(type.getPackageName().length()+1)+".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
        }
    }
    record Task(int id) implements Runnable { public void run() {} }
    record Op(int action,long pos,int a,int b,int token) {}
    static long pos(int x,int z) { return ChunkPos.asLong(x,z); }
    static List<Op> operations(int seed,int count,int levels) {
        var ops=new ArrayList<Op>();var random=new Random(seed);
        long[] positions={0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,pos(-12,47),pos(7309,-7309),pos(Integer.MIN_VALUE,Integer.MAX_VALUE)};
        int token=1;
        // Existing destination appends, same-level move changes chunk order,
        // invalid moves remove a source, null/duplicate tasks and release(false).
        ops.add(new Op(0,0,3,0,token++));ops.add(new Op(0,1,3,0,token++));ops.add(new Op(0,0,3,0,token++));
        ops.add(new Op(1,0,3,3,0));ops.add(new Op(3,0,0,0,0));
        ops.add(new Op(0,1,4,0,token++));ops.add(new Op(1,1,4,-1,0));
        ops.add(new Op(0,0,0,0,0));ops.add(new Op(0,0,0,0,0));ops.add(new Op(2,0,0,0,0));ops.add(new Op(3,0,0,0,0));
        for(int i=0;i<count;i++) {
            long position=i%3==0?positions[random.nextInt(positions.length)]:pos(random.nextInt(161)-80,random.nextInt(161)-80);
            int action=random.nextInt(10);int old=random.nextInt(levels+4)-2,newLevel=random.nextInt(levels+4)-2;
            if(action<5)ops.add(new Op(0,position,old,0,random.nextInt(15)==0?0:token++));
            else if(action<7)ops.add(new Op(1,position,old,newLevel,0));
            else if(action<9)ops.add(new Op(2,position,random.nextBoolean()?1:0,0,0));
            else ops.add(new Op(3,0,0,0,0));
        }
        // Exercise growth, repeated task identity, destination append and all levels.
        for(int i=0;i<600;i++)ops.add(new Op(0,pos(i,seed),i%levels,0,token++));
        for(int i=0;i<levels;i++)ops.add(new Op(0,positions[i%positions.length],i,0,17));
        for(int i=0;i<levels;i++)ops.add(new Op(1,positions[i%positions.length],i,levels-1,0));
        for(int i=0;i<1000+count;i++)ops.add(new Op(3,0,0,0,0));
        return ops;
    }
    public static void main(String[] args) throws Exception {
        var pins=new JsonObject();for(var type:PINNED)pins.addProperty(type.getName(),hash(type));
        if(args[0].equals("--pins")){Files.writeString(Path.of(args[1]),pins.toString());return;}
        var expected=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        if(!pins.equals(expected))throw new IllegalStateException("Frozen class pins changed");
        Method submit=ChunkTaskPriorityQueue.class.getDeclaredMethod("submit",Runnable.class,long.class,int.class);
        Method resort=ChunkTaskPriorityQueue.class.getDeclaredMethod("resortChunkTasks",int.class,ChunkPos.class,int.class);
        Method release=ChunkTaskPriorityQueue.class.getDeclaredMethod("release",long.class,boolean.class);
        Field buckets=ChunkTaskPriorityQueue.class.getDeclaredField("queuesPerPriority");
        for(var method:List.of(submit,resort,release))method.setAccessible(true);buckets.setAccessible(true);
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        int levels=ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT,total=0;
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(args[0]))))) {
            out.writeInt(0x43545131);out.writeInt(PINNED.length);for(var type:PINNED){out.writeUTF(type.getName());out.writeUTF(hash(type));}
            out.writeUTF("Actual Frozen server chunk-task queue; synthetic CPU histories, FIFO moves, release, exceptions and null tasks; no server/performance claim");
            out.writeInt(levels);out.writeInt(8);
            for(int seed=0;seed<8;seed++) {
                var queue=new ChunkTaskPriorityQueue("frozen");var tasks=new HashMap<Integer,Task>();var ops=operations(seed,1500,levels);out.writeInt(ops.size());
                for(var op:ops) {
                    String exception="";ChunkTaskPriorityQueue.TasksForChunk result=null;
                    try {
                        switch(op.action) {
                            case 0->submit.invoke(queue,op.token==0?null:tasks.computeIfAbsent(op.token,Task::new),op.pos,op.a);
                            case 1->resort.invoke(queue,op.a,new ChunkPos(op.pos),op.b);
                            case 2->release.invoke(queue,op.pos,op.a!=0);
                            case 3->result=queue.pop();
                        }
                    }catch(InvocationTargetException e){exception=e.getCause().getClass().getName();}
                    int pending=0;for(var bucket:(List<?>)buckets.get(queue))for(var list:((Long2ObjectLinkedOpenHashMap<?>)bucket).values())pending+=((List<?>)list).size();
                    out.writeInt(op.action);out.writeLong(op.pos);out.writeInt(op.a);out.writeInt(op.b);out.writeInt(op.token);
                    out.writeUTF(exception);out.writeBoolean(queue.hasWork());out.writeUTF(queue.toString());out.writeInt(pending);
                    out.writeBoolean(result!=null);if(result!=null){out.writeLong(result.chunkPos());out.writeInt(result.tasks().size());for(var task:result.tasks())out.writeInt(task==null?0:((Task)task).id);}
                    total++;
                }
            }
        }
        System.out.println("Recorded actual Frozen operations="+total+" levels="+levels);
    }
}

package threadedlighting;

import com.google.gson.GsonBuilder;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Does not retain engines, chunks or callable rows. No timing claim. */
public final class ThreadedLightingServerAudit {
    static final AtomicLong entered=new AtomicLong(),completed=new AtomicLong(),peakPending=new AtomicLong();
    static final AtomicBoolean nativeOwner=new AtomicBoolean();
    static final AtomicReference<String> error=new AtomicReference<>();
    static volatile Field tasksField;
    public static void enter(Object engine) {
        entered.incrementAndGet();
        try {
            Field field=tasksField;
            if(field==null){field=engine.getClass().getDeclaredField("lightTasks");field.setAccessible(true);tasksField=field;}
            Object tasks=field.get(engine);long pending;
            if(tasks instanceof Collection<?> rows)pending=rows.size();
            else {
                if(!tasks.getClass().getName().equals("net.minecraft.server.level.NativeLightTasks"))throw new AssertionError("Unexpected task owner");
                nativeOwner.set(true);Method method=tasks.getClass().getDeclaredMethod("pending");method.setAccessible(true);pending=((Number)method.invoke(tasks)).longValue();
            }
            peakPending.accumulateAndGet(pending,Math::max);
        }catch(Throwable failure){error.compareAndSet(null,failure.toString());}
    }
    public static void complete(){completed.incrementAndGet();}
    public static void initialize() {
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try {
                var result=new LinkedHashMap<String,Object>();result.put("entered",entered.get());result.put("completed",completed.get());
                result.put("peakPending",peakPending.get());result.put("nativeOwner",nativeOwner.get());result.put("observerError",error.get());
                boolean expect=Boolean.getBoolean("threadedLight.expectNative");
                result.put("passed",entered.get()>0&&completed.get()>0&&peakPending.get()>0&&error.get()==null&&nativeOwner.get()==expect);
                Path target=Path.of(System.getProperty("playerLoading.output"),"threaded-lighting.json");
                Files.writeString(target,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
            }catch(Throwable failure){failure.printStackTrace();}
        },"threaded-light-audit-output"));
    }
}

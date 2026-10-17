package fallen;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
/** Retry only the two noisy integrated oak rows, using the original body. */
public final class FallenOakRetry {
    public static void run(MinecraftServer server){
        try{
            FallenCost.THREADS.setThreadAllocatedMemoryEnabled(true);
            var rows=new ArrayList<Map<String,Object>>();
            for(String mode:List.of("placed","native"))rows.add(FallenCost.one(server.overworld(),"oak",mode));
            var output=Path.of(System.getProperty("audit.output"));Files.writeString(output.resolve("costs.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":2}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of(System.getProperty("audit.output"),"failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}

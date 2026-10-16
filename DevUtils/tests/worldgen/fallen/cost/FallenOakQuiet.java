package fallen;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
/** Only the interrupted/noisy oak configured row; original timed body reused. */
public final class FallenOakQuiet {
    public static void run(MinecraftServer server){
        try{
            FallenCost.THREADS.setThreadAllocatedMemoryEnabled(true);
            var row=FallenCost.one(server.overworld(),"oak","configured");
            var output=Path.of(System.getProperty("audit.output"));
            Files.writeString(output.resolve("costs.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(List.of(row)));
            Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":1}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of(System.getProperty("audit.output"),"failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}

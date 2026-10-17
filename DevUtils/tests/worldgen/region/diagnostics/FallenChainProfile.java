package fallen;

import java.nio.file.*;
import java.util.*;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.server.MinecraftServer;

/** One bounded attribution, using the original complete-call helper unchanged.
 * Instrumented times are diagnostic and never acceptance measurements. */
public final class FallenChainProfile {
    public static void run(MinecraftServer server){
        var output=Path.of(System.getProperty("audit.output"));
        try{
            FallenCost.THREADS.setThreadAllocatedMemoryEnabled(true);
            System.setProperty("fallen.cost.warmups","1024");
            System.setProperty("fallen.cost.groups","8");System.setProperty("fallen.cost.batch","32");
            FallenCost.one(server.overworld(),"chain_jdk","native");
            System.setProperty("fallen.cost.warmups","0");
            System.setProperty("fallen.cost.groups","128");System.setProperty("fallen.cost.batch","128");
            try(var recording=new Recording(Configuration.getConfiguration("profile"))){
                recording.start();
                var row=FallenCost.one(server.overworld(),"chain_jdk","native");
                recording.stop();recording.dump(output.resolve("samples.jfr"));
                Files.writeString(output.resolve("instrumented-only.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(Map.of("scope","JFR attribution only; includes fixture/reset/cold/bind as well as unchanged complete-call body. No acceptance latency or native CPU apportionment.","row",row)));
            }
            Files.writeString(output.resolve("complete.json"),"{\"success\":true,\"cases\":1,\"acceptanceTiming\":false}");
        }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(output.resolve("failed.txt"),failure.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}

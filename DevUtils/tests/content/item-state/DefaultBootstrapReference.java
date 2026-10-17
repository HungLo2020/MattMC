import java.nio.file.*;
import java.util.*;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.*;

/** Foreign component maps were usable before server/registry bootstrap. */
public final class DefaultBootstrapReference {
    public static void main(String[] args)throws Exception {
        var key=DataComponentType.<Integer>builder().persistent(Codec.INT).build();
        var map=DataComponentMap.builder().set(key,7).build();
        var rows=new ArrayList<String>();rows.add("before="+map.get(key)+"/size="+map.size()+"/contains="+map.keySet().contains(key));
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        rows.add("after="+map.get(key)+"/size="+map.size()+"/contains="+map.keySet().contains(key));
        Files.write(Path.of(args[0]),rows);System.out.println("DEFAULT_BOOTSTRAP rows="+rows.size());
    }
}

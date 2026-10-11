import com.seibel.distanthorizons.core.util.objects.quadTree.*;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.DhFrustumBounds;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.vulkanic.world.NativeDhVisibility;

/** Replay through the same mutation hooks as the real LodQuadTree. */
public class VerifyNativeDhVisibility {
    static class Tree extends QuadTree<VerifyDhVisibility.Item> {
        final NativeDhVisibility owner;
        final QuadTreeMutationObserver<VerifyDhVisibility.Item> observer=new QuadTreeMutationObserver<>() {
            public void added(long key){owner.event(0,key,0,0);}
            public void valueChanged(long key,VerifyDhVisibility.Item old,VerifyDhVisibility.Item item) {
                long token=owner.event(1,key,item==null?0:1,0);
                if(item!=null) {item.nativeToken=token;owner.state(key,token,item.enabled,item.generation,0L);}
            }
            public void childrenRemoved(long key){owner.event(2,key,0,0);}
            public void removed(long key){owner.event(3,key,0,0);}
        };
        Tree(int d,int x,int z){super(d,new DhBlockPos2D(x,z),(byte)6);owner=new NativeDhVisibility(treeRootDetailLevel,treeLeafDetailLevel,d,x,z);}
        protected QuadTreeMutationObserver<VerifyDhVisibility.Item> mutationObserver(){return owner==null?null:observer;}
    }
    static class Scene extends VerifyDhVisibility.Scene {
        final NativeDhVisibility owner;
        Scene(int d,int x,int z){super(new Tree(d,x,z));owner=((Tree)tree).owner;}
        public void changed(VerifyDhVisibility.Item item){owner.state(item.key,item.nativeToken,item.enabled,item.generation,0L);}
        public void moved(int x,int z){owner.event(4,0L,x,z);}
        public long[] walk(DhFrustumBounds f,boolean cull){return owner.inspect(cull?f.rustVisibilityPlanes():null,f.worldMinY,f.worldMaxY);}
        public void close(){owner.close();if(!Arrays.equals(owner.inspect(null,0F,1F),new long[4]))throw new AssertionError("Closed topology remains live");}
    }
    public static void main(String[] args)throws Exception {
        VerifyDhVisibility.classImages();
        try(var input=new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))))) {
            if(input.readInt()!=0x44565431)throw new AssertionError("Bad oracle");
            int total=VerifyDhVisibility.run(Scene::new,(scene,f,c,s,n)-> {
                int length=input.readInt();long[] expected=new long[length];for(int i=0;i<length;i++)expected[i]=input.readLong();
                long[] actual=scene.walk(f,c);
                if(!Arrays.equals(expected,actual))throw new AssertionError("Scenario "+s+" frame "+n+" expected="+Arrays.toString(expected)+" actual="+Arrays.toString(actual));
            });
            if(input.readInt()!=-1 || input.read()!=-1)throw new AssertionError("Unconsumed oracle");
            System.out.println("NATIVE_FRAMES "+total+" PASS");
            var maps=Files.readAllLines(Path.of("/proc/self/maps")).stream().filter(line->line.contains("mattmc_rust-linux-x64.so"))
                .map(line->line.split("\\s+",6)[5]).distinct().toList();
            for(String path:maps) System.out.println("NATIVE_IMAGE "+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(path))))+" "+path);
        }
    }
}

import com.seibel.distanthorizons.core.util.objects.quadTree.*;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.DhFrustumBounds;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.joml.Matrix4f;

/** Actual Frozen iterator/frustum oracle; this class uses only Frozen APIs. */
public class VerifyDhVisibility {
    public static final class Item {
        public final long key;
        public boolean enabled;
        public long generation;
        public long nativeToken;
        Item(long key,boolean enabled,long generation) {this.key=key;this.enabled=enabled;this.generation=generation;}
    }
    public static class Scene implements AutoCloseable {
        public final QuadTree<Item> tree;
        Scene(QuadTree<Item> tree) {this.tree=tree;}
        public void changed(Item item) {}
        public void moved(int x,int z) {}
        public long[] walk(DhFrustumBounds frustum,boolean cull) {
            long[] counts=new long[4];List<Long> pairs=new ArrayList<>();
            var it=tree.nodeIteratorWithStoppingFilter(node -> {
                if(node==null)return true;
                if(node.value==null || !cull)return false;
                var b=DhSectionPos.getSectionBBoxPos(node.sectionPos);
                if(frustum.intersects(b.getMinX().toBlockWidth(),b.getMinZ().toBlockWidth(),b.getBlockWidth(),b.detailLevel))return false;
                counts[1]++;return true;
            });
            while(it.hasNext()) {
                var node=it.next();counts[0]++;Item item=node.value;
                if(item==null)continue;
                if(!item.enabled)counts[2]++;
                else if(item.generation<0)counts[3]++;
                else if(item.generation>0) {pairs.add(node.sectionPos);pairs.add(item.generation);}
            }
            long[] out=Arrays.copyOf(counts,4+pairs.size());for(int i=0;i<pairs.size();i++)out[i+4]=pairs.get(i);return out;
        }
        public void close() {}
    }
    public interface Factory {Scene create(int diameter,int x,int z);}
    public interface Frame {void accept(Scene scene,DhFrustumBounds frustum,boolean cull,int scenario,int frame)throws Exception;}
    public static int run(Factory factory,Frame consume)throws Exception {
        int total=0;
        for(int scenario=0;scenario<32;scenario++) {
            var random=new Random(923781L+scenario);int diameter=new int[]{256,1024,4096,16384}[scenario%4];
            int x=new int[]{0,-4097,30000000,-30000000}[scenario/8],z=-x+137;
            try(Scene scene=factory.create(diameter,x,z)) {
                var tree=scene.tree;
                for(int op=0;op<800;op++) put(scene,random,diameter,x,z);
                var stale=tree.getNode(DhSectionPos.encode(tree.treeRootDetailLevel,x>>tree.treeRootDetailLevel,z>>tree.treeRootDetailLevel));
                var frustum=new DhFrustumBounds();
                for(int frame=0;frame<40;frame++) {
                    for(int op=0;op<9;op++) put(scene,random,diameter,x,z);
                    var nodes=tree.nodeIterator();List<QuadNode<Item>> changed=new ArrayList<>();
                    while(nodes.hasNext()) {var n=nodes.next();if(n.value!=null && random.nextInt(7)==0)changed.add(n);}
                    for(var node:changed) {
                        var item=node.value;item.enabled=!item.enabled;item.generation=(frame%5==0)?0:frame+200;
                        scene.changed(item);
                    }
                    if(frame%7==3) {
                        x+=diameter/2;z-=diameter/3;
                        tree.setCenterBlockPos(new DhBlockPos2D(x,z),item -> {});scene.moved(x,z);
                    }
                    if(frame==18 && stale!=null) {
                        // A detached public node reference is still usable, but cannot
                        // resurrect its former tree membership in a retained consumer.
                        tree.setCenterBlockPos(new DhBlockPos2D(x+diameter*4,z),item -> {});scene.moved(x+diameter*4,z);
                        stale.setValue(stale.sectionPos,new Item(stale.sectionPos,true,9876));
                        tree.setCenterBlockPos(new DhBlockPos2D(x,z),item -> {});scene.moved(x,z);
                    }
                    if(frame%9==4) {
                        var it=tree.nodeIterator();while(it.hasNext()) {var n=it.next();if(n.getTotalChildCount()>0) {n.deleteAllChildren();break;}}
                    }
                    Matrix4f m=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,0.1F,diameter*2F)
                        .rotateX(0.1F).rotateY(frame*0.21F).translate(-x,-100F,-z);
                    frustum.update(-64,320,new Mat4f(m));consume.accept(scene,frustum,frame%4!=0,scenario,frame);total++;
                }
            }
        }
        return total;
    }
    static void put(Scene scene,Random random,int diameter,int x,int z) {
        var tree=scene.tree;byte d=(byte)(6+random.nextInt(tree.treeRootDetailLevel-5));int width=1<<d;
        int px=(x+random.nextInt(diameter)-diameter/2)>>d,pz=(z+random.nextInt(diameter)-diameter/2)>>d;
        long key=DhSectionPos.encode(d,px,pz);
        if(!tree.isSectionPosInBounds(key))return;
        Item item=random.nextInt(8)==0?null:new Item(key,random.nextBoolean(),random.nextInt(6)-1);
        tree.setValue(key,item);
    }
    public static void classImages()throws Exception {
        for(Class<?> type:new Class<?>[]{QuadTree.class,QuadNode.class,
            com.seibel.distanthorizons.core.util.objects.quadTree.iterators.QuadTreeNodeIterator.class,
            com.seibel.distanthorizons.core.util.gridList.MovableGridRingList.class,DhFrustumBounds.class}) {
            try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) {
                System.out.println("CLASS "+type.getName()+" "+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
            }
        }
    }
    public static void main(String[] args)throws Exception {
        classImages();
        Path path=Path.of(args[0]);
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(0x44565431);
            int total=run((d,x,z)->new Scene(new QuadTree<>(d,new DhBlockPos2D(x,z),(byte)6)),(scene,f,c,s,n)-> {
                long[] result=scene.walk(f,c);out.writeInt(result.length);for(long value:result)out.writeLong(value);
            });out.writeInt(-1);System.out.println("FROZEN_FRAMES "+total);
        }
    }
}

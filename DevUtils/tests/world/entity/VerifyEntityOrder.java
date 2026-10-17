import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.storage.*;

/** Record actual Frozen ticking membership and compare the native production consumer. */
public final class VerifyEntityOrder {
    static final class Member extends Entity {
        int label, key;
        Runnable beforeId;
        Member(int label, int key) { super(EntityType.PIG, null); this.label=label; this.key=key; }
        @Override public int getId() {
            Runnable hook=beforeId;
            beforeId=null;
            if (hook!=null) hook.run();
            return key;
        }
        @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
        @Override protected void readAdditionalSaveData(ValueInput in) {}
        @Override protected void addAdditionalSaveData(ValueOutput out) {}
        @Override public boolean hurtServer(ServerLevel l, DamageSource s, float a) { return false; }
    }
    static void state(DataOutputStream out, EntityTickList list, Member[] members) throws IOException {
        ArrayList<Member> sequence=new ArrayList<>();
        list.forEach(e -> sequence.add((Member)e));
        out.writeInt(sequence.size());
        for (Member m:sequence) { out.writeInt(m.label); out.writeInt(m.key); }
        for (Member m:members) out.writeBoolean(list.contains(m));
    }
    @SuppressWarnings("unchecked")
    static <E extends Throwable> void sneaky(Throwable t) throws E { throw (E)t; }
    static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        int cases=0;
        try (DataOutputStream out=new DataOutputStream(bytes)) {
            EntityTickList list=new EntityTickList();
            list.forEach(null);
            out.writeBoolean(true); cases++;
            Member[] m=new Member[32];
            int[] edges={0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,7,8,9,10};
            for (int i=0;i<m.length;i++) m[i]=new Member(i,edges[i%edges.length]);
            state(out,list,m); cases++;
            for (Member e:m) { list.add(e); state(out,list,m); cases++; }
            try { list.forEach(null); throw new AssertionError("Nonempty null consumer accepted"); }
            catch (NullPointerException expected) { out.writeBoolean(true); }
            state(out,list,m); cases++;
            Throwable callbackFailure=new IOException("checked callback");
            try { list.forEach(e -> VerifyEntityOrder.<RuntimeException>sneaky(callbackFailure)); throw new AssertionError("Checked callback did not throw"); }
            catch (Throwable caught) { if(caught!=callbackFailure) throw new AssertionError("Callback identity lost",caught); out.writeUTF(caught.getMessage()); }
            state(out,list,m); cases++;
            Throwable getterFailure=new IOException("checked getter");
            m[0].beforeId=() -> VerifyEntityOrder.<RuntimeException>sneaky(getterFailure);
            try { list.forEach(e -> list.add(m[0])); throw new AssertionError("Checked getter did not throw"); }
            catch (Throwable caught) { if(caught!=getterFailure) throw new AssertionError("Getter identity lost",caught); out.writeUTF(caught.getMessage()); }
            state(out,list,m); cases++;
            // Replacements preserve insertion position; IDs can change while an
            // older map key still names the same physical Java entity.
            m[31].key=77; list.add(m[31]); state(out,list,m); cases++;
            list.remove(m[31]); state(out,list,m); cases++;
            // Nested/throwing callbacks must release iteration ownership.
            try { list.forEach(e -> list.forEach(x -> {})); out.writeBoolean(false); }
            catch (UnsupportedOperationException e) { out.writeBoolean(true); out.writeUTF(e.getMessage()); }
            state(out,list,m); cases++;
            try { list.forEach(e -> { list.remove(e); throw new IllegalStateException("callback"); }); }
            catch (IllegalStateException e) { out.writeUTF(e.getMessage()); }
            state(out,list,m); cases++;
            // The mutation preparation occurs before the virtual getId prefix.
            m[0].beforeId=() -> { list.remove(m[1]); list.add(m[2]); };
            boolean[] firstId={true};
            list.forEach(e -> { if (firstId[0]) { firstId[0]=false; list.add(m[0]); } });
            state(out,list,m); cases++;
            Random random=new Random(0x454e544954494553L);
            for (int i=0;i<4096;i++) {
                Member a=m[random.nextInt(m.length)], b=m[random.nextInt(m.length)];
                int op=random.nextInt(6);
                out.writeInt(op);
                if (op==0) list.add(a);
                else if (op==1) list.remove(a);
                else if (op==2) out.writeBoolean(list.contains(a));
                else if (op==3) { a.key=random.nextBoolean()?edges[random.nextInt(edges.length)]:random.nextInt(); list.add(a); }
                else if (op==4) {
                    ArrayList<Integer> observed=new ArrayList<>();
                    boolean[] first={true};
                    list.forEach(e -> {
                        observed.add(((Member)e).label);
                        if (first[0]) { first[0]=false; list.remove(a); list.add(b); }
                    });
                    out.writeInt(observed.size()); for (int label:observed) out.writeInt(label);
                } else {
                    a.beforeId=() -> { list.remove(b); list.add(m[0]); };
                    out.writeBoolean(list.contains(a));
                }
                state(out,list,m); cases++;
            }
            // Java captures the map receiver before virtual getId. The getter
            // can switch active/passive maps while that receiver stays captured.
            EntityTickList receiverRead=new EntityTickList();
            Member ra=new Member(900,900), rb=new Member(901,901), probe=new Member(902,900);
            receiverRead.add(ra); receiverRead.add(rb);
            boolean[] queried={false};
            receiverRead.forEach(e -> {
                if(!queried[0]) {
                    queried[0]=true;
                    probe.beforeId=() -> receiverRead.remove(ra);
                    try { out.writeBoolean(receiverRead.contains(probe)); }
                    catch(IOException failure) { throw new UncheckedIOException(failure); }
                }
            });
            state(out,receiverRead,new Member[]{ra,rb,probe}); cases++;
            for(int rounds:new int[]{1,2}) {
                EntityTickList receiverWrite=new EntityTickList();
                Member wa=new Member(910,910), wb=new Member(911,911), wc=new Member(912,912), wd=new Member(913,913);
                receiverWrite.add(wa);
                wc.beforeId=() -> {
                    receiverWrite.forEach(e -> receiverWrite.add(wb));
                    if(rounds==2) receiverWrite.forEach(e -> receiverWrite.add(wd));
                };
                receiverWrite.add(wc);
                state(out,receiverWrite,new Member[]{wa,wb,wc,wd}); cases++;
            }
            EntityTickList receiverRemove=new EntityTickList();
            Member da=new Member(920,920), db=new Member(921,921);
            receiverRemove.add(da);
            da.beforeId=() -> receiverRemove.forEach(e -> receiverRemove.add(db));
            receiverRemove.remove(da);
            state(out,receiverRemove,new Member[]{da,db}); cases++;
            // Force several native pages and Java binding-array growth while
            // the old snapshot is still being consumed.
            EntityTickList growing=new EntityTickList();
            Member[] many=new Member[512];
            for(int i=0;i<many.length;i++) many[i]=new Member(i,i);
            for(int i=0;i<128;i++) growing.add(many[i]);
            ArrayList<Integer> seen=new ArrayList<>();
            growing.forEach(e -> {
                seen.add(((Member)e).label);
                if(seen.size()==1) {
                    for(int i=0;i<128;i++) growing.remove(many[i]);
                    for(int i=128;i<many.length;i++) growing.add(many[i]);
                }
            });
            out.writeInt(seen.size()); for(int label:seen) out.writeInt(label);
            state(out,growing,many); cases++;
        }
        Files.write(Path.of(args[0]),bytes.toByteArray());
        System.out.println("ORDER_CASES "+cases+" bytes="+bytes.size()+" sha="+sha(bytes.toByteArray()));
        boolean current=args[1].equals("current");
        List<String> classes=new ArrayList<>(List.of("net.minecraft.world.level.entity.EntityTickList","net.minecraft.world.entity.Entity"));
        if(current) classes.add("net.minecraft.world.level.entity.NativeEntityOrder");
        for(String name:classes) {
            try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")) {
                System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));
            }
        }
        if(current) {
            Path image=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+image)))
                throw new AssertionError("Native image not mapped");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(image))+" "+image);
        }
    }
}

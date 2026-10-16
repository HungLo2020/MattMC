package net.minecraft.server.level;

import java.lang.management.ManagementFactory;
import java.util.IdentityHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Whole public update/import paths. Layer creation/decode and observations are
 * deliberately outside the retained-layer import window. No copied policy. */
public final class BenchmarkLightCoordinator {
    static volatile long sink;
    final VerifyThreadedLighting.Fixture fixture;
    final LevelLightEngine direct;
    final String mode;
    final SectionPos[] sections;
    final DataLayer[][] inputs;
    final IdentityHashMap<DataLayer,String> published=new IdentityHashMap<>();
    long checksum;
    int phase;

    BenchmarkLightCoordinator(String mode,int count) throws Exception {
        this.mode=mode;
        fixture=new VerifyThreadedLighting.Fixture(false,true);
        fixture.initialize();fixture.assertReady();
        direct=new LevelLightEngine(fixture.world,true,true);
        for(var chunk:fixture.world.chunks.values()) {
            for(int y=0;y<4;y++)if(!chunk.getSection(y).hasOnlyAir())
                direct.updateSectionStatus(SectionPos.of(chunk.getPos(),y),false);
        }
        direct.runLightUpdates();
        for(var chunk:fixture.world.chunks.values()) {
            direct.setLightEnabled(chunk.getPos(),true);
            direct.propagateLightSources(chunk.getPos());
        }
        direct.runLightUpdates();
        for(var chunk:fixture.world.chunks.values()) {
            var pos=chunk.getPos();
            if(!direct.lightOnInColumn(SectionPos.getZeroNode(pos.x,pos.z))
                ||direct.getLayerListener(LightLayer.BLOCK).getLightValue(new BlockPos(pos.x*16+8,24,pos.z*16+8))!=15
                ||direct.getLayerListener(LightLayer.SKY).getLightValue(new BlockPos(pos.x*16+8,63,pos.z*16+8))!=15)
                throw new AssertionError("Direct fixture is not enabled and lit");
        }
        sections=new SectionPos[count];inputs=new DataLayer[2][count*2];
        for(int i=0;i<count;i++) {
            if(i>=18)throw new IllegalArgumentException("At most18 distinct nonempty fixture sections");
            sections[i]=SectionPos.of(i%3-1,1+i/9,(i/3)%3-1);
            for(int variant=0;variant<2;variant++)for(int channel=0;channel<2;channel++)
                inputs[variant][i*2+channel]=new DataLayer(variant==0?3:7);
        }
        if(!mode.equals("direct-empty")&&!mode.equals("direct-seed")&&!mode.equals("queued-retained"))throw new IllegalArgumentException(mode);
        observe();
        for(var kind:LightLayer.values()) {
            Object engine=direct.getLayerListener(kind);
            try {
                Object owner=VerifyThreadedLighting.field(engine,"lightCoordinator");
                if(owner==null)throw new AssertionError("Canonical engine did not admit native coordinator");
                System.out.println("NATIVE_ADMISSION "+kind+" true");
            } catch(NoSuchFieldException baseline) {System.out.println("NATIVE_ADMISSION "+kind+" unavailable");}
        }
    }

    private final ChunkPos seedPos=new ChunkPos(0,0);

    void cycle() {
        if(mode.equals("direct-empty")) {
            checksum+=direct.runLightUpdates();
        } else if(mode.equals("direct-seed")) {
            direct.propagateLightSources(seedPos);
            checksum+=direct.runLightUpdates();
        } else {
            int variant=phase++&1;
            for(int i=0;i<sections.length;i++) {
                fixture.engine.queueSectionData(LightLayer.BLOCK,sections[i],inputs[variant][i*2]);
                fixture.engine.queueSectionData(LightLayer.SKY,sections[i],inputs[variant][i*2+1]);
            }
            fixture.engine.waitForPendingTasks(0,0).thenRun(()->checksum=Long.rotateLeft(checksum,7)^sections.length);
            fixture.transport();fixture.engine.tryScheduleUpdate();fixture.transport();
        }
    }

    long observe() throws Exception {
        if(fixture.pending()!=0||fixture.scheduled()||fixture.engine.hasLightWork()||direct.hasLightWork())
            throw new AssertionError("Unfinished workload");
        fixture.snapshot("untimed import observation");
        for(var entry:published.entrySet())if(!VerifyThreadedLighting.layer(entry.getKey()).equals(entry.getValue()))
            throw new AssertionError("Direct published layer mutated");
        published.clear();
        for(var kind:LightLayer.values()) {
            Object storage=VerifyThreadedLighting.field(direct.getLayerListener(kind),"storage");
            var visible=VerifyThreadedLighting.map(VerifyThreadedLighting.field(storage,"visibleSectionData"));
            for(var layer:visible.values())if(layer!=null)published.put(layer,VerifyThreadedLighting.layer(layer));
        }
        long result=checksum;
        if(mode.equals("queued-retained")&&phase>0)for(int i=0;i<sections.length;i++)for(var kind:LightLayer.values()) {
            DataLayer actual=fixture.engine.getLayerListener(kind).getDataLayerData(sections[i]);
            DataLayer expected=inputs[(phase-1)&1][i*2+(kind==LightLayer.BLOCK?0:1)];
            if(actual!=expected||actual.get(0,0,0)!=expected.get(0,0,0))throw new AssertionError("Import identity/value lost");
            result=Long.rotateLeft(result,3)^actual.get(0,0,0);
        }
        sink=result;return result;
    }

    void sample(int steps,int index,String prefix,java.lang.management.CompilationMXBean compilation,
                com.sun.management.ThreadMXBean memory,long thread) throws Exception {
        checksum=0;
        long compiled=compilation.getTotalCompilationTime(),allocated=memory.getThreadAllocatedBytes(thread);
        long dispatch=fixture.dispatch.executed,light=fixture.light.executed;
        long began=System.nanoTime();for(int i=0;i<steps;i++)cycle();long elapsed=System.nanoTime()-began;
        long bytes=memory.getThreadAllocatedBytes(thread)-allocated,jit=compilation.getTotalCompilationTime()-compiled;
        long check=observe();
        String status=java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/status"));
        String rss=status.lines().filter(s->s.startsWith("VmRSS:")).findFirst().orElse("VmRSS: unavailable");
        String swap=status.lines().filter(s->s.startsWith("VmSwap:")).findFirst().orElse("VmSwap: unavailable");
        System.out.println(prefix+" "+index+" "+elapsed+" "+bytes+" "+jit+" "+check+" "+(fixture.dispatch.executed-dispatch)+" "+(fixture.light.executed-light));
        System.out.println("RESOURCE "+prefix+" "+index+" 0 0 "+rss+" "+swap);
    }

    public static void main(String[] args) throws Exception {
        VerifyThreadedLighting.pinClasses();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        int count=Integer.parseInt(args[1]),steps=Integer.parseInt(args[2]),samples=Integer.parseInt(args[3]);
        var compilation=ManagementFactory.getCompilationMXBean();
        var memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();memory.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId(),start=System.nanoTime();
        var benchmark=new BenchmarkLightCoordinator(args[0],count);
        try {
            System.out.println("COLD "+(System.nanoTime()-start)+" 0");
            System.out.println("ENGINE_CONSTRUCTION "+benchmark.fixture.engineConstructionNs);
            int quiet=0,warm=0;
            while(warm<32||quiet<12) {
                if(warm>=512)throw new AssertionError("Compiler did not settle");
                long before=compilation.getTotalCompilationTime();
                benchmark.sample(steps,warm,"WARM_SAMPLE",compilation,memory,thread);
                quiet=before==compilation.getTotalCompilationTime()?quiet+1:0;warm++;
            }
            System.out.println("WARM "+warm);
            for(int i=0;i<samples;i++)benchmark.sample(steps,i,"SAMPLE",compilation,memory,thread);
        } finally {benchmark.fixture.close();}
    }
}

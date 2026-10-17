package net.minecraft.world.level.biome;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

/** Actual parent/temperature-child owners through VM failure, reentry and GC. */
@Isolated
class NativeBiomeWeatherTest {
    @BeforeAll static void bootstrap(){BiomeWeatherContractTest.bootstrap();}
    @Test void genuineSeaFailureRetiresTheParentBeforeAnyTemperatureChild() {
        var biome=BiomeWeatherContractTest.biome(false,0);var level=mock(LevelReader.class);
        var failure=new RuntimeException("actual sea");when(level.getSeaLevel()).thenThrow(failure);
        long[] parent=NativeBiomeWeather.statistics(),child=NativeBiomeTemperature.statistics();
        assertSame(failure,assertThrows(RuntimeException.class,()->biome.shouldSnow(level,null)));
        long[] after=NativeBiomeWeather.statistics();assertEquals(parent[0],after[0]);assertEquals(parent[1]+1,after[1]);assertEquals(parent[2]+1,after[2]);
        assertEquals(child[1],NativeBiomeTemperature.statistics()[1]);assertEquals(child[6],NativeBiomeTemperature.statistics()[6]);
    }
    @Test void laterWorldFailureKeepsTheCompletedTemperatureCommitAndRetiresBothFrames() {
        var f=new BiomeWeatherContractTest.Fixture(true,0);var failure=new RuntimeException("actual light");
        doAnswer(i->{f.trace.add("light");throw failure;}).when(f.level).getBrightness(eq(LightLayer.BLOCK),same(f.pos));
        long[] parent=NativeBiomeWeather.statistics(),child=NativeBiomeTemperature.statistics();f.trace.clear();
        assertSame(failure,assertThrows(RuntimeException.class,()->f.b.shouldFreeze(f.level,f.pos,false)));
        assertEquals(java.util.List.of("sea","key","y","y","inside","light"),f.trace);
        assertEquals(parent[0],NativeBiomeWeather.statistics()[0]);assertEquals(child[6],NativeBiomeTemperature.statistics()[6]);
        assertEquals(child[5]+1,NativeBiomeTemperature.statistics()[5]);f.trace.clear();
        assertFalse(f.b.warmEnoughToRain(f.pos,300));assertEquals(java.util.List.of("key"),f.trace);
    }
    @SuppressWarnings("unchecked") static ThreadLocal<NativeBiomeTemperature> cache(Biome biome)throws Exception {
        var field=Biome.class.getDeclaredField("temperatureCache");field.setAccessible(true);return (ThreadLocal<NativeBiomeTemperature>)field.get(biome);
    }
    @Test void nativeChildSurvivesActualFacadeCollectionInsideItsGenuineCoordinateCallback()throws Exception {
        for(int i=0;i<20;i++){System.gc();Thread.sleep(10);}
        var biome=BiomeWeatherContractTest.biome(true,0);var local=cache(biome);var weak=new WeakReference<>(local.get());
        long[] parent=NativeBiomeWeather.statistics(),child=NativeBiomeTemperature.statistics();int[] reads={0};
        var pos=new BlockPos(7,70,11){
            @Override public long asLong(){return BlockPos.asLong(7,70,11);}
            @Override public int getY(){
                if(++reads[0]==1){
                    assertEquals(parent[0]+1,NativeBiomeWeather.statistics()[0]);assertEquals(child[6]+1,NativeBiomeTemperature.statistics()[6]);local.remove();
                    try {
                        for(int i=0;i<120&&weak.get()!=null;i++){System.gc();Thread.sleep(20);}
                        assertNull(weak.get(),"actual Java cache facade is still rooted during the child");
                        for(int i=0;i<10;i++){System.gc();Thread.sleep(20);}
                    }catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}
                    assertTrue(NativeBiomeTemperature.statistics()[0]>=1,"native child lost its actual cache during GC");
                } else assertEquals(child[6],NativeBiomeTemperature.statistics()[6],"finished child survived into later world effects");
                return 70;
            }
        };
        var level=mock(LevelReader.class);when(level.getSeaLevel()).thenReturn(63);when(level.isInsideBuildHeight(anyInt())).thenReturn(false);
        assertFalse(biome.shouldFreeze(level,pos,false));assertEquals(2,reads[0]);
        assertEquals(child[5]+1,NativeBiomeTemperature.statistics()[5],"actual child never committed its cache after facade collection");
        assertEquals(parent[0],NativeBiomeWeather.statistics()[0]);assertEquals(child[6],NativeBiomeTemperature.statistics()[6]);
    }
}

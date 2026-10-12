import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.fog.*;
import net.minecraft.client.renderer.fog.environment.*;
import net.minecraft.core.*;
import net.minecraft.hooks.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;
import sun.misc.Unsafe;

/** Actual built-in producers under controlled semantic getters; unchanged Frozen is the oracle. */
public final class VerifyFogPolicy {
    static final List<String> events = new ArrayList<>();
    static int cases;
    static float rain, thunder, time, sun, vision, blend, ticks, onset;
    static int sky, water, light, duration, cloudRange, effectMask;
    static boolean spectator, closer, precipitation, infinite, sunset;
    static Vec3 sampled = Vec3.ZERO, position = Vec3.ZERO;
    static Vector3f look = new Vector3f(1, 0, 0);
    static FogType medium = FogType.NONE;
    static RuntimeException visionFailure, biomeFailure;
    static String floats(float... values) {
        var out = new ArrayList<String>();
        for (float value : values) out.add(Integer.toHexString(Float.floatToIntBits(value)));
        return out.toString();
    }
    static String data(FogData d) {
        return floats(d.environmentalStart, d.renderDistanceStart, d.environmentalEnd,
            d.renderDistanceEnd, d.skyEnd, d.cloudEnd);
    }
    static FogData seeded() {
        var d = new FogData();
        d.environmentalStart=11; d.renderDistanceStart=12; d.environmentalEnd=13;
        d.renderDistanceEnd=14; d.skyEnd=15; d.cloudEnd=16;
        return d;
    }
    static void call(PrintWriter out, String label, Runnable operation) {
        out.print(label + ":");
        try { operation.run(); out.print("ok"); }
        catch (Throwable t) { out.print(t.getClass().getName() + ":" + t.getMessage()); }
        out.println(" events=" + events); events.clear(); cases++;
    }
    static String effect(Object value) {
        return value == MobEffects.BLINDNESS ? "blind" : value == MobEffects.DARKNESS ? "dark" :
            value == MobEffects.NIGHT_VISION ? "night" : value == MobEffects.FIRE_RESISTANCE ? "fire" : "other";
    }
    static int bit(Object value) {
        return value == MobEffects.BLINDNESS ? 1 : value == MobEffects.DARKNESS ? 2 :
            value == MobEffects.NIGHT_VISION ? 4 : value == MobEffects.FIRE_RESISTANCE ? 8 : 0;
    }
    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var level = mock(ClientLevel.class);
        var camera = mock(Camera.class);
        var local = mock(LocalPlayer.class);
        var plain = mock(Entity.class);
        var tracker = mock(DeltaTracker.class);
        var biome = mock(Biome.class);
        @SuppressWarnings("unchecked") Holder<Biome> holder = mock(Holder.Reference.class);
        var effects = mock(DimensionSpecialEffects.class);
        var instance = mock(MobEffectInstance.class);
        var levelData = mock(ClientLevel.ClientLevelData.class);
        when(holder.value()).thenAnswer(c -> {events.add("biome.value"); return biome;});
        when(holder.is(any(net.minecraft.tags.TagKey.class))).thenAnswer(c -> {events.add("biome.tag"); return closer;});
        when(biome.getWaterFogColor()).thenAnswer(c -> {events.add("water-color"); return water;});
        when(biome.hasPrecipitation()).thenAnswer(c -> {events.add("precipitation"); return precipitation;});
        when(level.getBiome(any())).thenAnswer(c -> {events.add("biome"); if(biomeFailure!=null)throw biomeFailure; return holder;});
        when(level.getBiomeManager()).thenReturn(new BiomeManager((x,y,z)->holder, 0));
        when(level.getTimeOfDay(anyFloat())).thenAnswer(c -> {events.add("time:"+floats((float)c.getArgument(0))); return time;});
        when(level.getSunAngle(anyFloat())).thenAnswer(c -> {events.add("sun"); return sun;});
        when(level.getRainLevel(anyFloat())).thenAnswer(c -> {events.add("rain:"+floats((float)c.getArgument(0))); return rain;});
        when(level.getThunderLevel(anyFloat())).thenAnswer(c -> {events.add("thunder"); return thunder;});
        when(level.getSkyColor(any(),anyFloat())).thenAnswer(c -> {events.add("sky"); return sky;});
        when(level.effects()).thenAnswer(c -> {events.add("effects"); return effects;});
        when(level.getMinY()).thenAnswer(c -> {events.add("min-y"); return -64;});
        when(level.getLevelData()).thenReturn(levelData);
        when(levelData.voidDarknessOnsetRange()).thenAnswer(c -> {events.add("void-onset"); return onset;});
        when(effects.getBrightnessDependentFogColor(any(),anyFloat())).thenAnswer(c -> {events.add("brightness:"+floats((float)c.getArgument(1))); return c.getArgument(0);});
        when(effects.isSunriseOrSunset(anyFloat())).thenAnswer(c -> {events.add("sunset?"); return sunset;});
        when(effects.getSunriseOrSunsetColor(anyFloat())).thenAnswer(c -> {events.add("sunset-color"); return 0x7faaccee;});
        when(camera.getPosition()).thenAnswer(c -> {events.add("position"); return position;});
        when(camera.getBlockPosition()).thenAnswer(c -> {events.add("block-position"); return BlockPos.ZERO;});
        when(camera.getLookVector()).thenAnswer(c -> {events.add("look"); return look;});
        when(camera.getFluidInCamera()).thenAnswer(c -> {events.add("medium"); return medium;});
        when(camera.getEntity()).thenReturn(local);
        when(local.isSpectator()).thenAnswer(c -> {events.add("spectator"); return spectator;});
        when(plain.isSpectator()).thenAnswer(c -> {events.add("spectator"); return spectator;});
        when(local.hasEffect(any())).thenAnswer(c -> {events.add("has:"+effect(c.getArgument(0))); return (effectMask & bit(c.getArgument(0))) != 0;});
        when(local.getEffect(any())).thenAnswer(c -> {events.add("effect:"+effect(c.getArgument(0))); return (effectMask & bit(c.getArgument(0))) != 0 ? instance : null;});
        when(local.getWaterVision()).thenAnswer(c -> {events.add("vision"); if(visionFailure!=null)throw visionFailure; return vision;});
        when(instance.getDuration()).thenAnswer(c -> {events.add("duration"); return duration;});
        when(instance.isInfiniteDuration()).thenAnswer(c -> {events.add("infinite"); return infinite;});
        when(instance.endsWithin(anyInt())).thenAnswer(c -> {events.add("ends:"+c.getArgument(0)); return !infinite && duration <= (int)c.getArgument(0);});
        when(instance.getBlendFactor(any(),anyFloat())).thenAnswer(c -> {events.add("blend:"+floats((float)c.getArgument(1))); return blend;});
        when(tracker.getGameTimeDeltaPartialTick(false)).thenAnswer(c -> {events.add("partial"); return .25F;});
        when(tracker.getGameTimeDeltaTicks()).thenAnswer(c -> {events.add("ticks"); return ticks;});
        var lightEngine=mock(net.minecraft.world.level.lighting.LevelLightEngine.class);
        var skyListener=mock(net.minecraft.world.level.lighting.LayerLightEventListener.class);
        when(level.getLightEngine()).thenReturn(lightEngine);
        when(lightEngine.getLayerListener(net.minecraft.world.level.LightLayer.SKY)).thenReturn(skyListener);
        when(skyListener.getLightValue(any())).thenAnswer(c -> {events.add("sky-light"); return light;});
        var minecraft=mock(Minecraft.class); var options=mock(Options.class);
        var optionsField=Minecraft.class.getDeclaredField("options");optionsField.setAccessible(true);optionsField.set(minecraft,options);
        @SuppressWarnings("unchecked") OptionInstance<Integer> cloudOption=mock(OptionInstance.class);
        when(options.cloudRange()).thenAnswer(c -> {events.add("cloud-option"); return cloudOption;});
        when(cloudOption.get()).thenAnswer(c -> {events.add("cloud-range"); return cloudRange;});
        FogColorHooks hook=new FogColorHooks(){
            public Vec3 sampleFogColor(BiomeManager m,Vec3 p,net.minecraft.util.CubicSampler.Vec3Fetcher fetch){events.add("fog-hook");return sampled;}
        };
        AtomicLong clock=new AtomicLong(); var previousClock=Util.timeSource;
        Util.timeSource=()->clock.get()*1_000_000L;
        StringWriter output=new StringWriter();
        try(var hooks=mockStatic(HookRegistry.class);var mc=mockStatic(Minecraft.class);var out=new PrintWriter(output)){
            hooks.when(HookRegistry::getFogColorHooks).thenReturn(List.of(hook));
            mc.when(Minecraft::getInstance).thenReturn(minecraft);
            var air=new DimensionOrBossFogEnvironment();
            var waterEnvironment=new WaterFogEnvironment();
            var atmosphere=new AtmosphericFogEnvironment();
            FogEnvironment[] environments={new LavaFogEnvironment(),new PowderedSnowFogEnvironment(),
                waterEnvironment,air,new BlindnessFogEnvironment(),new DarknessFogEnvironment(),atmosphere};
            float[] edges={-1,-0F,0,.1F,.25F,.5F,.999F,1,2,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
            Random random=new Random(0x464f47504f4c4943L);
            for(int i=0;i<2048;i++){
                int count=i;rain=edges[i%edges.length];thunder=edges[(i/3)%edges.length];
                time=edges[(i/5)%edges.length];sun=edges[(i/7)%edges.length];sunset=(i&1)!=0;
                sampled=new Vec3(random.nextFloat()*2-1,random.nextFloat()*2-1,random.nextFloat()*2-1);
                sky=random.nextInt();look=new Vector3f(edges[(i/11)%edges.length],0,0);
                events.clear();call(out,"air:"+i,()->events.add("color:"+Integer.toHexString(air.getBaseColor(level,camera,(count%80)-8,.5F))));
            }
            for(int i=0;i<1024;i++){
                vision=edges[i%edges.length];blend=edges[(i/2)%edges.length];ticks=edges[(i/3)%edges.length];
                rain=edges[(i/5)%edges.length];light=i%24-4;precipitation=(i&1)!=0;cloudRange=i%3==0?Integer.MAX_VALUE:24;
                duration=i%50-10;infinite=(i%7)==0;spectator=(i%5)==0;closer=(i%3)==0;effectMask=i%16;
                for(int kind=0;kind<environments.length;kind++){
                    var d=seeded();var environment=environments[kind];float range=edges[(i/7)%edges.length]*256;
                    events.clear();call(out,"ranges:"+i+":"+kind,()->{
                        try{environment.setupFog(d,local,BlockPos.ZERO,level,range,tracker);}finally{events.add("data:"+data(d));}
                    });
                }
                events.clear();call(out,"darkness:"+i,()->{
                    for(var env:environments)events.add("darkness:"+floats(env.getModifiedDarkness(local,vision,.25F)));
                });
            }
            for(int i=0;i<1024;i++){
                clock.set(i%19==0?-1:(long)i*137);water=random.nextInt();
                if(i%17==0)waterEnvironment.onNotApplicable();
                events.clear();call(out,"water:"+i,()->events.add("color:"+Integer.toHexString(waterEnvironment.getBaseColor(level,camera,12,.5F))));
            }
            for(int mode=0;mode<2;mode++){
                var d=seeded();vision=.5F;closer=true;
                visionFailure=mode==0?new IllegalStateException("vision callback"):null;
                biomeFailure=mode==1?new IllegalStateException("biome callback"):null;
                events.clear();call(out,"water-failure:"+mode,()->{
                    try{waterEnvironment.setupFog(d,local,BlockPos.ZERO,level,256,tracker);}finally{events.add("data:"+data(d));}
                });
            }
            visionFailure=null;biomeFailure=null;
            var unsafeField=Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            var renderer=(FogRenderer)((Unsafe)unsafeField.get(null)).allocateInstance(FogRenderer.class);
            for(int i=0;i<512;i++){
                medium=FogType.values()[i%6];effectMask=i%16;onset=edges[(i/7)%edges.length]*32;
                position=new Vec3(0,(i%7)*32-128,0);sampled=new Vec3(.3,.6,.9);time=.25F;sun=0;rain=0;thunder=0;
                duration=i%240;infinite=(i%9)==0;blend=edges[(i/3)%edges.length];vision=edges[i%edges.length];
                int count=i;clock.set(i*17L);events.clear();call(out,"final-color:"+i,()->{
                    Vector4f c=renderer.computeFogColor(camera,.25F,level,12,edges[(count/5)%edges.length],(count&1)!=0);
                    events.add("rgba:"+floats(c.x,c.y,c.z,c.w));
                });
            }
        }finally{Util.timeSource=previousClock;}
        byte[] bytes=output.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(Path.of(args[0]),bytes);
        System.out.println("FOG_CASES "+cases+" bytes="+bytes.length+" sha="+sha(bytes));
        var names=new ArrayList<>(List.of("net.minecraft.client.renderer.fog.FogRenderer","net.minecraft.client.renderer.fog.FogData"));
        for(String name:List.of("AirBased","Atmospheric","Blindness","Darkness","DimensionOrBoss","Lava","PowderedSnow","Water"))names.add("net.minecraft.client.renderer.fog.environment."+name+"FogEnvironment");
        if(args[1].equals("current"))names.addAll(List.of("net.minecraft.client.renderer.fog.NativeFog","net.minecraft.client.renderer.fog.NativeFog$RainState","net.minecraft.client.renderer.fog.NativeFog$Scratch"));
        for(String name:names)try(var in=Class.forName(name).getResourceAsStream("/"+name.replace('.','/')+".class")){System.out.println("CLASS "+name+" "+sha(in.readAllBytes()));}
        if(args[1].equals("current")){
            var nativePath=Path.of(System.getProperty("mattmc.rust.natives.dir"),"mattmc_rust-linux-x64.so").toRealPath();
            if(Files.readString(Path.of("/proc/self/maps")).lines().noneMatch(line->line.endsWith(" "+nativePath)))throw new AssertionError("Native image not mapped");
            System.out.println("NATIVE_IMAGE "+sha(Files.readAllBytes(nativePath))+" "+nativePath);
        }
    }
}

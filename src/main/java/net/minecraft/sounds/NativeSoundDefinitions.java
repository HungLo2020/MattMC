package net.minecraft.sounds;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.SoundType;

/** Temporary CPU views of native sound content. Playback remains audio-owned. */
public final class NativeSoundDefinitions {
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_sound_definitions_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final Data DATA = load();
    private record Event(String key, String location, Float range) { }
    private record Type(String name, float volume, float pitch, int[] events) { }
    public record Instrument(String name, int event, int kind) { }
    private record Data(List<Event> events, List<Type> types, List<Instrument> instruments,
                        Map<String, Integer> eventIds, Map<String, Integer> typeIds, Map<String, Instrument> instrumentsByName) { }

    // Registration occurs once. Raw metadata loading never initializes Java
    // SoundType or NoteBlockInstrument, so their static views cannot recurse.
    private static final class Events {
        private record Views(List<SoundEvent> events, List<Holder.Reference<SoundEvent>> holders) { }
        private static final Views VALUES = register();
        private static Views register() {
            if (BuiltInRegistries.SOUND_EVENT.size() != 0) throw new IllegalStateException("Sound registry already populated outside native definitions");
            List<SoundEvent> events = new ArrayList<>(DATA.events.size());
            List<Holder.Reference<SoundEvent>> values = new ArrayList<>(DATA.events.size());
            for (Event e : DATA.events) {
                var location = ResourceLocation.parse(e.location);
                SoundEvent view = e.range == null ? SoundEvent.createVariableRangeEvent(location) : SoundEvent.createFixedRangeEvent(location, e.range);
                var key = e.key.equals(e.location) ? location : ResourceLocation.parse(e.key);
                var holder = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, key, view);
                if (BuiltInRegistries.SOUND_EVENT.getId(view) != values.size()) throw new IllegalStateException("Native sound registration order differs");
                events.add(view);
                values.add(holder);
            }
            return new Views(List.copyOf(events), List.copyOf(values));
        }
    }
    // Standalone registry holders bind at freeze. Constructors need the same
    // canonical event objects earlier, without changing that registry lifecycle.
    private static SoundEvent event(int id) { return Events.VALUES.events.get(id); }
    public static SoundEvent event(String key) { return event(eventId(key)); }
    private static int eventId(String key) {
        Integer id = DATA.eventIds.get(key.indexOf(':') < 0 ? "minecraft:" + key : key);
        if (id == null) throw new IllegalStateException("Missing native sound event: " + key);
        return id;
    }
    public static Holder.Reference<SoundEvent> holder(String key) { return holder(eventId(key)); }
    public static Holder.Reference<SoundEvent> holder(int id) { return Events.VALUES.holders.get(id); }
    public static int eventCount() { return DATA.events.size(); }
    public static int typeCount() { return DATA.types.size(); }
    public static int instrumentCount() { return DATA.instruments.size(); }
    public static int typeId(String name) {
        Integer id = DATA.typeIds.get(name);
        if (id == null) throw new IllegalStateException("Missing native sound profile: " + name);
        return id;
    }
    public static Instrument instrument(int id) { return DATA.instruments.get(id); }
    public static Instrument instrument(String name) {
        Instrument result = DATA.instrumentsByName.get(name);
        if (result == null) throw new IllegalStateException("Missing native instrument: " + name);
        return result;
    }
    /** Called only by SoundType's first static initializer; later aliases use its array. */
    public static SoundType[] createSoundTypes() {
        SoundType[] result = new SoundType[DATA.types.size()];
        for (int i = 0; i < result.length; i++) {
            Type t = DATA.types.get(i); int[] e = t.events;
            result[i] = new SoundType(t.volume, t.pitch, event(e[0]), event(e[1]), event(e[2]), event(e[3]), event(e[4]));
        }
        return result;
    }
    private static MemorySegment buffer(int kind, int count, int size, Arena arena) throws Throwable {
        var length = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment pointer = (MemorySegment) BUFFER.invokeExact(kind, length);
        if (pointer.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != count) throw new IllegalStateException("Native sound buffer mismatch: " + kind);
        return pointer.reinterpret((long)count * size, Arena.global(), null).asReadOnly();
    }
    private static int word(MemorySegment data, int index) { return data.getAtIndex(ValueLayout.JAVA_INT, index); }
    private static String text(MemorySegment strings, int start, int length) {
        if (start < 0 || length <= 0 || (long) start + length > strings.byteSize()) throw new IllegalStateException("Invalid native sound string");
        return new String(strings.asSlice(start, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
    }
    private static Data load() {
        try (Arena arena = Arena.ofConfined()) {
            var header = buffer(0, 5, Integer.BYTES, arena);
            int events = word(header,1), types = word(header,2), instruments = word(header,3), bytes = word(header,4);
            if (word(header,0) != 1 || events <= 0 || events > 65535 || types <= 0 || types > 65535
                || instruments <= 0 || instruments > 256 || bytes <= 0 || bytes > 16777216) throw new IllegalStateException("Unsupported native sound schema");
            var eventRows = buffer(1, events * 6, Integer.BYTES, arena);
            var typeRows = buffer(2, types * 9, Integer.BYTES, arena);
            var instrumentRows = buffer(3, instruments * 4, Integer.BYTES, arena);
            var strings = buffer(4, bytes, 1, arena);
            List<Event> eventViews = new ArrayList<>(events);
            Map<String,Integer> eventIds = new HashMap<>(), typeIds = new HashMap<>();
            for (int i = 0; i < events; i++) {
                int at = i * 6, fixed = word(eventRows,at+4);float range = Float.intBitsToFloat(word(eventRows,at+5));
                if ((fixed != 0 && fixed != 1) || !Float.isFinite(range) || range < 0) throw new IllegalStateException("Invalid native event range");
                String key = text(strings,word(eventRows,at),word(eventRows,at+1));
                String location = word(eventRows,at)==word(eventRows,at+2) && word(eventRows,at+1)==word(eventRows,at+3)
                    ? key : text(strings,word(eventRows,at+2),word(eventRows,at+3));
                if (eventIds.put(key,i) != null) throw new IllegalStateException("Duplicate native event");
                eventViews.add(new Event(key,location,fixed == 0 ? null : range));
            }
            List<Type> typeViews = new ArrayList<>(types);
            for (int i = 0; i < types; i++) {
                int at = i * 9;String name = text(strings,word(typeRows,at),word(typeRows,at+1));
                float volume = Float.intBitsToFloat(word(typeRows,at+2)), pitch = Float.intBitsToFloat(word(typeRows,at+3));
                if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0) throw new IllegalStateException("Invalid native sound profile");
                int[] ids = new int[5];
                for (int j=0;j<5;j++) { ids[j] = word(typeRows,at+4+j); if (ids[j]<0 || ids[j]>=events) throw new IllegalStateException("Invalid profile event"); }
                if (typeIds.put(name,i) != null) throw new IllegalStateException("Duplicate native sound profile");
                typeViews.add(new Type(name,volume,pitch,ids));
            }
            List<Instrument> instrumentViews = new ArrayList<>(instruments);
            Map<String,Instrument> instrumentNames = new HashMap<>();
            for (int i = 0; i < instruments; i++) {
                int at=i*4;String name=text(strings,word(instrumentRows,at),word(instrumentRows,at+1));
                int event=word(instrumentRows,at+2),kind=word(instrumentRows,at+3);
                if(event<0 || event>=events || kind<0 || kind>2)throw new IllegalStateException("Invalid native instrument");
                var view=new Instrument(name,event,kind);
                if(instrumentNames.put(name,view)!=null)throw new IllegalStateException("Duplicate native instrument");
                instrumentViews.add(view);
            }
            return new Data(List.copyOf(eventViews),List.copyOf(typeViews),List.copyOf(instrumentViews),Map.copyOf(eventIds),Map.copyOf(typeIds),Map.copyOf(instrumentNames));
        } catch (RuntimeException | Error e) { throw e; }
          catch (Throwable e) { throw new IllegalStateException("Cannot load native sound definitions",e); }
    }
}

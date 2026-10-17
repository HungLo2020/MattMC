package net.minecraft.world.level.chunk;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Section snapshots for Rust light propagation: a section's stored light
 * layer and its block states as {@code LightChunk.getBlockState} returns
 * them, as state IDs that Rust maps to light-property types. Lives beside
 * chunk storage to borrow its package-private palette data. */
public final class NativeLightBlocks {
    // Callback buffer layout, shared with lighting/propagation/ffi.rs.
    public static final int LAYER_AT = 64;
    public static final int PALETTE_AT = LAYER_AT + 2048;
    private static final int PALETTE_MAX = 256;
    public static final int WORDS_AT = PALETTE_AT + PALETTE_MAX * 2;
    private static final int WORDS_MAX = 1024;
    public static final int FULL_AT = WORDS_AT + WORDS_MAX * 8;
    public static final int BUFFER = FULL_AT + 4096 * 2;
    /** The ID of states Rust must not see. */
    public static final char UNSUPPORTED = Character.MAX_VALUE;

    private NativeLightBlocks() {}

    /** One synchronous callback's CPU owner pin; contains no copied states. */
    public static final class Lease {
        @Nullable private NativeLiveBlockSection owner;
        public void clear() { this.owner = null; }
    }

    /** One pending node's actual CPU owner and original compatibility captures. */
    public static final class NodeLease {
        private LightChunk chunk;
        private BlockPos position;
        private NativeLiveBlockSection owner;
        private int index;
        public boolean acquire(@Nullable LightChunk chunk,BlockPos position)throws Throwable {
            clear();this.chunk=chunk;this.position=position;
            if(chunk==null || (chunk.getClass()!=ProtoChunk.class && chunk.getClass()!=LevelChunk.class))return false;
            ChunkAccess access=(ChunkAccess)chunk;
            var height=NativeSkySourceSections.pureAccessor(access);
            if(height==null)return false;
            if(chunk.getClass()==LevelChunk.class){
                var level=((LevelChunk)chunk).getLevel();
                if(level.getClass()!=net.minecraft.server.level.ServerLevel.class
                    && level.getClass()!=net.minecraft.client.multiplayer.ClientLevel.class)return false;
                if(level.isDebug())return false;
            }
            int min=height.getMinY(),y=position.getY();
            var sections=access.getSectionsForRead();
            if((min&15)!=0 || sections.length*16!=height.getHeight() || y<min || y>=min+height.getHeight())return false;
            var section=sections[(y>>4)-(min>>4)];
            if(section==null || section.getClass()!=LevelChunkSection.class || section.hasOnlyAir())return false;
            var states=section.getStates();
            if(states.getClass()!=PalettedContainer.class)return false;
            owner=states.nativeLiveBlocks();
            index=(y&15)<<8|(position.getZ()&15)<<4|position.getX()&15;
            return owner!=null;
        }
        public boolean hasOwner(){return owner!=null;}
        public MemorySegment pointer(){return owner.stageOwner();}
        public int index(){return index;}
        /** Reuses the position after any original lookup reentry; never repeats lookup. */
        public BlockState compatibility(){return chunk==null?Blocks.BEDROCK.defaultBlockState():chunk.getBlockState(position);}
        public void clear(){java.lang.ref.Reference.reachabilityFence(owner);owner=null;chunk=null;position=null;}
    }

    /** Header [light-on, default value or -1] then the bytes; -1 for layer subclasses. */
    public static int layer(DataLayer layer, boolean lightOn, MemorySegment buffer) {
        if (layer.getClass() != DataLayer.class) return -1;
        buffer.setAtIndex(ValueLayout.JAVA_INT, 0, lightOn ? 1 : 0);
        var owner = layer.nativeLightLayer();
        if (owner != null) {
            buffer.set(ValueLayout.JAVA_LONG, 8, owner.ownerForNativeCall().address());
            return 2; // CPU owner is pinned by NativeLightPropagation's callback.
        }
        byte[] data = layer.dataForNativeLight();
        if (data == null) {
            buffer.setAtIndex(ValueLayout.JAVA_INT, 1, layer.defaultValueForNativeLight());
        } else {
            buffer.setAtIndex(ValueLayout.JAVA_INT, 1, -1);
            MemorySegment.copy(MemorySegment.ofArray(data), 0, buffer, LAYER_AT, 2048);
        }
        return 1;
    }

    private static char id(@Nullable BlockState state) {
        if (state == null || state.getClass() != BlockState.class) return UNSUPPORTED;
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        return id < 0 || id >= UNSUPPORTED ? UNSUPPORTED : (char)id;
    }

    private static int uniform(MemorySegment buffer, char id) {
        if (id == UNSUPPORTED) return -1;
        buffer.setAtIndex(ValueLayout.JAVA_INT, 0, 0);
        buffer.setAtIndex(ValueLayout.JAVA_INT, 1, id);
        return 0;
    }

    /** Bulk propagation may only snapshot silent, canonical section inputs. */
    public static int blocksForPropagation(@Nullable LightChunk chunk,int x,int y,int z,MemorySegment buffer,Lease lease)throws Throwable {
        if(chunk==null)return blocks(null,x,y,z,buffer,lease);
        if(chunk.getClass()==ImposterProtoChunk.class)chunk=((ImposterProtoChunk)chunk).getWrapped();
        if(chunk.getClass()!=ProtoChunk.class && chunk.getClass()!=LevelChunk.class)return -1;
        ChunkAccess access=(ChunkAccess)chunk;
        var height=NativeSkySourceSections.pureAccessor(access);
        if(height==null)return -1;
        if(chunk.getClass()==LevelChunk.class){
            var level=((LevelChunk)chunk).getLevel();
            if((level.getClass()!=net.minecraft.server.level.ServerLevel.class
                && level.getClass()!=net.minecraft.client.multiplayer.ClientLevel.class)||level.isDebug())return -1;
        }
        var sections=access.getSectionsForRead();int min=height.getMinY();
        if((min&15)!=0 || sections.length*16!=height.getHeight())return -1;
        int index=y-(min>>4);
        if(index>=0 && index<sections.length){
            var section=sections[index];
            if(section==null || section.getClass()!=LevelChunkSection.class)return -1;
            if(!section.hasOnlyAir()){
                var states=section.getStates();
                if(states.getClass()!=PalettedContainer.class || states.nativeLiveBlocks()==null)return -1;
            }
        }
        return blocks(chunk,x,y,z,buffer,lease);
    }

    /** Header [kind, uniform state or bits, palette size] plus palette states,
     * packed words or one state per block. Kinds: 0 uniform, 1 local palette,
     * 2 global state ids, 3 full. The leased overload also supplies kind 4:
     * a primary native CPU owner. A null chunk reads as bedrock, like
     * {@code LightEngine.getState}. Returns 0, or -1 for states Rust must not see. */
    public static int blocks(@Nullable LightChunk chunk, int sectionX, int sectionY, int sectionZ, MemorySegment buffer) {
        return blocks(chunk, sectionX, sectionY, sectionZ, buffer, null);
    }

    /** Production bridge pins the primary CPU owner until Rust has captured
     * its immutable pass input. The legacy overload exports packed bytes. */
    public static int blocks(@Nullable LightChunk chunk, int sectionX, int sectionY, int sectionZ,
                             MemorySegment buffer, @Nullable Lease lease) {
        if (lease != null) lease.clear();
        if (chunk == null) return uniform(buffer, id(Blocks.BEDROCK.defaultBlockState()));
        ChunkAccess access = null;
        boolean proto = false;
        if (chunk.getClass() == ProtoChunk.class) {
            access = (ProtoChunk)chunk;
            proto = true;
        } else {
            LevelChunk level = chunk.getClass() == LevelChunk.class ? (LevelChunk)chunk
                : chunk.getClass() == ImposterProtoChunk.class ? ((ImposterProtoChunk)chunk).getWrapped() : null;
            if (level != null && level.getClass() == LevelChunk.class && !level.getLevel().isDebug()) access = level;
        }
        if (access != null) {
            LevelChunkSection[] sections = access.getSectionsForRead();
            int index = access.getSectionIndexFromSectionY(sectionY);
            if (access.getMinY() % 16 == 0 && sections.length * 16 == access.getHeight()) {
                if (index < 0 || index >= sections.length) {
                    return uniform(buffer, id((proto ? Blocks.VOID_AIR : Blocks.AIR).defaultBlockState()));
                }
                int packed = packed(sections[index], buffer, lease);
                if (packed != -2) return packed;
            }
        }
        // Other chunk kinds: the original lookup for each block.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4096; i++) {
            char id = id(chunk.getBlockState(pos.set(sectionX * 16 + (i & 15), sectionY * 16 + (i >> 8), sectionZ * 16 + (i >> 4 & 15))));
            if (id == UNSUPPORTED) return -1;
            buffer.setAtIndex(ValueLayout.JAVA_CHAR, FULL_AT / 2 + i, id);
        }
        buffer.setAtIndex(ValueLayout.JAVA_INT, 0, 3);
        return 0;
    }

    /** -2 when the section is not a modelled palette container. */
    private static int packed(@Nullable LevelChunkSection section, MemorySegment buffer, @Nullable Lease lease) {
        if (section == null || section.getClass() != LevelChunkSection.class) return -2;
        if (section.hasOnlyAir()) return uniform(buffer, id(Blocks.AIR.defaultBlockState()));
        var container = section.getStates();
        if (container.getClass() != PalettedContainer.class) return -2;
        var live = container.nativeLiveBlocks();
        if (live != null) {
            if (lease == null) return live.light(buffer);
            lease.owner = live;
            buffer.setAtIndex(ValueLayout.JAVA_INT, 0, 4);
            buffer.set(ValueLayout.JAVA_LONG, 8, live.stageOwner().address());
            return 0;
        }
        var data = container.dataForNativeScan();
        var storage = data.storage();
        var palette = data.palette();
        if (storage.getSize() != 4096) return -2;
        if (storage.getClass() == ZeroBitStorage.class) return uniform(buffer, id(palette.valueFor(0)));
        if (storage.getClass() != SimpleBitStorage.class) return -2;
        int bits = storage.getBits();
        long[] raw = storage.getRaw();
        if (bits < 1 || bits > 32 || raw.length > WORDS_MAX || raw.length != (4096 + 64 / bits - 1) / (64 / bits)) return -2;
        if (palette.getClass() == GlobalPalette.class) {
            if (container.registryForNativeScan() != Block.BLOCK_STATE_REGISTRY) return -2;
            buffer.setAtIndex(ValueLayout.JAVA_INT, 0, 2);
        } else if (palette.getClass() == SingleValuePalette.class || palette.getClass() == LinearPalette.class
            || palette.getClass() == HashMapPalette.class) {
            int count = palette.getSize();
            if (count < 1 || count > PALETTE_MAX) return -2;
            for (int i = 0; i < count; i++) {
                buffer.setAtIndex(ValueLayout.JAVA_CHAR, PALETTE_AT / 2 + i, id(palette.valueFor(i)));
            }
            buffer.setAtIndex(ValueLayout.JAVA_INT, 0, 1);
            buffer.setAtIndex(ValueLayout.JAVA_INT, 2, count);
        } else {
            return -2;
        }
        buffer.setAtIndex(ValueLayout.JAVA_INT, 1, bits);
        MemorySegment.copy(MemorySegment.ofArray(raw), 0, buffer, WORDS_AT, raw.length * 8L);
        return 0;
    }
}

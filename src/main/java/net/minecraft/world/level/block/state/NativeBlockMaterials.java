package net.minecraft.world.level.block.state;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.NativeSoundDefinitions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.Vec3;

/** Bounded CPU views of native block sound, note and model-offset policy. */
final class NativeBlockMaterials {
    record Material(SoundType baseSound, NoteBlockInstrument instrument, Offset offset) {
        void apply(BlockBehaviour.Properties properties) {
            properties.soundType = this.baseSound;
            properties.instrument = this.instrument;
            // Preserve generic Properties-copy semantics. Native state views
            // use the shared table below, while legacy copies may have another
            // block class and different explicit maximum offsets.
            properties.offsetType(this.offset.kind);
        }
    }
    static final class Offset {
        final BlockBehaviour.OffsetType kind;
        final float horizontal, vertical;
        private final Vec3[] values;
        private Offset(BlockBehaviour.OffsetType kind, float horizontal, float vertical, Vec3[] values) {
            this.kind=kind;this.horizontal=horizontal;this.vertical=vertical;this.values=values;
        }
        Vec3 at(BlockPos pos) {
            if (this.kind == BlockBehaviour.OffsetType.NONE) return Vec3.ZERO;
            long seed = Mth.getSeed(pos.getX(),0,pos.getZ());
            int index = this.kind == BlockBehaviour.OffsetType.XYZ ? (int)seed & 4095 : ((int)seed & 15) | ((int)(seed >> 4) & 240);
            return this.values[index];
        }
    }
    private static int word(MemorySegment rows,int index) { return rows.getAtIndex(ValueLayout.JAVA_INT,index); }
    static Material[] load(int count, int offsetCount, int valueCount, MemorySegment rows, MemorySegment offsetRows, MemorySegment offsetValues) {
        if(count<=0 || count>65535 || offsetCount<=0 || offsetCount>count || valueCount<=0 || valueCount>offsetCount*4096*3)throw new IllegalStateException("Invalid native material counts");
        Offset[] offsets=new Offset[offsetCount];
        var kinds=BlockBehaviour.OffsetType.values();int next=0;
        for(int id=0;id<offsetCount;id++) {
            int at=id*5,kind=word(offsetRows,at),first=word(offsetRows,at+3),size=word(offsetRows,at+4);
            float h=Float.intBitsToFloat(word(offsetRows,at+1)),v=Float.intBitsToFloat(word(offsetRows,at+2));
            if(kind<0 || kind>=kinds.length || !Float.isFinite(h) || h<0 || !Float.isFinite(v) || v<0 || first!=next
                || size!=(kind==0?1:kind==1?256:4096) || (long)first+size*3>valueCount)throw new IllegalStateException("Invalid native offset profile");
            Vec3[] values=new Vec3[size];
            for(int i=0;i<size;i++) {
                long base=first+i*3L;double x=offsetValues.getAtIndex(ValueLayout.JAVA_DOUBLE,base);
                double y=offsetValues.getAtIndex(ValueLayout.JAVA_DOUBLE,base+1),z=offsetValues.getAtIndex(ValueLayout.JAVA_DOUBLE,base+2);
                if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x)>h || Math.abs(z)>h || y>0 || y < -v)throw new IllegalStateException("Invalid native offset value");
                values[i]=kind==0?Vec3.ZERO:new Vec3(x,y,z);
            }
            offsets[id]=new Offset(kinds[kind],h,v,values);next+=size*3;
        }
        if(next!=valueCount)throw new IllegalStateException("Incomplete native offset table");
        var instruments=NoteBlockInstrument.values();
        if(instruments.length!=NativeSoundDefinitions.instrumentCount())throw new IllegalStateException("Native instrument count differs");
        Material[] result=new Material[count];
        for(int id=0;id<count;id++) {
            int at=id*3,sound=word(rows,at),instrument=word(rows,at+1),offset=word(rows,at+2);
            if(sound<0 || sound>=NativeSoundDefinitions.typeCount() || instrument<0 || instrument>=instruments.length || offset<0 || offset>=offsets.length)throw new IllegalStateException("Invalid native material binding");
            if(!instruments[instrument].getSerializedName().equals(NativeSoundDefinitions.instrument(instrument).name()))throw new IllegalStateException("Native instrument order differs");
            result[id]=new Material(SoundType.nativeView(sound),instruments[instrument],offsets[offset]);
        }
        return result;
    }
}
